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

package org.havenask.engine.rpc.arpc;

import java.io.Closeable;
import java.io.IOException;

import com.alibaba.search.common.arpc.ANetRPCChannel;
import com.alibaba.search.common.arpc.ANetRPCChannelManager;
import com.alibaba.search.common.arpc.ANetRPCController;

import com.google.protobuf.ServiceException;
import org.havenask.engine.rpc.HeartbeatTargetResponse;
import org.havenask.engine.rpc.SearcherClient;
import org.havenask.engine.rpc.UpdateHeartbeatTargetRequest;
import org.havenask.engine.rpc.WriteRequest;
import org.havenask.engine.rpc.WriteResponse;
import suez.service.proto.TableService;
import suez.service.proto.Write;

public class SearcherArpcClient implements SearcherClient, Closeable {

    private final ANetRPCChannelManager manager;
    private ANetRPCChannel channel;
    private TableService.BlockingInterface blockingStub;
    private final ANetRPCController controller = new ANetRPCController();
    private final int port;

    public SearcherArpcClient(int port) {
        this.manager = new ANetRPCChannelManager();
        this.port = port;
    }

    @Override
    public HeartbeatTargetResponse getHeartbeatTarget() throws IOException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public HeartbeatTargetResponse updateHeartbeatTarget(UpdateHeartbeatTargetRequest request) throws IOException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public WriteResponse write(WriteRequest request) {
        Write write = Write.newBuilder().setHashId(request.getHashid()).setStr(request.getSource()).build();
        suez.service.proto.WriteRequest writeRequest = suez.service.proto.WriteRequest.newBuilder().setTableName(request.getTable()).setFormat("ha3").addWrites(write).build();
        try {
            if (blockingStub == null) {
                init();
            }
            suez.service.proto.WriteResponse writeResponse = blockingStub.writeTable(controller, writeRequest);
            return null;
        } catch (ServiceException e) {
            return null;
        }
    }

    private void init() {
        channel = manager.openChannel("127.0.0.1", port);
        blockingStub = TableService.newBlockingStub(channel);
    }

    @Override
    public void close() {
        manager.dispose();
    }
}
