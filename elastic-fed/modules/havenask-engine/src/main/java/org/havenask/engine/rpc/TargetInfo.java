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

package org.havenask.engine.rpc;

import com.alibaba.fastjson.annotation.JSONField;
import org.havenask.engine.util.JsonPrettyFormatter;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class TargetInfo {
    public AppInfo app_info;
    public BizInfo biz_info;
    public CustomAppInfo custom_app_info;
    public ServiceInfo service_info;
    public Map<String, TableGroup> table_groups;
    public Map<String, Map<String, TableInfo>> table_info;
    public Boolean clean_disk;
    public Integer target_version;
    public String catalog_address;

    public static class AppInfo {
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            AppInfo appInfo = (AppInfo) o;
            return keep_count == appInfo.keep_count && Objects.equals(config_path, appInfo.config_path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(config_path, keep_count);
        }

        public String config_path;
        public Integer keep_count;
    }

    public static class BizInfo {
        public static class Biz {
            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                Biz biz = (Biz) o;
                return keep_count == biz.keep_count
                    && Objects.equals(config_path, biz.config_path)
                    && Objects.equals(custom_biz_info, biz.custom_biz_info);
            }

            @Override
            public int hashCode() {
                return Objects.hash(config_path, custom_biz_info, keep_count);
            }

            public String config_path;
            public CustomBizInfo custom_biz_info;
            public Integer keep_count;
        }

        public static class CustomBizInfo {

        }

        @JSONField(name = "default")
        public Biz default_biz;

        public BizInfo() {

        }

        public BizInfo(Path defaultConfigPath) {
            default_biz = new Biz();
            default_biz.config_path = defaultConfigPath.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BizInfo bizInfo = (BizInfo) o;
            return Objects.equals(default_biz, bizInfo.default_biz);
        }

        @Override
        public int hashCode() {
            return Objects.hash(default_biz);
        }
    }

    public static class CustomAppInfo {

    }

    public static class ServiceInfo {
        public static class Service {
            public String topo_info;

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                Service service = (Service) o;
                return Objects.equals(topo_info, service.topo_info);
            }

            @Override
            public int hashCode() {
                return Objects.hash(topo_info);
            }
        }

        public Service cm2;

        public String zone_name;
        public Integer part_id;
        public Integer part_count;
        public Integer version;
        public Map<String, List<Cm2Config>> cm2_config;

        public static class Cm2Config {
            public Integer part_count;
            public String biz_name;
            public String ip;
            public Integer version;
            public Integer part_id;
            public Integer tcp_port;
            public Boolean support_heartbeat;
            public Integer grpc_port;

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                Cm2Config cm2Config = (Cm2Config) o;
                return Objects.equals(part_count, cm2Config.part_count)
                    && Objects.equals(version, cm2Config.version)
                    && Objects.equals(part_id, cm2Config.part_id)
                    && Objects.equals(tcp_port, cm2Config.tcp_port)
                    && Objects.equals(support_heartbeat, cm2Config.support_heartbeat)
                    && Objects.equals(grpc_port, cm2Config.grpc_port)
                    && Objects.equals(biz_name, cm2Config.biz_name)
                    && Objects.equals(ip, cm2Config.ip);
            }

            @Override
            public int hashCode() {
                return Objects.hash(part_count, biz_name, ip, version, part_id, tcp_port, support_heartbeat, grpc_port);
            }
        }

        public ServiceInfo() {

        }

        public ServiceInfo(String zone_name, int part_id, int part_count, int version) {
            this.zone_name = zone_name;
            this.part_id = part_id;
            this.part_count = part_count;
            this.version = version;
        }

        public ServiceInfo(String zone_name, int part_id, int part_count) {
            this.zone_name = zone_name;
            this.part_id = part_id;
            this.part_count = part_count;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ServiceInfo that = (ServiceInfo) o;
            return Objects.equals(part_id, that.part_id)
                && Objects.equals(part_count, that.part_count)
                && Objects.equals(version, that.version)
                && Objects.equals(cm2, that.cm2)
                && Objects.equals(zone_name, that.zone_name)
                && Objects.equals(cm2_config, that.cm2_config);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cm2, zone_name, part_id, part_count, version, cm2_config);
        }
    }

    public static class TableGroup {
        public boolean broadcast = false;
        public List<String> table_names;
        public Set<Integer> unpublish_part_ids;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TableGroup that = (TableGroup) o;
            return broadcast == that.broadcast
                && Objects.equals(table_names, that.table_names)
                && Objects.equals(unpublish_part_ids, that.unpublish_part_ids);
        }

        @Override
        public int hashCode() {
            return Objects.hash(broadcast, table_names, unpublish_part_ids);
        }
    }

    public static class TableInfo {
        public static class Partition {
            public static class DeployStatus {
                public Integer deploy_status;
                public String local_config_path;

                @Override
                public boolean equals(Object o) {
                    if (this == o) {
                        return true;
                    }
                    if (o == null || getClass() != o.getClass()) {
                        return false;
                    }
                    DeployStatus that = (DeployStatus) o;
                    return Objects.equals(deploy_status, that.deploy_status) && Objects.equals(local_config_path, that.local_config_path);
                }

                @Override
                public int hashCode() {
                    return Objects.hash(deploy_status, local_config_path);
                }
            }

            public String check_index_path;
            public Integer deploy_status;
            public List<List<Object>> deploy_status_map;
            public Long inc_version;
            public Integer keep_count;
            public String loaded_config_path;
            public String loaded_index_root;
            public String local_index_path;
            public Integer rt_status;
            public Integer schema_version;
            public Integer table_load_type;
            public Integer table_status;
            public Integer table_type;

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                Partition partition = (Partition) o;
                return Objects.equals(deploy_status, partition.deploy_status)
                    && Objects.equals(inc_version, partition.inc_version)
                    && Objects.equals(keep_count, partition.keep_count)
                    && Objects.equals(rt_status, partition.rt_status)
                    && Objects.equals(schema_version, partition.schema_version)
                    && Objects.equals(table_load_type, partition.table_load_type)
                    && Objects.equals(table_status, partition.table_status)
                    && Objects.equals(table_type, partition.table_type)
                    && Objects.equals(check_index_path, partition.check_index_path)
                    && Objects.equals(deploy_status_map, partition.deploy_status_map)
                    && Objects.equals(loaded_config_path, partition.loaded_config_path)
                    && Objects.equals(loaded_index_root, partition.loaded_index_root)
                    && Objects.equals(local_index_path, partition.local_index_path);
            }

            @Override
            public int hashCode() {
                return Objects.hash(
                    check_index_path,
                    deploy_status,
                    deploy_status_map,
                    inc_version,
                    keep_count,
                    loaded_config_path,
                    loaded_index_root,
                    local_index_path,
                    rt_status,
                    schema_version,
                    table_load_type,
                    table_status,
                    table_type
                );
            }
        }

        public Integer table_mode;
        public Integer table_type;
        public Integer total_partition_count;
        public String config_path;
        public Boolean force_online;
        public String group_name;
        public String index_root;
        public Map<String, Partition> partitions;
        public String raw_index_root;
        public Integer rt_status;
        public Long timestamp_to_skip;

        public TableInfo() {

        }

        public TableInfo(
            Integer tableMode,
            Integer tableType,
            String configPath,
            String indexRoot,
            Integer totalPartitionCount,
            Map<String, Partition> partitions
        ) {
            table_mode = tableMode;
            table_type = tableType;
            config_path = configPath;
            index_root = indexRoot;
            total_partition_count = totalPartitionCount;
            this.partitions = partitions;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TableInfo tableInfo = (TableInfo) o;
            return Objects.equals(table_mode, tableInfo.table_mode)
                && Objects.equals(table_type, tableInfo.table_type)
                && Objects.equals(total_partition_count, tableInfo.total_partition_count)
                && Objects.equals(force_online, tableInfo.force_online)
                && Objects.equals(rt_status, tableInfo.rt_status)
                && Objects.equals(timestamp_to_skip, tableInfo.timestamp_to_skip)
                && Objects.equals(config_path, tableInfo.config_path)
                && Objects.equals(group_name, tableInfo.group_name)
                && Objects.equals(index_root, tableInfo.index_root)
                && Objects.equals(partitions, tableInfo.partitions)
                && Objects.equals(raw_index_root, tableInfo.raw_index_root);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                table_mode,
                table_type,
                total_partition_count,
                config_path,
                force_online,
                group_name,
                index_root,
                partitions,
                raw_index_root,
                rt_status,
                timestamp_to_skip
            );
        }
    }

    public static class CatalogAddress {
        public String catalog_address;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CatalogAddress that = (CatalogAddress) o;
            return Objects.equals(catalog_address, that.catalog_address);
        }

        @Override
        public int hashCode() {
            return Objects.hash(catalog_address);
        }
    }

    public static TargetInfo parse(String json) {
        return JsonPrettyFormatter.fromJsonString(json, TargetInfo.class);
    }

    @Override
    public String toString() {
        return JsonPrettyFormatter.toJsonString(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TargetInfo that = (TargetInfo) o;
        return Objects.equals(target_version, that.target_version)
            && Objects.equals(app_info, that.app_info)
            && Objects.equals(biz_info, that.biz_info)
            && Objects.equals(custom_app_info, that.custom_app_info)
            && Objects.equals(service_info, that.service_info)
            && Objects.equals(table_groups, that.table_groups)
            && Objects.equals(table_info, that.table_info)
            && Objects.equals(clean_disk, that.clean_disk)
            && Objects.equals(catalog_address, that.catalog_address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            app_info,
            biz_info,
            custom_app_info,
            service_info,
            table_groups,
            table_info,
            clean_disk,
            target_version,
            catalog_address
        );
    }
}
