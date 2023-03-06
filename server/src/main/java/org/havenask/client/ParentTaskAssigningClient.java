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

package org.havenask.client;

import org.havenask.action.ActionType;
import org.havenask.action.ActionListener;
import org.havenask.action.ActionRequest;
import org.havenask.action.ActionResponse;
import org.havenask.cluster.node.DiscoveryNode;
import org.havenask.tasks.Task;
import org.havenask.tasks.TaskId;

/**
 * A {@linkplain Client} that sets the parent task on all requests that it makes. Use this to conveniently implement actions that cause
 * many other actions.
 */
public class ParentTaskAssigningClient extends FilterClient {
    private final TaskId parentTask;

    /**
     * Standard constructor.
     */
    public ParentTaskAssigningClient(Client in, TaskId parentTask) {
        super(in);
        this.parentTask = parentTask;
    }

    /**
     * Convenience constructor for building the TaskId out of what is usually at hand.
     */
    public ParentTaskAssigningClient(Client in, DiscoveryNode localNode, Task parentTask) {
        this(in, new TaskId(localNode.getId(), parentTask.getId()));
    }

    /**
     * Fetch the wrapped client. Use this to make calls that don't set {@link ActionRequest#setParentTask(TaskId)}.
     */
    public Client unwrap() {
        return in();
    }

    @Override
    protected <Request extends ActionRequest, Response extends ActionResponse>
    void doExecute(ActionType<Response> action, Request request, ActionListener<Response> listener) {
        request.setParentTask(parentTask);
        super.doExecute(action, request, listener);
    }
}
