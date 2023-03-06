/*
*Copyright (c) 2021, Alibaba Group;
*Licensed under the Apache License, Version 2.0 (the "License");
*you may not use this file except in compliance with the License.
*You may obtain a copy of the License at

*   http://www.apache.org/licenses/LICENSE-2.0

*Unless required by applicable law or agreed to in writing, software
*distributed under the License is distributed on an "AS IS" BASIS,
*WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*See the License for the specific language governing permissions and
*limitations under the License.
*/

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Modifications Copyright Havenask Contributors. See
 * GitHub history for details.
 */

package org.havenask.action.admin.indices.readonly;

import org.havenask.cluster.ack.IndicesClusterStateUpdateRequest;
import org.havenask.cluster.metadata.IndexMetadata.APIBlock;

/**
 * Cluster state update request that allows to add a block to one or more indices
 */
public class AddIndexBlockClusterStateUpdateRequest extends IndicesClusterStateUpdateRequest<AddIndexBlockClusterStateUpdateRequest> {

    private final APIBlock block;
    private long taskId;

    public AddIndexBlockClusterStateUpdateRequest(final APIBlock block, final long taskId) {
        this.block = block;
        this.taskId = taskId;
    }

    public long taskId() {
        return taskId;
    }

    public APIBlock getBlock() {
        return block;
    }

    public AddIndexBlockClusterStateUpdateRequest taskId(final long taskId) {
        this.taskId = taskId;
        return this;
    }
}
