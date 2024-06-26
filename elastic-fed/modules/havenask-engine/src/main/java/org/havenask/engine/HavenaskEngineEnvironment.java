/*
 * Copyright (c) 2021, Alibaba Group;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.havenask.engine;

import static org.havenask.engine.index.config.generator.BizConfigGenerator.BIZ_DIR;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.CLUSTER_DIR;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.DATA_TABLES_DIR;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.DEFAULT_BIZ_CONFIG;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.DEFAULT_DIR;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.PLUGINS_DIR;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.SCHEMAS_DIR;
import static org.havenask.engine.index.config.generator.RuntimeSegmentGenerator.DEPLOY_META_FILE_CONTENT_TEMPLATE;
import static org.havenask.engine.index.config.generator.RuntimeSegmentGenerator.DEPLOY_META_FILE_NAME;
import static org.havenask.engine.index.config.generator.RuntimeSegmentGenerator.ENTRY_TABLE_FILE_CONTENT;
import static org.havenask.engine.index.config.generator.RuntimeSegmentGenerator.ENTRY_TABLE_FILE_NAME;
import static org.havenask.engine.index.config.generator.RuntimeSegmentGenerator.INDEX_FORMAT_VERSION_FILE_CONTENT;
import static org.havenask.engine.index.config.generator.RuntimeSegmentGenerator.INDEX_FORMAT_VERSION_FILE_NAME;
import static org.havenask.engine.index.config.generator.RuntimeSegmentGenerator.INDEX_PARTITION_META_FILE_CONTENT;
import static org.havenask.engine.index.config.generator.RuntimeSegmentGenerator.INDEX_PARTITION_META_FILE_NAME;
import static org.havenask.engine.index.config.generator.RuntimeSegmentGenerator.SCHEMA_FILE_NAME;
import static org.havenask.engine.index.config.generator.RuntimeSegmentGenerator.VERSION_FILE_CONTENT;
import static org.havenask.engine.index.config.generator.RuntimeSegmentGenerator.VERSION_FILE_NAME;
import static org.havenask.engine.index.config.generator.TableConfigGenerator.TABLE_DIR;
import static org.havenask.env.Environment.PATH_HOME_SETTING;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.havenask.HavenaskException;
import org.havenask.common.io.PathUtils;
import org.havenask.common.settings.Setting;
import org.havenask.common.settings.Setting.Property;
import org.havenask.common.settings.Settings;
import org.havenask.core.internal.io.IOUtils;
import org.havenask.engine.index.config.generator.BizConfigGenerator;
import org.havenask.engine.index.config.generator.TableConfigGenerator;
import org.havenask.engine.index.engine.EngineSettings;
import org.havenask.engine.rpc.TargetInfo;
import org.havenask.engine.util.RangeUtil;
import org.havenask.engine.util.Utils;
import org.havenask.env.Environment;
import org.havenask.env.ShardLock;
import org.havenask.index.Index;
import org.havenask.index.IndexSettings;
import org.havenask.index.shard.ShardId;
import org.havenask.plugins.NodeEnvironmentPlugin.CustomEnvironment;
import org.havenask.threadpool.ThreadPool;

public class HavenaskEngineEnvironment implements CustomEnvironment {
    private static final Logger LOGGER = LogManager.getLogger(HavenaskEngineEnvironment.class);
    public static final String DEFAULT_DATA_PATH = "havenask";
    public static final String HAVENASK_CONFIG_PATH = "config";
    public static final String HAVENASK_RUNTIMEDATA_PATH = "runtimedata";
    public static final String HAVENASK_TABLE_CONFIG_PATH = "table";
    public static final String HAVENASK_BIZS_CONFIG_PATH = "bizs";
    public static final String HAVENASK_BS_WORK_PATH = "bs";
    public static final Setting<String> HAVENASK_PATH_DATA_SETTING = new Setting<>(
        "havenask.path.data",
        DEFAULT_DATA_PATH,
        Function.identity(),
        Property.NodeScope
    );

    public static final Setting<String> HAVENASK_ENVIRONMENT_BINFILE_PATH_SETTING = new Setting<>(
        "havenask.binfile.path",
        "",
        Function.identity(),
        Property.NodeScope
    );

    private final Environment environment;
    private final Path dataPath;
    private final Path configPath;
    private final Path runtimedataPath;
    private final Path bsWorkPath;
    private final Path tablePath;
    private final Path bizsPath;

    private MetaDataSyncer metaDataSyncer;

    public HavenaskEngineEnvironment(final Environment environment, final Settings settings) {
        this.environment = environment;
        if (HAVENASK_PATH_DATA_SETTING.exists(settings)) {
            dataPath = PathUtils.get(HAVENASK_PATH_DATA_SETTING.get(settings)).normalize();
        } else if (this.environment.dataFiles().length >= 1) {
            dataPath = this.environment.dataFiles()[0].resolve(DEFAULT_DATA_PATH);
        } else {
            Path homeFile = PathUtils.get(PATH_HOME_SETTING.get(settings)).toAbsolutePath().normalize();
            dataPath = homeFile.resolve(DEFAULT_DATA_PATH);
        }

        try {
            if (Files.exists(dataPath) == false) {
                Files.createDirectories(dataPath);
            }

            configPath = dataPath.resolve(HAVENASK_CONFIG_PATH);
            if (Files.exists(configPath) == false) {
                initConfig();
            }

            runtimedataPath = dataPath.resolve(HAVENASK_RUNTIMEDATA_PATH);
            if (Files.exists(runtimedataPath) == false) {
                initRuntimeData("partition_0_32767");
                initRuntimeData("partition_32768_65535");
            }
        } catch (IOException e) {
            throw new HavenaskException("havenask init engine environment error", e);
        }

        bsWorkPath = dataPath.resolve(HAVENASK_BS_WORK_PATH);
        tablePath = configPath.resolve(HAVENASK_TABLE_CONFIG_PATH);
        bizsPath = configPath.resolve(HAVENASK_BIZS_CONFIG_PATH);
    }

    /**
     * get havenask data path
     *
     * @return dataPath
     */
    public Path getDataPath() {
        return dataPath;
    }

    /**
     * get config path
     *
     * @return configPath
     */
    public Path getConfigPath() {
        return configPath;
    }

    /**
     * get table path
     *
     * @return tablePath
     */
    public Path getTablePath() {
        return tablePath;
    }

    /**
     * get bizs path
     *
     * @return bizsPath
     */
    public Path getBizsPath() {
        return bizsPath;
    }

    /**
     * get runtime data path
     *
     * @return runtimedataPath
     */
    public Path getRuntimedataPath() {
        return runtimedataPath;
    }

    /**
     * get table config path
     *
     * @return bsWorkPath
     */
    public Path getBsWorkPath() {
        return bsWorkPath;
    }

    /**
     * get table config path
     * @param shardId shardId
     * @return tablePath
     */
    public Path getShardPath(ShardId shardId) {
        String tableName = Utils.getHavenaskTableName(shardId);
        return runtimedataPath.resolve(tableName);
    }

    public void setMetaDataSyncer(MetaDataSyncer metaDataSyncer) {
        this.metaDataSyncer = metaDataSyncer;
    }

    public MetaDataSyncer getMetaDataSyncer() {
        return this.metaDataSyncer;
    }

    @Override
    public void deleteIndexDirectoryUnderLock(Index index, IndexSettings indexSettings) throws IOException {
        if (EngineSettings.isHavenaskEngine(indexSettings.getSettings()) == false) {
            return;
        }
        String tableName = index.getName();
        BizConfigGenerator.removeBiz(tableName, configPath);
        TableConfigGenerator.removeTable(tableName, configPath);
        Path indexDir = runtimedataPath.resolve(tableName);
        long maxRetries = 30;
        long sleepInterval = 1000;

        if (metaDataSyncer == null) {
            throw new RuntimeException("metaDataSyncer is null while deleting index");
        }
        metaDataSyncer.addIndexLock(tableName);
        LOGGER.debug("get lock while deleting index, table name :[{}]", tableName);
        // TODO: ThreadPool的获取是否可以优化
        final ThreadPool threadPool = metaDataSyncer.getThreadPool();
        asyncRemoveIndexRuntimeDir(threadPool, tableName, indexDir, maxRetries, sleepInterval);
    }

    @Override
    public void deleteShardDirectoryUnderLock(ShardLock lock, IndexSettings indexSettings) {
        if (EngineSettings.isHavenaskEngine(indexSettings.getSettings()) == false) {
            return;
        }
        String partitionName = RangeUtil.getRangePartition(indexSettings.getNumberOfShards(), lock.getShardId().id());
        Path shardDir = runtimedataPath.resolve(indexSettings.getIndex().getName()).resolve("generation_0").resolve(partitionName);
        long maxRetries = 30;
        long sleepInterval = 1000;

        if (metaDataSyncer == null) {
            throw new RuntimeException("metaDataSyncer is null while deleting shard");
        }
        metaDataSyncer.addShardLock(lock.getShardId());
        LOGGER.debug("get lock while deleting shard, table name :[{}]", lock.getShardId().getIndexName());
        String partitionId = RangeUtil.getRangeName(indexSettings.getNumberOfShards(), lock.getShardId().id());
        final ThreadPool threadPool = metaDataSyncer.getThreadPool();
        asyncRemoveShardRuntimeDir(threadPool, lock.getShardId(), partitionId, shardDir, maxRetries, sleepInterval);
    }

    /**
     * 异步移除索引删除后runtimedata内的数据信息
     */
    public void asyncRemoveIndexRuntimeDir(
        final ThreadPool threadPool,
        String tableName,
        Path indexDir,
        long maxRetries,
        long sleepInterval
    ) {
        String logMessage = String.format(Locale.ROOT, "index :[%s]", tableName);

        Runnable checkIndexIsDeletedAction = () -> {
            try {
                checkIndexIsDeletedInSearcher(metaDataSyncer, tableName);
            } catch (IOException e) {
                LOGGER.error(
                    "checkIndexIsDeletedInSearcher failed while deleting index runtime dir, index : [{}], error: [{}]",
                    tableName,
                    e
                );
            }
        };

        Consumer<Path> deleteRuntimeDirAction = (path) -> {
            try {
                IOUtils.rm(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        Runnable deleteLockAction = () -> { metaDataSyncer.deleteIndexLock(tableName); };

        asyncRemoveRuntimeDir(
            threadPool,
            checkIndexIsDeletedAction,
            deleteRuntimeDirAction,
            deleteLockAction,
            indexDir,
            maxRetries,
            sleepInterval,
            logMessage
        );
    }

    /**
     * 异步删除shard减少时runtimedata内的数据信息
     */
    public void asyncRemoveShardRuntimeDir(
        final ThreadPool threadPool,
        ShardId shardId,
        String partitionId,
        Path shardDir,
        long maxRetries,
        long sleepInterval
    ) {
        String logMessage = String.format(Locale.ROOT, "index :[%s], partitionId:[%s]", shardId.getIndexName(), partitionId);

        Runnable checkShardIsDeletedAction = () -> {
            try {
                checkShardIsDeletedInSearcher(metaDataSyncer, shardId.getIndexName(), partitionId);
            } catch (IOException e) {
                LOGGER.error(
                    "checkShardIsDeletedInSearcher failed while deleting shard runtime dir, "
                        + "index: [{}], partitionId:[{}], error: [{}]",
                    shardId.getIndexName(),
                    partitionId,
                    e
                );
            }
        };

        Consumer<Path> deleteRuntimeDirAction = (path) -> {
            try {
                IOUtils.rm(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        Runnable deleteLockAction = () -> { metaDataSyncer.deleteShardLock(shardId); };

        asyncRemoveRuntimeDir(
            threadPool,
            checkShardIsDeletedAction,
            deleteRuntimeDirAction,
            deleteLockAction,
            shardDir,
            maxRetries,
            sleepInterval,
            logMessage
        );
    }

    public void asyncRemoveRuntimeDir(
        final ThreadPool threadPool,
        final Runnable checkIsDeletedAction,
        final Consumer<Path> deleteRuntimeDirAction,
        final Runnable deleteLockAction,
        final Path dir,
        long maxRetries,
        long sleepInterval,
        final String logs
    ) {
        threadPool.executor(HavenaskEnginePlugin.HAVENASK_THREAD_POOL_NAME).execute(() -> {
            if (maxRetries <= 0) {
                LOGGER.error("maxRetries must be greater than 0, maxRetries: [{}]", maxRetries);
                deleteLockAction.run();
                return;
            }
            if (sleepInterval <= 0) {
                LOGGER.error("sleepInterval must be greater than 0, sleepInterval: [{}]", sleepInterval);
                deleteLockAction.run();
                return;
            }

            boolean success = false;
            boolean shouldReleaseLock = false;
            int retryCount = 0;

            while (!success && retryCount < maxRetries) {
                try {
                    if (Objects.nonNull(metaDataSyncer)) {
                        metaDataSyncer.setSearcherPendingSync();
                        checkIsDeletedAction.run();
                    }
                    deleteRuntimeDirAction.accept(dir);
                    LOGGER.info("remove runtime dir successful, {}", logs);
                    success = true;
                    shouldReleaseLock = true;
                } catch (Exception e) {
                    LOGGER.warn("remove runtime dir failed, try to retry, {}, retry count: {}", logs, retryCount);
                    retryCount++;
                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep(sleepInterval);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            shouldReleaseLock = true;
                            LOGGER.warn("remove runtime dir interrupted, {}", logs);
                            break;
                        }
                    } else {
                        shouldReleaseLock = true;
                    }
                } finally {
                    if (shouldReleaseLock) {
                        deleteLockAction.run();
                        LOGGER.debug("release lock after remove runtime dir, {}", logs);
                    }
                }
            }

            if (!success) {
                LOGGER.error("Failed to remove runtime dir after [{}] retries, {}", maxRetries, logs);
            }
        });
    }

    private void checkIndexIsDeletedInSearcher(MetaDataSyncer metaDataSyncer, String tableName) throws IOException {
        long timeout = 60000;
        long sleepInterval = 1000;
        while (timeout > 0) {
            TargetInfo targetInfo = metaDataSyncer.getSearcherTargetInfo();
            if (targetInfo != null && false == targetInfo.table_info.containsKey(tableName)) {
                LOGGER.debug("targetInfo update successfully while deleting index, table name: [{}]", tableName);
                break;
            }
            if (targetInfo == null) {
                LOGGER.debug("targetInfo is null while deleting index, table name: [{}], try to retry", tableName);
            } else {
                LOGGER.debug("havenask table status still in searcher while deleting index, table name: [{}], try to retry", tableName);
            }
            timeout -= sleepInterval;
            try {
                Thread.sleep(sleepInterval);
            } catch (InterruptedException ex) {
                LOGGER.debug("check havenask table status interrupted while deleting index");
                throw new IOException("check havenask table status interrupted while deleting index");
            }
        }

        if (timeout <= 0) {
            throw new IOException("check havenask table status timeout while deleting index");
        }
    }

    private void checkShardIsDeletedInSearcher(MetaDataSyncer metaDataSyncer, String tableName, String partitionId) throws IOException {
        long timeout = 60000;
        long sleepInterval = 1000;
        while (timeout > 0) {
            TargetInfo targetInfo = metaDataSyncer.getSearcherTargetInfo();
            if (targetInfo != null && targetInfo.table_info.containsKey(tableName)) {
                TargetInfo.TableInfo tableInfo = null;
                int maxGeneration = -1;
                for (Map.Entry<String, TargetInfo.TableInfo> entry : targetInfo.table_info.get(tableName).entrySet()) {
                    String k = entry.getKey();
                    TargetInfo.TableInfo v = entry.getValue();
                    if (Integer.valueOf(k) > maxGeneration) {
                        tableInfo = v;
                        maxGeneration = Integer.valueOf(k);
                    }
                }

                if (tableInfo != null && tableInfo.partitions.containsKey(partitionId)) {
                    LOGGER.debug(
                        "shard info still in searcher while deleting shard, table name: [{}], partitionId: [{}], try to retry",
                        tableName,
                        partitionId
                    );
                } else {
                    LOGGER.debug(
                        "partition not found in tableInfo, delete shard successfully, table name: [{}], partitionId: [{}]",
                        tableName,
                        partitionId
                    );
                    break;
                }
            } else if (targetInfo == null) {
                LOGGER.debug(
                    "targetInfo is null while deleting shard, table name: [{}], partitionId: [{}], try to retry",
                    tableName,
                    partitionId
                );
            } else if (false == targetInfo.table_info.containsKey(tableName)) {
                LOGGER.debug(
                    "index not found in targetInfo, delete shard successfully, table name: [{}], partitionId: [{}]",
                    tableName,
                    partitionId
                );
                break;
            }

            timeout -= sleepInterval;
            try {
                Thread.sleep(sleepInterval);
            } catch (InterruptedException ex) {
                LOGGER.debug("check havenask table status interrupted while deleting index");
                throw new IOException("check havenask table status interrupted while deleting index");
            }
        }

        if (timeout <= 0) {
            throw new IOException("check shard info timeout while deleting shard");
        }
    }

    private void initConfig() throws IOException {
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(CLUSTER_DIR));
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(SCHEMAS_DIR));
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(DATA_TABLES_DIR));
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(PLUGINS_DIR));
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve("zones").resolve("general"));

        Files.createDirectories(configPath.resolve(TABLE_DIR).resolve("0").resolve(CLUSTER_DIR));
        Files.createDirectories(configPath.resolve(TABLE_DIR).resolve("0").resolve(SCHEMAS_DIR));
        Files.createDirectories(configPath.resolve(TABLE_DIR).resolve("0").resolve(DATA_TABLES_DIR));
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(PLUGINS_DIR));

        String clusterContent = "{\n"
            + "  \"build_option_config\": {\n"
            + "    \"async_build\": true,\n"
            + "    \"async_queue_size\": 1000,\n"
            + "    \"document_filter\": true,\n"
            + "    \"max_recover_time\": 30,\n"
            + "    \"sort_build\": true,\n"
            + "    \"sort_descriptions\": [\n"
            + "      {\n"
            + "        \"sort_field\": \"hits\",\n"
            + "        \"sort_pattern\": \"asc\"\n"
            + "      }\n"
            + "    ],\n"
            + "    \"sort_queue_mem\": 4096,\n"
            + "    \"sort_queue_size\": 10000000\n"
            + "  },\n"
            + "  \"cluster_config\": {\n"
            + "    \"builder_rule_config\": {\n"
            + "      \"batch_mode\": false,\n"
            + "      \"build_parallel_num\": 1,\n"
            + "      \"merge_parallel_num\": 1,\n"
            + "      \"partition_count\": 1\n"
            + "    },\n"
            + "    \"cluster_name\": \"in0\",\n"
            + "    \"hash_mode\": {\n"
            + "      \"hash_field\": \"id\",\n"
            + "      \"hash_function\": \"HASH\"\n"
            + "    },\n"
            + "    \"table_name\": \"in0\"\n"
            + "  },\n"
            + "  \"offline_index_config\": {\n"
            + "    \"build_config\": {\n"
            + "      \"building_memory_limit_mb\": 5120,\n"
            + "      \"keep_version_count\": 40\n"
            + "    }\n"
            + "  }\n"
            + "}\n";
        Files.write(
            configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(CLUSTER_DIR).resolve("in0_cluster.json"),
            clusterContent.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE
        );
        Files.write(
            configPath.resolve(TABLE_DIR).resolve("0").resolve(CLUSTER_DIR).resolve("in0_cluster.json"),
            clusterContent.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE
        );

        String schemaContent = "{\n"
            + "    \"attributes\": [\n"
            + "        \"id\",\n"
            + "        \"hits\",\n"
            + "        \"createtime\"\n"
            + "    ],\n"
            + "    \"fields\": [\n"
            + "        {\n"
            + "            \"analyzer\": \"singlews_analyzer\",\n"
            + "            \"field_name\": \"title\",\n"
            + "            \"field_type\": \"TEXT\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"analyzer\": \"singlews_analyzer\",\n"
            + "            \"field_name\": \"subject\",\n"
            + "            \"field_type\": \"TEXT\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"field_name\": \"id\",\n"
            + "            \"field_type\": \"UINT32\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"field_name\": \"hits\",\n"
            + "            \"field_type\": \"UINT32\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"field_name\": \"createtime\",\n"
            + "            \"field_type\": \"UINT64\"\n"
            + "        }\n"
            + "    ],\n"
            + "    \"indexs\": [\n"
            + "        {\n"
            + "            \"index_name\": \"id\",\n"
            + "            \"index_fields\": \"id\",\n"
            + "            \"index_type\": \"PRIMARYKEY64\",\n"
            + "            \"has_primary_key_attribute\": true,\n"
            + "            \"is_primary_key_sorted\": true\n"
            + "        },\n"
            + "        {\n"
            + "            \"index_name\": \"title\",\n"
            + "            \"index_fields\": \"title\",\n"
            + "            \"index_type\": \"TEXT\",\n"
            + "            \"doc_payload_flag\": 0,\n"
            + "            \"has_section_attribute\": false,\n"
            + "            \"position_list_flag\" : 0,\n"
            + "            \"position_payload_flag\": 0,\n"
            + "            \"term_frequency_bitmap\": 0,\n"
            + "            \"term_frequency_flag\": 0,\n"
            + "            \"term_payload_flag\": 0\n"
            + "        },\n"
            + "        {\n"
            + "            \"index_name\": \"default\",\n"
            + "            \"index_fields\": [\n"
            + "                {\n"
            + "                    \"boost\": 1,\n"
            + "                    \"field_name\": \"title\"\n"
            + "                },\n"
            + "                {\n"
            + "                    \"boost\": 1,\n"
            + "                    \"field_name\": \"subject\"\n"
            + "                }\n"
            + "            ],\n"
            + "            \"index_type\": \"PACK\",\n"
            + "            \"doc_payload_flag\": 0,\n"
            + "            \"has_section_attribute\": true,\n"
            + "            \"section_attribute_config\": {\n"
            + "                \"compress_type\": \"uniq | equal\",\n"
            + "                \"has_field_id\": false,\n"
            + "                \"has_section_weight\": true\n"
            + "            },\n"
            + "            \"position_list_flag\" : 0,\n"
            + "            \"position_payload_flag\": 0,\n"
            + "            \"term_frequency_bitmap\": 0,\n"
            + "            \"term_frequency_flag\": 0,\n"
            + "            \"term_payload_flag\": 0\n"
            + "        }\n"
            + "    ],\n"
            + "    \"summarys\": {\n"
            + "        \"compress\": true,\n"
            + "        \"summary_fields\": [\n"
            + "            \"title\",\n"
            + "            \"subject\",\n"
            + "            \"hits\",\n"
            + "            \"createtime\"\n"
            + "        ]\n"
            + "    },\n"
            + "    \"table_name\": \"in0\"\n"
            + "}\n";
        Files.write(
            configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(SCHEMAS_DIR).resolve("in0_schema.json"),
            schemaContent.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE
        );
        Files.write(
            configPath.resolve(TABLE_DIR).resolve("0").resolve(SCHEMAS_DIR).resolve("in0_schema.json"),
            schemaContent.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE
        );

        String analyzerContent = "{   \n"
            + "    \"analyzers\":\n"
            + "    {\n"
            + "        \"simple_analyzer\":\n"
            + "        {\n"
            + "            \"tokenizer_configs\" :\n"
            + "            {\n"
            + "                \"tokenizer_type\" : \"simple\",\n"
            + "                \"delimiter\" : \" \"\n"
            + "            },\n"
            + "            \"stopwords\" : [],\n"
            + "            \"normalize_options\" :\n"
            + "            {\n"
            + "                \"case_sensitive\" : false,\n"
            + "                \"traditional_sensitive\" : true,\n"
            + "                \"width_sensitive\" : false\n"
            + "            }\n"
            + "        },\n"
            + "        \"singlews_analyzer\":\n"
            + "        {\n"
            + "            \"tokenizer_configs\" :\n"
            + "            {\n"
            + "                \"tokenizer_type\" : \"singlews\"\n"
            + "            },\n"
            + "            \"stopwords\" : [],\n"
            + "            \"normalize_options\" :\n"
            + "            {\n"
            + "                \"case_sensitive\" : false,\n"
            + "                \"traditional_sensitive\" : true,\n"
            + "                \"width_sensitive\" : false\n"
            + "            }\n"
            + "        },\n"
            + "        \"jieba_analyzer\":\n"
            + "        {\n"
            + "            \"tokenizer_name\" : \"jieba_analyzer\",\n"
            + "            \"stopwords\" : [],\n"
            + "            \"normalize_options\" :\n"
            + "            {\n"
            + "                \"case_sensitive\" : false,\n"
            + "                \"traditional_sensitive\" : true,\n"
            + "                \"width_sensitive\" : false\n"
            + "            }\n"
            + "        }\n"
            + "    },\n"
            + "    \"tokenizer_config\" : {\n"
            + "        \"modules\" : [\n"
            + "            {\n"
            + "                \"module_name\": \"analyzer_plugin\",\n"
            + "                \"module_path\": \"libjieba_analyzer.so\",\n"
            + "                \"parameters\": { }\n"
            + "            }\n"
            + "        ],\n"
            + "        \"tokenizers\" : [\n"
            + "            {\n"
            + "                \"tokenizer_name\": \"jieba_analyzer\",\n"
            + "                \"tokenizer_type\": \"jieba\",\n"
            + "                \"module_name\": \"analyzer_plugin\",\n"
            + "                \"parameters\": {\n"
            + "                    \"dict_path\": \"/ha3_install/usr/local/share/jieba_dict/jieba.dict.utf8\",\n"
            + "                    \"hmm_path\": \"/ha3_install/usr/local/share/jieba_dict/hmm_model.utf8\",\n"
            + "                    \"user_dict_path\": \"/ha3_install/usr/local/share/jieba_dict/user.dict.utf8\",\n"
            + "                    \"idf_path\": \"/ha3_install/usr/local/share/jieba_dict/idf.utf8\",\n"
            + "                    \"stop_word_path\": \"/ha3_install/usr/local/share/jieba_dict/stop_words.utf8\"\n"
            + "                }\n"
            + "            }\n"
            + "        ]\n"
            + "    }\n"
            + "}\n";
        Files.write(
            configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve("analyzer.json"),
            analyzerContent.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE
        );
        Files.write(
            configPath.resolve(TABLE_DIR).resolve("0").resolve("analyzer.json"),
            analyzerContent.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE
        );

        String bizContent = "{\n"
            + "    \"turing_options_config\": {\n"
            + "        \"dependency_table\": [\n"
            + "            \"in0\"\n"
            + "        ]\n"
            + "    },\n"
            + "    \"cluster_config\": {\n"
            + "        \"hash_mode\": {\n"
            + "            \"hash_field\": \"docid\",\n"
            + "            \"hash_function\": \"HASH\"\n"
            + "        },\n"
            + "        \"query_config\": {\n"
            + "            \"default_index\": \"title\",\n"
            + "            \"default_operator\": \"AND\"\n"
            + "        },\n"
            + "        \"table_name\": \"in0\"\n"
            + "    }\n"
            + "}";
        Files.write(
            configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(DEFAULT_BIZ_CONFIG),
            bizContent.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE
        );

        String qrsContent = "{\n"
            + "    \"chains\" : [\n"
            + "        {\n"
            + "            \"chain_name\" : \"DEFAULT\",\n"
            + "            \"plugin_points\" : {\n"
            + "                \"BEFORE_PARSER_POINT\" : [\n"
            + "                ],\n"
            + "                \"BEFORE_SEARCH_POINT\" : [\n"
            + "                ],\n"
            + "                \"BEFORE_VALIDATE_POINT\" : [\n"
            + "                ]\n"
            + "            }\n"
            + "        }\n"
            + "    ],\n"
            + "    \"modules\" : [\n"
            + "    ],\n"
            + "    \"processors\" : [\n"
            + "    ],\n"
            + "    \"qrs_query_cache\" : {\n"
            + "        \"cache_time_out\" : 20,\n"
            + "        \"max_cache_size\" : 0\n"
            + "    },\n"
            + "    \"qrs_request_compress\" : {\n"
            + "        \"compress_type\" : \"z_speed_compress\"\n"
            + "    },\n"
            + "    \"qrs_result_compress\" : {\n"
            + "        \"compress_type\" : \"no_compress\"\n"
            + "    },\n"
            + "    \"qrs_rule\" : {\n"
            + "        \"connection_timeout\" : 750,\n"
            + "        \"return_hits_limit\" : 5000\n"
            + "    }\n"
            + "}\n";
        Files.write(
            configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve("qrs.json"),
            qrsContent.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE
        );

        String sqlContent = "{\n"
            + "    \"table_writer_config\": {\n"
            + "        \"zone_names\": [\n"
            + "            \"general\"\n"
            + "        ],\n"
            + "        \"allow_follow_write\": true\n"
            + "    }\n"
            + "}\n";
        Files.write(
            configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve("sql.json"),
            sqlContent.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE
        );
    }

    private void initRuntimeData(String part) throws IOException {
        Path dataPath = runtimedataPath.resolve("in0").resolve("generation_0").resolve(part);
        if (Files.exists(dataPath)) {
            return;
        }

        Files.createDirectories(dataPath);
        Files.write(
            dataPath.resolve(VERSION_FILE_NAME),
            VERSION_FILE_CONTENT.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        Files.write(
            dataPath.resolve(INDEX_FORMAT_VERSION_FILE_NAME),
            INDEX_FORMAT_VERSION_FILE_CONTENT.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        Files.write(
            dataPath.resolve(INDEX_PARTITION_META_FILE_NAME),
            INDEX_PARTITION_META_FILE_CONTENT.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        String strSchema = "{\n"
            + "\"attributes\":\n"
            + "  [\n"
            + "    \"id\",\n"
            + "    \"hits\",\n"
            + "    \"createtime\"\n"
            + "  ],\n"
            + "\"fields\":\n"
            + "  [\n"
            + "    {\n"
            + "    \"analyzer\":\n"
            + "      \"singlews_analyzer\",\n"
            + "    \"binary_field\":\n"
            + "      false,\n"
            + "    \"field_name\":\n"
            + "      \"title\",\n"
            + "    \"field_type\":\n"
            + "      \"TEXT\"\n"
            + "    },\n"
            + "    {\n"
            + "    \"analyzer\":\n"
            + "      \"singlews_analyzer\",\n"
            + "    \"binary_field\":\n"
            + "      false,\n"
            + "    \"field_name\":\n"
            + "      \"subject\",\n"
            + "    \"field_type\":\n"
            + "      \"TEXT\"\n"
            + "    },\n"
            + "    {\n"
            + "    \"binary_field\":\n"
            + "      false,\n"
            + "    \"field_name\":\n"
            + "      \"id\",\n"
            + "    \"field_type\":\n"
            + "      \"UINT32\"\n"
            + "    },\n"
            + "    {\n"
            + "    \"binary_field\":\n"
            + "      false,\n"
            + "    \"field_name\":\n"
            + "      \"hits\",\n"
            + "    \"field_type\":\n"
            + "      \"UINT32\"\n"
            + "    },\n"
            + "    {\n"
            + "    \"binary_field\":\n"
            + "      false,\n"
            + "    \"field_name\":\n"
            + "      \"createtime\",\n"
            + "    \"field_type\":\n"
            + "      \"UINT64\"\n"
            + "    }\n"
            + "  ],\n"
            + "\"indexs\":\n"
            + "  [\n"
            + "    {\n"
            + "    \"has_primary_key_attribute\":\n"
            + "      true,\n"
            + "    \"index_fields\":\n"
            + "      \"id\",\n"
            + "    \"index_name\":\n"
            + "      \"id\",\n"
            + "    \"index_type\":\n"
            + "      \"PRIMARYKEY64\",\n"
            + "    \"pk_hash_type\":\n"
            + "      \"default_hash\",\n"
            + "    \"pk_storage_type\":\n"
            + "      \"sort_array\"\n"
            + "    },\n"
            + "    {\n"
            + "    \"doc_payload_flag\":\n"
            + "      0,\n"
            + "    \"has_dict_inline_compress\":\n"
            + "      true,\n"
            + "    \"index_analyzer\":\n"
            + "      \"singlews_analyzer\",\n"
            + "    \"index_fields\":\n"
            + "      \"title\",\n"
            + "    \"index_name\":\n"
            + "      \"title\",\n"
            + "    \"index_type\":\n"
            + "      \"TEXT\",\n"
            + "    \"position_list_flag\":\n"
            + "      0,\n"
            + "    \"position_payload_flag\":\n"
            + "      0,\n"
            + "    \"term_frequency_bitmap\":\n"
            + "      0,\n"
            + "    \"term_frequency_flag\":\n"
            + "      0,\n"
            + "    \"term_payload_flag\":\n"
            + "      0\n"
            + "    },\n"
            + "    {\n"
            + "    \"doc_payload_flag\":\n"
            + "      0,\n"
            + "    \"has_dict_inline_compress\":\n"
            + "      true,\n"
            + "    \"index_analyzer\":\n"
            + "      \"singlews_analyzer\",\n"
            + "    \"index_fields\":\n"
            + "      [\n"
            + "        {\n"
            + "        \"boost\":\n"
            + "          1,\n"
            + "        \"field_name\":\n"
            + "          \"title\"\n"
            + "        },\n"
            + "        {\n"
            + "        \"boost\":\n"
            + "          1,\n"
            + "        \"field_name\":\n"
            + "          \"subject\"\n"
            + "        }\n"
            + "      ],\n"
            + "    \"index_name\":\n"
            + "      \"default\",\n"
            + "    \"index_type\":\n"
            + "      \"PACK\",\n"
            + "    \"position_list_flag\":\n"
            + "      0,\n"
            + "    \"position_payload_flag\":\n"
            + "      0,\n"
            + "    \"section_attribute_config\":\n"
            + "      {\n"
            + "      \"compress_type\":\n"
            + "        \"uniq|equal\",\n"
            + "      \"has_field_id\":\n"
            + "        false,\n"
            + "      \"has_section_weight\":\n"
            + "        true\n"
            + "      },\n"
            + "    \"term_frequency_bitmap\":\n"
            + "      0,\n"
            + "    \"term_frequency_flag\":\n"
            + "      0,\n"
            + "    \"term_payload_flag\":\n"
            + "      0\n"
            + "    }\n"
            + "  ],\n"
            + "\"summarys\":\n"
            + "  {\n"
            + "  \"compress\":\n"
            + "    true,\n"
            + "  \"summary_fields\":\n"
            + "    [\n"
            + "      \"title\",\n"
            + "      \"subject\",\n"
            + "      \"hits\",\n"
            + "      \"createtime\"\n"
            + "    ]\n"
            + "  },\n"
            + "\"table_name\":\n"
            + "  \"in0\",\n"
            + "\"table_type\":\n"
            + "  \"normal\"\n"
            + "}";
        Files.write(
            dataPath.resolve(SCHEMA_FILE_NAME),
            strSchema.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        String strDeployMeta = String.format(Locale.ROOT, DEPLOY_META_FILE_CONTENT_TEMPLATE, strSchema.length());
        Files.write(
            dataPath.resolve(DEPLOY_META_FILE_NAME),
            strDeployMeta.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        Files.write(
            dataPath.resolve(ENTRY_TABLE_FILE_NAME),
            String.format(Locale.ROOT, ENTRY_TABLE_FILE_CONTENT, strDeployMeta.length(), strSchema.length())
                .getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        );
    }
}
