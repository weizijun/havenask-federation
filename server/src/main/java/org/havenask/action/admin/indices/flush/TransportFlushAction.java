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

package org.havenask.action.admin.indices.flush;

import org.havenask.action.support.ActionFilters;
import org.havenask.action.support.DefaultShardOperationFailedException;
import org.havenask.action.support.replication.ReplicationResponse;
import org.havenask.action.support.replication.TransportBroadcastReplicationAction;
import org.havenask.cluster.metadata.IndexNameExpressionResolver;
import org.havenask.cluster.service.ClusterService;
import org.havenask.common.inject.Inject;
import org.havenask.index.shard.ShardId;
import org.havenask.transport.TransportService;

import java.util.List;

/**
 * Flush ActionType.
 */
public class TransportFlushAction
        extends TransportBroadcastReplicationAction<FlushRequest, FlushResponse, ShardFlushRequest, ReplicationResponse> {

    @Inject
    public TransportFlushAction(ClusterService clusterService, TransportService transportService,
                                ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver,
                                TransportShardFlushAction replicatedFlushAction) {
        super(FlushAction.NAME, FlushRequest::new, clusterService, transportService, actionFilters, indexNameExpressionResolver,
            replicatedFlushAction);
    }

    @Override
    protected ReplicationResponse newShardResponse() {
        return new ReplicationResponse();
    }

    @Override
    protected ShardFlushRequest newShardRequest(FlushRequest request, ShardId shardId) {
        return new ShardFlushRequest(request, shardId);
    }

    @Override
    protected FlushResponse newResponse(int successfulShards, int failedShards, int totalNumCopies, List
            <DefaultShardOperationFailedException> shardFailures) {
        return new FlushResponse(totalNumCopies, successfulShards, failedShards, shardFailures);
    }
}
