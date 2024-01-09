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

package org.havenask.engine.index.config.generator;

import static java.util.Collections.singletonList;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.BIZ_DIR;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.CLUSTER_DIR;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.CLUSTER_FILE_SUFFIX;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.DATA_TABLES_DIR;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.DATA_TABLES_FILE_SUFFIX;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.DEFAULT_DIR;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.SCHEMAS_DIR;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.SCHEMAS_FILE_SUFFIX;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;

import org.havenask.cluster.metadata.IndexMetadata;
import org.havenask.common.settings.Settings;
import org.havenask.engine.HavenaskEnginePlugin;
import org.havenask.engine.index.config.BizConfig;
import org.havenask.engine.index.engine.EngineSettings;
import org.havenask.engine.index.mapper.DenseVectorFieldMapper;
import org.havenask.index.mapper.MapperService;
import org.havenask.index.mapper.MapperServiceTestCase;
import org.havenask.plugins.Plugin;

public class BizConfigGeneratorTests extends MapperServiceTestCase {
    @Override
    protected Collection<? extends Plugin> getPlugins() {
        return singletonList(new HavenaskEnginePlugin(Settings.EMPTY));
    }

    public void testBasic() throws IOException {
        String indexName = randomAlphaOfLength(5);
        MapperService mapperService = createMapperService(fieldMapping(b -> b.field("type", "keyword")));
        Path configPath = createTempDir();
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(CLUSTER_DIR));
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(SCHEMAS_DIR));
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(DATA_TABLES_DIR));
        BizConfigGenerator bizConfigGenerator = new BizConfigGenerator(indexName, Settings.EMPTY, mapperService, configPath);
        bizConfigGenerator.generate();

        {
            Path clusterConfigPath = configPath.resolve(BIZ_DIR)
                .resolve(DEFAULT_DIR)
                .resolve("0")
                .resolve(CLUSTER_DIR)
                .resolve(indexName + CLUSTER_FILE_SUFFIX);
            assertTrue(Files.exists(clusterConfigPath));
            String content = Files.readString(clusterConfigPath);

            BizConfig bizConfig = new BizConfig();
            bizConfig.cluster_config.cluster_name = indexName;
            bizConfig.cluster_config.table_name = indexName;
            bizConfig.wal_config.sink.queue_name = indexName;
            String expect = bizConfig.toString();

            assertEquals(expect, content);
        }

        {
            Path schemaConfigPath = configPath.resolve(BIZ_DIR)
                .resolve(DEFAULT_DIR)
                .resolve("0")
                .resolve(SCHEMAS_DIR)
                .resolve(indexName + SCHEMAS_FILE_SUFFIX);
            assertTrue(Files.exists(schemaConfigPath));
            String content = Files.readString(schemaConfigPath);
            String expect = String.format(
                Locale.ROOT,
                "{\n"
                    + "\t\"attributes\":[\"_seq_no\",\"field\",\"_id\",\"_version\",\"_primary_term\"],\n"
                    + "\t\"fields\":[{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"_routing\",\n"
                    + "\t\t\"field_type\":\"STRING\"\n"
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"_seq_no\",\n"
                    + "\t\t\"field_type\":\"INT64\"\n"
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"field\",\n"
                    + "\t\t\"field_type\":\"STRING\"\n"
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"_source\",\n"
                    + "\t\t\"field_type\":\"STRING\"\n"
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"_id\",\n"
                    + "\t\t\"field_type\":\"STRING\"\n"
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"_version\",\n"
                    + "\t\t\"field_type\":\"INT64\"\n"
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"_primary_term\",\n"
                    + "\t\t\"field_type\":\"INT64\"\n"
                    + "\t}],\n"
                    + "\t\"indexs\":[{\n"
                    + "\t\t\"has_primary_key_attribute\":true,\n"
                    + "\t\t\"index_fields\":\"_id\",\n"
                    + "\t\t\"index_name\":\"_id\",\n"
                    + "\t\t\"index_type\":\"PRIMARYKEY64\",\n"
                    + "\t\t\"is_primary_key_sorted\":false\n"
                    + "\t},{\n"
                    + "\t\t\"index_fields\":\"_routing\",\n"
                    + "\t\t\"index_name\":\"_routing\",\n"
                    + "\t\t\"index_type\":\"STRING\"\n"
                    + "\t},{\n"
                    + "\t\t\"index_fields\":\"_seq_no\",\n"
                    + "\t\t\"index_name\":\"_seq_no\",\n"
                    + "\t\t\"index_type\":\"NUMBER\"\n"
                    + "\t},{\n"
                    + "\t\t\"index_fields\":\"field\",\n"
                    + "\t\t\"index_name\":\"field\",\n"
                    + "\t\t\"index_type\":\"STRING\"\n"
                    + "\t}],\n"
                    + "\t\"settings\":{\n"
                    + "\t\t\"enable_all_text_field_meta\":true\n"
                    + "\t},\n"
                    + "\t\"summarys\":{\n"
                    + "\t\t\"compress\":true,\n"
                    + "\t\t\"summary_fields\":[\"_routing\",\"_source\",\"_id\"]\n"
                    + "\t},\n"
                    + "\t\"table_name\":\"%s\",\n"
                    + "\t\"table_type\":\"normal\"\n"
                    + "}",
                indexName
            );
            assertEquals(expect, content);
        }

        {
            Path dataTablesPath = configPath.resolve(BIZ_DIR)
                .resolve(DEFAULT_DIR)
                .resolve("0")
                .resolve(DATA_TABLES_DIR)
                .resolve(indexName + DATA_TABLES_FILE_SUFFIX);
            assertTrue(Files.exists(dataTablesPath));
            String content = Files.readString(dataTablesPath);
            String expect = String.format(
                Locale.ROOT,
                "{\n"
                    + "\t\"data_descriptions\":[],\n"
                    + "\t\"processor_chain_config\":[\n"
                    + "\t\t{\n"
                    + "\t\t\t\"clusters\":[\n"
                    + "\t\t\t\t\"%s\"\n"
                    + "\t\t\t],\n"
                    + "\t\t\t\"document_processor_chain\":[\n"
                    + "\t\t\t\t{\n"
                    + "\t\t\t\t\t\"class_name\":\"TokenizeDocumentProcessor\",\n"
                    + "\t\t\t\t\t\"module_name\":\"\",\n"
                    + "\t\t\t\t\t\"parameters\":{}\n"
                    + "\t\t\t\t}\n"
                    + "\t\t\t],\n"
                    + "\t\t\t\"modules\":[]\n"
                    + "\t\t}\n"
                    + "\t],\n"
                    + "\t\"processor_config\":{\n"
                    + "\t\t\"processor_queue_size\":2000,\n"
                    + "\t\t\"processor_thread_num\":30\n"
                    + "\t},\n"
                    + "\t\"processor_rule_config\":{\n"
                    + "\t\t\"parallel_num\":1,\n"
                    + "\t\t\"partition_count\":1\n"
                    + "\t}\n"
                    + "}",
                indexName
            );
            assertEquals(expect, content);
        }

        bizConfigGenerator.remove();
        Path clusterConfigPath = configPath.resolve(BIZ_DIR)
            .resolve(DEFAULT_DIR)
            .resolve("0")
            .resolve(CLUSTER_DIR)
            .resolve(indexName + CLUSTER_FILE_SUFFIX);
        assertFalse(Files.exists(clusterConfigPath));

        Path schemaConfigPath = configPath.resolve(BIZ_DIR)
            .resolve(DEFAULT_DIR)
            .resolve("0")
            .resolve(SCHEMAS_DIR)
            .resolve(indexName + SCHEMAS_FILE_SUFFIX);
        assertFalse(Files.exists(schemaConfigPath));

        Path dataTablesPath = configPath.resolve(BIZ_DIR)
            .resolve("0")
            .resolve(DATA_TABLES_DIR)
            .resolve(indexName + DATA_TABLES_FILE_SUFFIX);
        assertFalse(Files.exists(dataTablesPath));
    }

    public void testDupFieldProcessor() throws IOException {
        String indexName = randomAlphaOfLength(5);
        MapperService mapperService = createMapperService(mapping(b -> {
            {
                b.startObject("field");
                {
                    b.field("type", DenseVectorFieldMapper.CONTENT_TYPE);
                    b.field("dims", 128);
                }
                b.endObject();
            }
        }));
        Path configPath = createTempDir();
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(CLUSTER_DIR));
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(SCHEMAS_DIR));
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(DATA_TABLES_DIR));
        BizConfigGenerator bizConfigGenerator = new BizConfigGenerator(indexName, Settings.EMPTY, mapperService, configPath);
        bizConfigGenerator.generate();

        {
            Path clusterConfigPath = configPath.resolve(BIZ_DIR)
                .resolve(DEFAULT_DIR)
                .resolve("0")
                .resolve(CLUSTER_DIR)
                .resolve(indexName + CLUSTER_FILE_SUFFIX);
            assertTrue(Files.exists(clusterConfigPath));
            String content = Files.readString(clusterConfigPath);

            BizConfig bizConfig = new BizConfig();
            bizConfig.cluster_config.cluster_name = indexName;
            bizConfig.cluster_config.table_name = indexName;
            bizConfig.wal_config.sink.queue_name = indexName;
            String expect = bizConfig.toString();

            assertEquals(expect, content);
        }

        {
            Path schemaConfigPath = configPath.resolve(BIZ_DIR)
                .resolve(DEFAULT_DIR)
                .resolve("0")
                .resolve(SCHEMAS_DIR)
                .resolve(indexName + SCHEMAS_FILE_SUFFIX);
            assertTrue(Files.exists(schemaConfigPath));
            String content = Files.readString(schemaConfigPath);
            String expect = String.format(
                Locale.ROOT,
                "{\n"
                    + "\t\"attributes\":[\"_seq_no\",\"_id\",\"_version\",\"_primary_term\"],\n"
                    + "\t\"fields\":[{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"_routing\",\n"
                    + "\t\t\"field_type\":\"STRING\"\n"
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"_seq_no\",\n"
                    + "\t\t\"field_type\":\"INT64\"\n"
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"field\",\n"
                    + "\t\t\"field_type\":\"STRING\"\n"
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"_source\",\n"
                    + "\t\t\"field_type\":\"STRING\"\n"
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"_id\",\n"
                    + "\t\t\"field_type\":\"STRING\"\n"
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"_version\",\n"
                    + "\t\t\"field_type\":\"INT64\"\n"
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"DUP_field\",\n"
                    + "\t\t\"field_type\":\"RAW\"\n"
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"_primary_term\",\n"
                    + "\t\t\"field_type\":\"INT64\"\n"
                    + "\t}],\n"
                    + "\t\"indexs\":[{\n"
                    + "\t\t\"has_primary_key_attribute\":true,\n"
                    + "\t\t\"index_fields\":\"_id\",\n"
                    + "\t\t\"index_name\":\"_id\",\n"
                    + "\t\t\"index_type\":\"PRIMARYKEY64\",\n"
                    + "\t\t\"is_primary_key_sorted\":false\n"
                    + "\t},{\n"
                    + "\t\t\"index_fields\":\"_routing\",\n"
                    + "\t\t\"index_name\":\"_routing\",\n"
                    + "\t\t\"index_type\":\"STRING\"\n"
                    + "\t},{\n"
                    + "\t\t\"index_fields\":\"_seq_no\",\n"
                    + "\t\t\"index_name\":\"_seq_no\",\n"
                    + "\t\t\"index_type\":\"NUMBER\"\n"
                    + "\t},{\n"
                    + "\t\t\"index_fields\":[\n"
                    + "\t\t\t{\n"
                    + "\t\t\t\t\"boost\":1,\n"
                    + "\t\t\t\t\"field_name\":\"_id\"\n"
                    + "\t\t\t},\n"
                    + "\t\t\t{\n"
                    + "\t\t\t\t\"boost\":1,\n"
                    + "\t\t\t\t\"field_name\":\"DUP_field\"\n"
                    + "\t\t\t}\n"
                    + "\t\t],\n"
                    + "\t\t\"index_name\":\"field\",\n"
                    + "\t\t\"index_type\":\"CUSTOMIZED\",\n"
                    + "\t\t\"indexer\":\"aitheta2_indexer\",\n"
                    + "\t\t\"parameters\":{\n"
                    + "\t\t\t\"dimension\":\"128\",\n"
                    + "\t\t\t\"enable_rt_build\":\"true\",\n"
                    + "\t\t\t\"distance_type\":\"SquaredEuclidean\",\n"
                    + "\t\t\t\"ignore_invalid_doc\":\"true\",\n"
                    + "\t\t\t\"builder_name\":\"HnswBuilder\",\n"
                    + "\t\t\t\"searcher_name\":\"HnswSearcher\"\n"
                    + "\t\t}\n"
                    + "\t}],\n"
                    + "\t\"settings\":{\n"
                    + "\t\t\"enable_all_text_field_meta\":true\n"
                    + "\t},\n"
                    + "\t\"summarys\":{\n"
                    + "\t\t\"compress\":true,\n"
                    + "\t\t\"summary_fields\":[\"_routing\",\"_source\",\"_id\"]\n"
                    + "\t},\n"
                    + "\t\"table_name\":\"%s\",\n"
                    + "\t\"table_type\":\"normal\"\n"
                    + "}",
                indexName
            );
            assertEquals(expect, content);
        }

        {
            Path dataTablesPath = configPath.resolve(BIZ_DIR)
                .resolve(DEFAULT_DIR)
                .resolve("0")
                .resolve(DATA_TABLES_DIR)
                .resolve(indexName + DATA_TABLES_FILE_SUFFIX);
            assertTrue(Files.exists(dataTablesPath));
            String content = Files.readString(dataTablesPath);
            String expect = String.format(
                Locale.ROOT,
                "{\n"
                    + "\t\"data_descriptions\":[],\n"
                    + "\t\"processor_chain_config\":[\n"
                    + "\t\t{\n"
                    + "\t\t\t\"clusters\":[\n"
                    + "\t\t\t\t\"%s\"\n"
                    + "\t\t\t],\n"
                    + "\t\t\t\"document_processor_chain\":[\n"
                    + "\t\t\t\t{\n"
                    + "\t\t\t\t\t\"class_name\":\"TokenizeDocumentProcessor\",\n"
                    + "\t\t\t\t\t\"module_name\":\"\",\n"
                    + "\t\t\t\t\t\"parameters\":{}\n"
                    + "\t\t\t\t},\n"
                    + "\t\t\t\t{\n"
                    + "\t\t\t\t\t\"class_name\":\"DupFieldProcessor\",\n"
                    + "\t\t\t\t\t\"module_name\":\"\",\n"
                    + "\t\t\t\t\t\"parameters\":{\n"
                    + "\t\t\t\t\t\t\"DUP_field\":\"field\"\n"
                    + "\t\t\t\t\t}\n"
                    + "\t\t\t\t}\n"
                    + "\t\t\t],\n"
                    + "\t\t\t\"modules\":[]\n"
                    + "\t\t}\n"
                    + "\t],\n"
                    + "\t\"processor_config\":{\n"
                    + "\t\t\"processor_queue_size\":2000,\n"
                    + "\t\t\"processor_thread_num\":30\n"
                    + "\t},\n"
                    + "\t\"processor_rule_config\":{\n"
                    + "\t\t\"parallel_num\":1,\n"
                    + "\t\t\"partition_count\":1\n"
                    + "\t}\n"
                    + "}",
                indexName
            );
            assertEquals(expect, content);
        }

        bizConfigGenerator.remove();
        Path clusterConfigPath = configPath.resolve(BIZ_DIR)
            .resolve(DEFAULT_DIR)
            .resolve("0")
            .resolve(CLUSTER_DIR)
            .resolve(indexName + CLUSTER_FILE_SUFFIX);
        assertFalse(Files.exists(clusterConfigPath));

        Path schemaConfigPath = configPath.resolve(BIZ_DIR)
            .resolve(DEFAULT_DIR)
            .resolve("0")
            .resolve(SCHEMAS_DIR)
            .resolve(indexName + SCHEMAS_FILE_SUFFIX);
        assertFalse(Files.exists(schemaConfigPath));

        Path dataTablesPath = configPath.resolve(BIZ_DIR)
            .resolve("0")
            .resolve(DATA_TABLES_DIR)
            .resolve(indexName + DATA_TABLES_FILE_SUFFIX);
        assertFalse(Files.exists(dataTablesPath));
    }

    public void testFullSettings() throws IOException {
        String indexName = randomAlphaOfLength(5);
        MapperService mapperService = createMapperService(fieldMapping(b -> b.field("type", "keyword")));
        Path configPath = createTempDir();
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(CLUSTER_DIR));
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(SCHEMAS_DIR));
        Files.createDirectories(configPath.resolve(BIZ_DIR).resolve(DEFAULT_DIR).resolve("0").resolve(DATA_TABLES_DIR));
        BizConfigGenerator bizConfigGenerator = new BizConfigGenerator(
            indexName,
            Settings.builder()
                .put(IndexMetadata.SETTING_NUMBER_OF_SHARDS, 3)
                .put(EngineSettings.HAVENASK_BUILD_CONFIG_MAX_DOC_COUNT.getKey(), 10)
                .put(EngineSettings.HAVENASK_WAL_CONFIG_SINK_QUEUE_SIZE.getKey(), 100)
                .put(EngineSettings.HAVENASK_HASH_MODE_HASH_FIELD.getKey(), "test")
                .build(),
            mapperService,
            configPath
        );
        bizConfigGenerator.generate();

        {
            Path clusterConfigPath = configPath.resolve(BIZ_DIR)
                .resolve(DEFAULT_DIR)
                .resolve("0")
                .resolve(CLUSTER_DIR)
                .resolve(indexName + CLUSTER_FILE_SUFFIX);
            assertTrue(Files.exists(clusterConfigPath));
            String content = Files.readString(clusterConfigPath);

            BizConfig bizConfig = new BizConfig();
            bizConfig.cluster_config.cluster_name = indexName;
            bizConfig.cluster_config.table_name = indexName;
            bizConfig.wal_config.sink.queue_name = indexName;
            bizConfig.cluster_config.builder_rule_config.partition_count = 3;
            bizConfig.online_index_config.build_config.max_doc_count = 10;
            bizConfig.wal_config.sink.queue_size = "100";
            bizConfig.cluster_config.hash_mode.hash_field = "test";

            String expect = bizConfig.toString();

            assertEquals(expect, content);
        }
    }
}
