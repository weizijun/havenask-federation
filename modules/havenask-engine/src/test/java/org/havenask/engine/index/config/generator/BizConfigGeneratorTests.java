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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.havenask.Version;
import org.havenask.cluster.metadata.IndexMetadata;
import org.havenask.common.settings.Settings;
import org.havenask.index.Index;
import org.havenask.index.IndexSettings;
import org.havenask.index.codec.CodecService;
import org.havenask.index.engine.EngineConfig;
import org.havenask.index.mapper.MapperService;
import org.havenask.index.mapper.MapperServiceTestCase;
import org.havenask.index.shard.ShardId;

import static org.havenask.engine.index.config.generator.BizConfigGenerator.CLUSTER_DIR;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.CLUSTER_FILE_SUFFIX;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.SCHEMAS_DIR;
import static org.havenask.engine.index.config.generator.BizConfigGenerator.SCHEMAS_FILE_SUFFIX;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BizConfigGeneratorTests extends MapperServiceTestCase {
    public void testBasic() throws IOException {
        String indexName = randomAlphaOfLength(5);
        IndexMetadata build = IndexMetadata.builder(indexName)
            .settings(Settings.builder().put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT))
            .numberOfShards(1)
            .numberOfReplicas(0)
            .build();
        IndexSettings settings = new IndexSettings(build, Settings.EMPTY);
        MapperService mapperService = createMapperService(fieldMapping(b -> b.field("type", "keyword")));
        EngineConfig engineConfig = mock(EngineConfig.class);
        CodecService codecService = mock(CodecService.class);

        when(codecService.getMapperService()).thenReturn(mapperService);
        when(engineConfig.getShardId()).thenReturn(new ShardId(new Index(indexName, randomAlphaOfLength(5)), 0));
        when(engineConfig.getCodecService()).thenReturn(codecService);
        when(engineConfig.getIndexSettings()).thenReturn(settings);
        Path configPath = createTempDir();
        Files.createDirectories(configPath.resolve("0").resolve(CLUSTER_DIR));
        Files.createDirectories(configPath.resolve("0").resolve(SCHEMAS_DIR));
        BizConfigGenerator bizConfigGenerator = new BizConfigGenerator(engineConfig, configPath);
        bizConfigGenerator.generate();

        {
            Path clusterConfigPath = configPath.resolve("0").resolve(CLUSTER_DIR).resolve(indexName + CLUSTER_FILE_SUFFIX);
            assertTrue(Files.exists(clusterConfigPath));
            String content = Files.readString(clusterConfigPath);
            String expect = String.format(
                Locale.ROOT,
                "{\n"
                    + "\t\"build_option_config\":{\n"
                    + "\t\t\"async_build\":true,\n"
                    + "\t\t\"max_recover_time\":3\n"
                    + "\t},\n"
                    + "\t\"cluster_config\":{\n"
                    + "\t\t\"build_in_mem\":false,\n"
                    + "\t\t\"builder_rule_config\":{\n"
                    + "\t\t\t\"partition_count\":1\n"
                    + "\t\t},\n"
                    + "\t\t\"cluster_name\":\"%s\",\n"
                    + "\t\t\"hash_mode\":{\n"
                    + "\t\t\t\"hash_field\":\"id\",\n"
                    + "\t\t\t\"hash_function\":\"HASH\"\n"
                    + "\t\t},\n"
                    + "\t\t\"table_name\":\"%s\"\n"
                    + "\t},\n"
                    + "\t\"offline_index_config\":{\n"
                    + "\t\t\n"
                    + "\t},\n"
                    + "\t\"online_index_config\":{\n"
                    + "\t\t\"build_config\":{\n"
                    + "\t\t\t\"build_total_memory\":5120,\n"
                    + "\t\t\t\"dump_thread_count\":8,\n"
                    + "\t\t\t\"max_doc_count\":0\n"
                    + "\t\t},\n"
                    + "\t\t\"enable_async_dump_segment\":false,\n"
                    + "\t\t\"load_remain_flush_realtime_index\":false,\n"
                    + "\t\t\"max_realtime_memory_use\":800,\n"
                    + "\t\t\"on_disk_flush_realtime_index\":false\n"
                    + "\t}\n"
                    + "}",
                indexName,
                indexName
            );

            assertEquals(expect, content);
        }

        {
            Path schemaConfigPath = configPath.resolve("0").resolve(SCHEMAS_DIR).resolve(indexName + SCHEMAS_FILE_SUFFIX);
            assertTrue(Files.exists(schemaConfigPath));
            String content = Files.readString(schemaConfigPath);
            String expect = String.format(
                Locale.ROOT,
                "{\n"
                    + "\t\"attributes\":[\"_seq_no\",\"field\",\"_id\",\"_version\",\"_primary_term\","
                    + "\"_local_checkpoint\"],\n"
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
                    + "\t},{\n"
                    + "\t\t\"binary_field\":false,\n"
                    + "\t\t\"field_name\":\"_local_checkpoint\"\n"
                    + "\t}],\n"
                    + "\t\"indexs\":[{\n"
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
                    + "\t},{\n"
                    + "\t\t\"has_primary_key_attribute\":true,\n"
                    + "\t\t\"index_fields\":\"_id\",\n"
                    + "\t\t\"index_name\":\"_id\",\n"
                    + "\t\t\"index_type\":\"PRIMARYKEY64\",\n"
                    + "\t\t\"is_primary_key_sorted\":false\n"
                    + "\t}],\n"
                    + "\t\"summarys\":{\n"
                    + "\t\t\"summary_fields\":[\"_routing\",\"_source\",\"_id\",\"_local_checkpoint\"]\n"
                    + "\t},\n"
                    + "\t\"table_name\":\"%s\",\n"
                    + "\t\"table_type\":\"normal\"\n"
                    + "}",
                indexName
            );
            assertEquals(expect, content);
        }
    }
}