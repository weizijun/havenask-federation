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

package org.havenask.engine.index.engine;

import static org.havenask.engine.search.rest.RestHavenaskSqlAction.SQL_DATABASE;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import javax.management.MBeanTrustPermission;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.QueryCache;
import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.util.BytesRef;
import org.havenask.HavenaskException;
import org.havenask.action.bulk.BackoffPolicy;
import org.havenask.client.Client;
import org.havenask.client.OriginSettingClient;
import org.havenask.common.Nullable;
import org.havenask.common.bytes.BytesArray;
import org.havenask.common.bytes.BytesReference;
import org.havenask.common.collect.Tuple;
import org.havenask.common.lucene.index.HavenaskDirectoryReader;
import org.havenask.common.lucene.uid.VersionsAndSeqNoResolver.DocIdAndVersion;
import org.havenask.common.metrics.CounterMetric;
import org.havenask.common.settings.Settings;
import org.havenask.common.unit.TimeValue;
import org.havenask.common.util.SingleObjectCache;
import org.havenask.common.xcontent.XContentHelper;
import org.havenask.engine.HavenaskEngineEnvironment;
import org.havenask.engine.MetaDataSyncer;
import org.havenask.engine.NativeProcessControlService;
import org.havenask.engine.index.mapper.VectorField;
import org.havenask.engine.rpc.ArpcResponse;
import org.havenask.engine.rpc.QueryTableRequest;
import org.havenask.engine.rpc.QueryTableResponse;
import org.havenask.engine.rpc.SearcherClient;
import org.havenask.engine.rpc.TargetInfo;
import org.havenask.engine.rpc.WriteRequest;
import org.havenask.engine.rpc.WriteResponse;
import org.havenask.engine.rpc.arpc.SearcherArpcClient;
import org.havenask.engine.search.action.HavenaskSqlAction;
import org.havenask.engine.search.action.HavenaskSqlRequest;
import org.havenask.engine.search.action.HavenaskSqlResponse;
import org.havenask.engine.util.JsonPrettyFormatter;
import org.havenask.engine.util.RangeUtil;
import org.havenask.engine.util.Utils;
import org.havenask.index.engine.Engine;
import org.havenask.index.engine.EngineConfig;
import org.havenask.index.engine.EngineException;
import org.havenask.index.engine.InternalEngine;
import org.havenask.index.engine.TranslogLeafReader;
import org.havenask.index.mapper.IdFieldMapper;
import org.havenask.index.mapper.ParseContext;
import org.havenask.index.mapper.ParsedDocument;
import org.havenask.index.mapper.SourceFieldMapper;
import org.havenask.index.mapper.Uid;
import org.havenask.index.seqno.SequenceNumbers;
import org.havenask.index.shard.DocsStats;
import org.havenask.index.shard.ShardId;
import org.havenask.index.translog.Translog;
import org.havenask.index.translog.TranslogConfig;
import org.havenask.index.translog.TranslogDeletionPolicy;
import org.havenask.search.DefaultSearchContext;
import org.havenask.search.internal.ContextIndexSearcher;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import suez.service.proto.DocValue;
import suez.service.proto.ErrorCode;
import suez.service.proto.SingleAttrValue;
import suez.service.proto.SummaryValue;

public class HavenaskEngine extends InternalEngine {

    public static final String STACK_ORIGIN = "stack";

    private final Client client;
    private final SearcherArpcClient searcherClient;
    private final HavenaskEngineEnvironment env;
    private final NativeProcessControlService nativeProcessControlService;
    private final MetaDataSyncer metaDataSyncer;
    private final ShardId shardId;
    private final String tableName;
    private final boolean realTimeEnable;
    private final String kafkaTopic;
    private int kafkaPartition;
    private KafkaProducer<String, String> producer = null;
    private volatile HavenaskCommitInfo lastCommitInfo = null;
    private CheckpointCalc checkpointCalc = null;
    private final String partitionName;
    private final RangeUtil.PartitionRange partitionRange;
    private final SingleObjectCache<DocsStats> docsStatsCache;
    private final CounterMetric numDocDeletes = new CounterMetric();
    private final CounterMetric numDocIndexes = new CounterMetric();

    public HavenaskEngine(
        EngineConfig engineConfig,
        Client client,
        int searcherPort,
        HavenaskEngineEnvironment env,
        NativeProcessControlService nativeProcessControlService,
        MetaDataSyncer metaDataSyncer
    ) {
        super(engineConfig);

        this.client = new OriginSettingClient(client, STACK_ORIGIN);
        this.searcherClient = new SearcherArpcClient(searcherPort);
        this.env = env;
        this.nativeProcessControlService = nativeProcessControlService;
        this.metaDataSyncer = metaDataSyncer;
        this.shardId = engineConfig.getShardId();
        this.tableName = Utils.getHavenaskTableName(shardId);
        this.partitionName = RangeUtil.getRangePartition(engineConfig.getIndexSettings().getNumberOfShards(), shardId.id());
        this.partitionRange = RangeUtil.getRange(engineConfig.getIndexSettings().getNumberOfShards(), shardId.id());
        this.realTimeEnable = EngineSettings.HAVENASK_REALTIME_ENABLE.get(engineConfig.getIndexSettings().getSettings());
        this.kafkaTopic = realTimeEnable
            ? EngineSettings.HAVENASK_REALTIME_TOPIC_NAME.get(engineConfig.getIndexSettings().getSettings())
            : null;
        try {
            this.producer = realTimeEnable ? initKafkaProducer(engineConfig.getIndexSettings().getSettings()) : null;
            this.kafkaPartition = realTimeEnable ? getKafkaPartition(engineConfig.getIndexSettings().getSettings(), kafkaTopic) : -1;
        } catch (Exception e) {
            if (realTimeEnable && producer != null) {
                producer.close();
            }
            failEngine("init kafka producer failed", e);
            throw new EngineException(shardId, "init kafka producer failed", e);
        }

        long commitTimestamp = getLastCommittedSegmentInfos().userData.containsKey(HavenaskCommitInfo.COMMIT_TIMESTAMP_KEY)
            ? Long.valueOf(getLastCommittedSegmentInfos().userData.get(HavenaskCommitInfo.COMMIT_TIMESTAMP_KEY))
            : -1L;
        long commitVersion = getLastCommittedSegmentInfos().userData.containsKey(HavenaskCommitInfo.COMMIT_VERSION_KEY)
            ? Long.valueOf(getLastCommittedSegmentInfos().userData.get(HavenaskCommitInfo.COMMIT_VERSION_KEY))
            : 0;
        long commitCheckpoint = getLastCommittedSegmentInfos().userData.containsKey(SequenceNumbers.LOCAL_CHECKPOINT_KEY)
            ? Long.valueOf(getLastCommittedSegmentInfos().userData.get(SequenceNumbers.LOCAL_CHECKPOINT_KEY))
            : -1L;
        this.lastCommitInfo = new HavenaskCommitInfo(commitTimestamp, commitVersion, commitCheckpoint);
        this.checkpointCalc = new CheckpointCalc();
        if (commitTimestamp >= 0 && commitCheckpoint >= 0) {
            this.checkpointCalc.addCheckpoint(commitTimestamp, commitCheckpoint);
        }
        logger.info(
            "havenask engine init, shardId: {}, commitTimestamp: {}, commitVersion: {}, commitCheckpoint: {}",
            shardId,
            commitTimestamp,
            commitVersion,
            commitCheckpoint
        );

        // 加载配置表
        try {
            metaDataSyncer.setSearcherPendingSync();
            checkTableStatus();
        } catch (IOException e) {
            logger.error(() -> new ParameterizedMessage("shard [{}] activeTable exception", engineConfig.getShardId()), e);
            failEngine("active havenask table failed", e);
            throw new EngineException(shardId, "active havenask table failed", e);
        }

        nativeProcessControlService.addHavenaskEngine(this);
        final TimeValue refreshInterval = engineConfig.getIndexSettings().getRefreshInterval();
        docsStatsCache = new SingleObjectCache<>(refreshInterval, new DocsStats()) {
            private long lastRefreshTime = 0;
            private long indexes = 0;
            private long deletes = 0;
            private long lastDocCount = -1;
            private long newDocCount = -1;

            @Override
            protected DocsStats refresh() {
                indexes = numDocIndexes.count();
                deletes = numDocDeletes.count();
                lastRefreshTime = lastCommitInfo.getCommitTimestamp();

                // get doc count from havenask
                this.newDocCount = getDocCount();
                if (newDocCount >= 0) {
                    lastDocCount = newDocCount;
                }

                // get total size from du command
                long totalSize = nativeProcessControlService.getTableSize(env.getRuntimedataPath().resolve(tableName).toAbsolutePath());
                return new DocsStats(lastDocCount, 0, totalSize);
            }

            @Override
            protected boolean needsRefresh() {
                if (super.needsRefresh() == false) {
                    return false;
                }

                if (newDocCount < 0) {
                    return true;
                }

                if (indexes != numDocIndexes.count()) {
                    return true;
                }

                if (deletes != numDocDeletes.count()) {
                    return true;
                }

                if (lastRefreshTime != lastCommitInfo.getCommitTimestamp()) {
                    return true;
                }

                logger.trace(
                    "havenask engine docs stats cache not need refresh, shardId: {}, docCount: {}, indexes: {},"
                        + " deletes: {}, lastRefreshTime={}",
                    shardId,
                    newDocCount,
                    indexes,
                    deletes,
                    lastRefreshTime
                );

                return false;
            }
        };
    }

    static KafkaProducer<String, String> initKafkaProducer(Settings settings) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, EngineSettings.HAVENASK_REALTIME_BOOTSTRAP_SERVERS.get(settings));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        Thread.currentThread().setContextClassLoader(null);
        return AccessController.doPrivileged(
            (PrivilegedAction<KafkaProducer<String, String>>) () -> { return new KafkaProducer<>(props); },
            AccessController.getContext(),
            new MBeanTrustPermission("register")
        );
    }

    @Override
    public void close() throws IOException {
        super.close();
        logger.info("[{}] close havenask engine", shardId);
        if (realTimeEnable && producer != null) {
            producer.close();
        }

        searcherClient.close();
        nativeProcessControlService.removeHavenaskEngine(this);
    }

    /**
     * 获取kafka topic partition数量
     *
     * @param settings   settings
     * @param kafkaTopic kafkaTopic name
     * @return partition数量
     */
    static int getKafkaPartition(Settings settings, String kafkaTopic) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, EngineSettings.HAVENASK_REALTIME_BOOTSTRAP_SERVERS.get(settings));
        try (
            AdminClient adminClient = AccessController.doPrivileged((PrivilegedAction<AdminClient>) () -> KafkaAdminClient.create(props))
        ) {
            DescribeTopicsResult result = adminClient.describeTopics(Arrays.asList(kafkaTopic));
            Map<String, TopicDescription> topicDescriptionMap = null;
            try {
                topicDescriptionMap = result.all().get();
            } catch (Exception e) {
                throw new HavenaskException("get kafka partition exception", e);
            }
            TopicDescription topicDescription = topicDescriptionMap.get(kafkaTopic);

            return topicDescription.partitions().size();
        }
    }

    private void checkTableStatus() throws IOException {
        long timeout = 60000;
        long sleepInterval = 1000;
        String partitionId = RangeUtil.getRangeName(engineConfig.getIndexSettings().getNumberOfShards(), shardId.id());
        while (timeout > 0) {
            try {
                TargetInfo targetInfo = metaDataSyncer.getSearcherTargetInfo();
                if (targetInfo == null || false == targetInfo.table_info.containsKey(tableName)) {
                    throw new IOException("havenask table not found in searcher");
                }

                TargetInfo.TableInfo tableInfo = null;
                int maxGeneration = -1;
                for (Map.Entry<String, TargetInfo.TableInfo> entry : targetInfo.table_info.get(tableName).entrySet()) {
                    String k = entry.getKey();
                    TargetInfo.TableInfo v = entry.getValue();
                    if (Integer.valueOf(k) > maxGeneration) {
                        tableInfo = v;
                    }
                }

                if (tableInfo == null || false == tableInfo.partitions.containsKey(partitionId)) {
                    throw new IOException("havenask partition not found in searcher");
                }

                return;
            } catch (Exception e) {
                logger.debug(
                    () -> new ParameterizedMessage("shard [{}] checkTableStatus exception, waiting for retry", engineConfig.getShardId()),
                    e
                );
                timeout -= sleepInterval;
                try {
                    Thread.sleep(sleepInterval);
                } catch (InterruptedException ex) {
                    throw new IOException("shard [" + engineConfig.getShardId() + "] check havenask table status interrupted", ex);
                }
            }
        }

        if (timeout <= 0) {
            throw new IOException("shard [" + engineConfig.getShardId() + "] check havenask table status timeout");
        }
    }

    /**
     * convert lucene fields to indexlib fields.
     */
    static Map<String, String> toHaIndex(ParsedDocument parsedDocument) throws IOException {
        Map<String, String> haDoc = new HashMap<>();
        haDoc.put("_id", parsedDocument.id());
        if (parsedDocument.routing() != null) {
            haDoc.put("_routing", parsedDocument.routing());
        }
        if (parsedDocument.rootDoc() == null) {
            return haDoc;
        }
        ParseContext.Document rootDoc = parsedDocument.rootDoc();
        for (IndexableField field : rootDoc.getFields()) {
            String fieldName = field.name();
            if (haDoc.containsKey(fieldName) || fieldName.contains(".")) {
                continue;
            }

            // for string or number
            String stringVal = field.stringValue();
            if (Objects.isNull(stringVal)) {
                stringVal = Optional.ofNullable(field.numericValue()).map(Number::toString).orElse(null);
            }

            if (Objects.nonNull(stringVal)) {
                haDoc.put(field.name(), stringVal);
                continue;
            }

            BytesRef binaryVal = field.binaryValue();
            if (binaryVal == null) {
                throw new IOException("invalid field value!");
            }
            if (field.name().equals(IdFieldMapper.NAME)) {
                haDoc.put(field.name(), Uid.decodeId(binaryVal.bytes));
            } else if (field.name().equals(SourceFieldMapper.NAME)) {
                BytesReference bytes = new BytesArray(binaryVal);
                String src = XContentHelper.convertToJson(bytes, false, parsedDocument.getXContentType());
                haDoc.put(field.name(), src);
            } else if (field instanceof VectorField) {
                VectorField vectorField = (VectorField) field;
                float[] array = (float[]) VectorField.readValue(vectorField.binaryValue().bytes);
                int iMax = array.length - 1;
                StringBuilder b = new StringBuilder();
                for (int i = 0;; i++) {
                    b.append(array[i]);
                    if (i == iMax) {
                        break;
                    }
                    b.append(",");
                }
                haDoc.put(field.name(), b.toString());
            } else { // TODO other special fields support.
                haDoc.put(field.name(), binaryVal.utf8ToString());
            }
        }

        return haDoc;
    }

    /**
     * build producer record
     */
    static ProducerRecord<String, String> buildProducerRecord(
        String id,
        Operation.TYPE type,
        String topicName,
        int topicPartition,
        Map<String, String> haDoc
    ) {
        StringBuilder message = new StringBuilder();
        switch (type) {
            case INDEX:
                message.append("CMD=add\u001F\n");
                break;
            case DELETE:
                message.append("CMD=delete\u001F\n");
                break;
            default:
                throw new IllegalArgumentException("invalid operation type!");
        }

        for (Map.Entry<String, String> entry : haDoc.entrySet()) {
            message.append(entry.getKey()).append("=").append(entry.getValue()).append("\u001F\n");
        }
        message.append("\u001E\n");
        long hashId = HashAlgorithm.getHashId(id);
        long partition = HashAlgorithm.getPartitionId(hashId, topicPartition);

        return new ProducerRecord<>(topicName, (int) partition, id, message.toString());
    }

    static WriteRequest buildWriteRequest(String table, int hashId, Operation.TYPE type, Map<String, String> haDoc) {
        StringBuilder message = new StringBuilder();
        switch (type) {
            case INDEX:
                message.append("CMD=add\u001F\n");
                break;
            case DELETE:
                message.append("CMD=delete\u001F\n");
                break;
            default:
                throw new IllegalArgumentException("invalid operation type!");
        }

        for (Map.Entry<String, String> entry : haDoc.entrySet()) {
            message.append(entry.getKey()).append("=").append(entry.getValue()).append("\u001F\n");
        }
        message.append("\u001E\n");
        return new WriteRequest(table, hashId, message.toString());
    }

    @Override
    protected boolean assertSearcherIsWarmedUp(String source, SearcherScope scope) {
        // for havenask we don't need to care about "externalReaderManager.isWarmedup"
        return true;
    }

    @Override
    protected IndexResult indexIntoLucene(Index index, IndexingStrategy plan) throws IOException {
        long start = System.nanoTime();
        index.parsedDoc().updateSeqID(index.seqNo(), index.primaryTerm());
        index.parsedDoc().version().setLongValue(plan.versionForIndexing);
        Map<String, String> haDoc = toHaIndex(index.parsedDoc());
        if (realTimeEnable) {
            ProducerRecord<String, String> record = buildProducerRecord(
                index.id(),
                index.operationType(),
                kafkaTopic,
                kafkaPartition,
                haDoc
            );
            try {
                producer.send(record).get();
            } catch (Exception e) {
                throw new HavenaskException("havenask realtime index exception", e);
            }
            return new IndexResult(index.version(), index.primaryTerm(), index.seqNo(), true);
        } else {
            try {
                WriteRequest writeRequest = buildWriteRequest(tableName, partitionRange.first, index.operationType(), haDoc);
                WriteResponse writeResponse = retryWrite(shardId, searcherClient, writeRequest);
                if (writeResponse.getErrorCode() != null) {
                    throw new IOException(
                        "havenask index exception, error code: "
                            + writeResponse.getErrorCode()
                            + ", error message:"
                            + writeResponse.getErrorMessage()
                    );
                }
                if (logger.isTraceEnabled()) {
                    logger.trace(
                        "[{}] index into lucene, id: {}, version: {}, primaryTerm: {}, seqNo: {}, cost: {} us",
                        shardId,
                        index.id(),
                        index.version(),
                        index.primaryTerm(),
                        index.seqNo(),
                        (System.nanoTime() - start) / 1000
                    );
                }

                numDocIndexes.inc();
                return new IndexResult(index.version(), index.primaryTerm(), index.seqNo(), true);
            } catch (IOException e) {
                logger.warn("havenask index exception", e);
                failEngine(e.getMessage(), e);
                throw e;
            }
        }
    }

    @Override
    protected DeleteResult deleteInLucene(Delete delete, DeletionStrategy plan) throws IOException {
        Map<String, String> haDoc = new HashMap<>();
        haDoc.put(IdFieldMapper.NAME, delete.id());
        if (realTimeEnable) {
            ProducerRecord<String, String> record = buildProducerRecord(
                delete.id(),
                delete.operationType(),
                kafkaTopic,
                kafkaPartition,
                haDoc
            );
            try {
                producer.send(record).get();
            } catch (Exception e) {
                throw new HavenaskException("havenask realtime delete exception", e);
            }
            return new DeleteResult(delete.version(), delete.primaryTerm(), delete.seqNo(), true);
        } else {
            try {
                WriteRequest writeRequest = buildWriteRequest(tableName, partitionRange.first, delete.operationType(), haDoc);
                WriteResponse writeResponse = retryWrite(shardId, searcherClient, writeRequest);
                if (writeResponse.getErrorCode() != null) {
                    throw new IOException(
                        "havenask delete exception, error code: "
                            + writeResponse.getErrorCode()
                            + ", error message:"
                            + writeResponse.getErrorMessage()
                    );
                }

                numDocDeletes.inc();
                return new DeleteResult(delete.version(), delete.primaryTerm(), delete.seqNo(), true);
            } catch (IOException e) {
                logger.warn("havenask delete exception", e);
                failEngine(e.getMessage(), e);
                throw e;
            }
        }
    }

    static final TimeValue DEFAULT_TIMEOUT = TimeValue.timeValueMillis(50);
    static final int MAX_RETRY = 10;
    private static final Logger LOGGER = LogManager.getLogger(HavenaskEngine.class);

    static WriteResponse retryWrite(ShardId shardId, SearcherClient searcherClient, WriteRequest writeRequest) {
        return retryRpc(shardId, () -> searcherClient.write(writeRequest));
    }

    static <Response extends ArpcResponse> Response retryRpc(ShardId shardId, Supplier<Response> supplier) {
        Response response = supplier.get();
        if (isWriteRetry(response)) {
            long start = System.currentTimeMillis();
            // retry if write queue is full or write response is null
            Iterator<TimeValue> backoff = BackoffPolicy.exponentialBackoff(DEFAULT_TIMEOUT, MAX_RETRY).iterator();
            int retryCount = 0;
            while (backoff.hasNext()) {
                TimeValue timeValue = backoff.next();
                try {
                    Thread.sleep(timeValue.millis());
                } catch (InterruptedException e) {
                    LOGGER.info(
                        "[{}] havenask write retry interrupted, retry count: {}, cost: {} ms",
                        shardId,
                        retryCount,
                        System.currentTimeMillis() - start
                    );
                    return response;
                }
                response = supplier.get();
                retryCount++;
                if (false == isWriteRetry(response)) {
                    break;
                }
            }
            LOGGER.info(
                "[{}] havenask write retry, retry count: {}, cost: {} ms, final result: {}",
                shardId,
                retryCount,
                System.currentTimeMillis() - start,
                response
            );
        }

        return response;
    }

    private static boolean isWriteRetry(ArpcResponse arpcResponse) {
        if ((arpcResponse.getErrorCode() == ErrorCode.TBS_ERROR_UNKOWN && arpcResponse.getErrorMessage().contains("response is null"))
            || (arpcResponse.getErrorCode() == ErrorCode.TBS_ERROR_OTHERS
                && (arpcResponse.getErrorMessage().contains("doc queue is full")
                    || arpcResponse.getErrorMessage().contains("no valid table/range")))) {
            LOGGER.debug(
                "havenask write retry, error code: {}, error message: {}",
                arpcResponse.getErrorCode(),
                arpcResponse.getErrorMessage()
            );
            return true;
        } else {
            return false;
        }
    }

    @Override
    public GetResult get(Get get, BiFunction<String, SearcherScope, Searcher> searcherFactory) throws EngineException {
        try {
            QueryTableRequest queryTableRequest = new QueryTableRequest(tableName, partitionRange, get.id());
            QueryTableResponse queryTableResponse = retryRpc(shardId, () -> searcherClient.queryTable(queryTableRequest));

            if (queryTableResponse.getErrorCode() != null) {
                if (queryTableResponse.getErrorCode().equals(ErrorCode.TBS_ERROR_NO_RECORD)) {
                    return GetResult.NOT_EXISTS;
                }
                throw new IOException(
                    "havenask get exception, error code: "
                        + queryTableResponse.getErrorCode()
                        + ", error message:"
                        + queryTableResponse.getErrorMessage()
                );
            }

            if (queryTableResponse.getDocValues().size() == 0) {
                return GetResult.NOT_EXISTS;
            }

            assert queryTableResponse.getDocValues().size() == 1;
            DocValue docValue = queryTableResponse.getDocValues().get(0);
            String routing = null;
            long seqNo = 0;
            long primaryTerm = 0;
            long version = 0;
            String source = null;
            for (SingleAttrValue attrValue : docValue.getAttrValueList()) {
                switch (attrValue.getAttrName()) {
                    case "_seq_no":
                        seqNo = attrValue.getIntValue();
                        break;
                    case "_primary_term":
                        primaryTerm = attrValue.getIntValue();
                        break;
                    case "_version":
                        version = attrValue.getIntValue();
                        break;
                }
            }
            for (SummaryValue summaryValue : docValue.getSummaryValuesList()) {
                switch (summaryValue.getFieldName()) {
                    case "_source":
                        source = summaryValue.getValue();
                        break;
                    case "_routing":
                        routing = summaryValue.getValue();
                        routing = routing == null || routing.isEmpty() ? null : routing;
                        break;
                }
            }
            Translog.Index operation = new Translog.Index(
                get.type(),
                get.id(),
                seqNo,
                primaryTerm,
                version,
                source.getBytes(StandardCharsets.UTF_8),
                routing,
                -1L
            );
            TranslogLeafReader reader = new TranslogLeafReader(operation);
            DocIdAndVersion docIdAndVersion = new DocIdAndVersion(0, version, seqNo, primaryTerm, reader, 0);
            return new GetResult(null, docIdAndVersion, false);
        } catch (Exception e) {
            throw new EngineException(shardId, e.getMessage());
        }
    }

    /**
     * do nothing
     */
    public void forceMerge(
        boolean flush,
        int maxNumSegments,
        boolean onlyExpungeDeletes,
        boolean upgrade,
        boolean upgradeOnlyAncientSegments,
        @Nullable String forceMergeUUID
    ) throws EngineException {
        throw new UnsupportedOperationException("havenask engine not support force merge operation");
    }

    @Override
    protected Translog newTranslog(
        TranslogConfig translogConfig,
        String translogUUID,
        TranslogDeletionPolicy translogDeletionPolicy,
        LongSupplier globalCheckpointSupplier,
        LongSupplier primaryTermSupplier,
        LongConsumer persistedSequenceNumberConsumer
    ) throws IOException {
        LongSupplier checkpointSupplier = () -> lastCommitInfo != null ? lastCommitInfo.getCommitCheckpoint() : -1L;
        return new Translog(
            translogConfig,
            translogUUID,
            translogDeletionPolicy,
            checkpointSupplier,
            primaryTermSupplier,
            persistedSequenceNumberConsumer
        );
    }

    @Override
    public void refresh(String source) throws EngineException {
        maybeRefresh(source);
    }

    /**
     * 原lucene引擎的逻辑是定时向内存中刷segment,而havenask引擎重载后则是增加一次checkpointCalc的记录项,并且更新内存中的checkpoint等commit信息
     * 刷新间隔可以在创建索引时通过指定settings中的refresh_interval进行调整
     */
    @Override
    public boolean maybeRefresh(String source) throws EngineException {
        long time = System.currentTimeMillis();
        long checkpoint = getPersistedLocalCheckpoint();
        checkpointCalc.addCheckpoint(time, checkpoint);

        Tuple<Long, Long> tuple = Utils.getVersionAndIndexCheckpoint(
            env.getRuntimedataPath().resolve(tableName).resolve("generation_0").resolve(partitionName)
        );
        if (tuple == null) {
            logger.debug(
                "havenask engine maybeRefresh failed, checkpoint not found, source: {}, time: {}, checkpoint: {}, "
                    + "havenask time point: {}, current checkpoint: {}",
                source,
                time,
                checkpoint,
                -1,
                -1
            );
            return false;
        }

        long segmentVersion = tuple.v1();
        Long havenaskTime = tuple.v2();
        long havenaskTimePoint;

        if (havenaskTime != null) {
            havenaskTimePoint = havenaskTime / 1000;
        } else {
            havenaskTimePoint = -1;
        }

        long currentCheckpoint = checkpointCalc.getCheckpoint(havenaskTimePoint);

        logger.debug(
            "havenask engine maybeRefresh, source: {}, time: {}, checkpoint: {}, havenask time point: {}, current checkpoint: {}",
            source,
            time,
            checkpoint,
            havenaskTimePoint,
            currentCheckpoint
        );

        if (currentCheckpoint > lastCommitInfo.getCommitCheckpoint()) {
            logger.info(
                "havenask engine refresh checkpoint, checkpoint time: {}, current checkpoint: {}, last commit " + "checkpoint: {}",
                havenaskTime,
                currentCheckpoint,
                lastCommitInfo.getCommitCheckpoint()
            );
            refreshCommitInfo(havenaskTimePoint, segmentVersion, currentCheckpoint);
            return true;
        }
        return false;
    }

    @Override
    public boolean refreshNeeded() {
        return true;
    }

    @Override
    public SyncedFlushResult syncFlush(String syncId, CommitId expectedCommitId) throws EngineException {
        throw new UnsupportedOperationException("havenask engine not support sync flush operation");
    }

    /**
     * havenask不能主动触发刷盘(flush),因此周期刷盘的逻辑对于havenask引擎是无效的,这里将判断逻辑简单改为探测是否有新的commit信息
     */
    @Override
    public boolean shouldPeriodicallyFlush() {
        return hasNewCommitInfo();
    }

    /**
     * havenask不能主动触发刷盘(flush),havenask引擎刷盘和fed元数据刷盘并不同步,只能等待commit信息发生变化,再主动探测这个变化（探测时机可以是下次写doc或隔一段时间）才能触发fed元数据的flush
     */
    @Override
    protected boolean hasTriggerFlush(boolean force) {
        if (force || hasNewCommitInfo()) {
            logger.info("has new commit info, need to flush, force: {}", force);
            return true;
        } else {
            return false;
        }
    }

    /**
     * 刷新commit信息
     * @param commitTimestamp  commit timestamp
     * @param commitVersion  commit version
     */
    private void refreshCommitInfo(long commitTimestamp, long commitVersion, long localCheckpoint) {
        lastCommitInfo = new HavenaskCommitInfo(commitTimestamp, commitVersion, localCheckpoint);
    }

    /**
     * 判断commit信息是否发生变化
     */
    public boolean hasNewCommitInfo() {
        if (lastCommitInfo == null || lastCommitInfo.getCommitTimestamp() <= 0) {
            return false;
        }

        // get last timestamp having been committed in disk
        long lastCommitTimestamp = getLastCommittedSegmentInfos().userData.containsKey(HavenaskCommitInfo.COMMIT_TIMESTAMP_KEY)
            ? Long.valueOf(getLastCommittedSegmentInfos().userData.get(HavenaskCommitInfo.COMMIT_TIMESTAMP_KEY))
            : -1L;

        // if last commit timestamp in memory is newer than last commit timestamp in disk, it means commit info has changed
        if (lastCommitInfo.getCommitTimestamp() > lastCommitTimestamp) {
            logger.info(
                "commit info changed, synchronization is needed， memory last commit timestamp: {}, disk last commit timestamp: {}",
                lastCommitInfo.getCommitTimestamp(),
                lastCommitTimestamp
            );
            return true;
        } else {
            return false;
        }
    }

    /**
     * 返回已和havenask引擎同步的checkpoint,这个checkpoint是在内存中的,并不是已经持久化到磁盘的checkpoint,相比磁盘中的checkpoint可能已经变化
     * @return the local checkpoint that has been synchronized with havenask engine
     */
    public long getCommitLocalCheckpoint() {
        return lastCommitInfo.getCommitCheckpoint();
    }

    /**
     * add custom commit data to the commit data map
     * @param commitData the commit data
     */
    @Override
    protected void addCustomCommitData(Map<String, String> commitData) {
        commitData.put(HavenaskCommitInfo.COMMIT_TIMESTAMP_KEY, Long.toString(lastCommitInfo.getCommitTimestamp()));
        commitData.put(HavenaskCommitInfo.COMMIT_VERSION_KEY, Long.toString(lastCommitInfo.getCommitVersion()));
        commitData.put(SequenceNumbers.MAX_SEQ_NO, Long.toString(lastCommitInfo.getCommitCheckpoint()));
    }

    public static class HavenaskCommitInfo {
        public static final String COMMIT_TIMESTAMP_KEY = "commit_timestamp";
        public static final String COMMIT_VERSION_KEY = "commit_version";

        private final long commitTimestamp;
        private final long commitVersion;
        private final long commitCheckpoint;

        public HavenaskCommitInfo(long commitTimestamp, long commitVersion, long commitCheckpoint) {
            this.commitTimestamp = commitTimestamp;
            this.commitVersion = commitVersion;
            this.commitCheckpoint = commitCheckpoint;
        }

        public long getCommitTimestamp() {
            return commitTimestamp;
        }

        public long getCommitVersion() {
            return commitVersion;
        }

        public long getCommitCheckpoint() {
            return commitCheckpoint;
        }
    }

    @Override
    public DocsStats docStats() {
        return docsStatsCache.getOrRefresh();
    }

    long getDocCount() {
        long docCount = -1;
        try {
            String sql = String.format(Locale.ROOT, "select /*+ SCAN_ATTR(partitionIds='%d')*/ count(*) from %s", shardId.id(), tableName);
            String kvpair = "format:full_json;timeout:10000;databaseName:" + SQL_DATABASE;
            HavenaskSqlResponse response = client.execute(HavenaskSqlAction.INSTANCE, new HavenaskSqlRequest(sql, kvpair)).actionGet();
            JSONObject jsonObject = JsonPrettyFormatter.fromString(response.getResult());
            JSONObject sqlResult = jsonObject.getJSONObject("sql_result");
            JSONArray datas = sqlResult.getJSONArray("data");
            if (datas.size() != 0) {
                assert datas.size() == 1;
                JSONArray row = datas.getJSONArray(0);
                assert row.size() == 1;
                docCount = row.getLongValue(0);
            }
        } catch (Exception e) {
            logger.debug("havenask engine get doc stats error", e);
        }
        return docCount;
    }

    @Override
    public Searcher acquireSearcher(String source, SearcherScope scope, Function<Searcher, Searcher> wrapper) throws EngineException {
        try {
            ReferenceManager<HavenaskDirectoryReader> referenceManager = getReferenceManager(scope);
            HavenaskDirectoryReader acquire = referenceManager.acquire();
            return new HavenaskSearcher(
                client,
                shardId,
                source,
                acquire,
                engineConfig.getSimilarity(),
                engineConfig.getQueryCache(),
                engineConfig.getQueryCachingPolicy(),
                () -> {}
            );
        } catch (AlreadyClosedException ex) {
            throw ex;
        } catch (Exception ex) {
            maybeFailEngine("acquire_reader", ex);
            ensureOpen(ex); // throw EngineCloseException here if we are already closed
            logger.error(() -> new ParameterizedMessage("failed to acquire reader"), ex);
            throw new EngineException(shardId, "failed to acquire reader", ex);
        }
    }

    @Override
    public SearcherSupplier acquireSearcherSupplier(Function<Searcher, Searcher> wrapper, SearcherScope scope) throws EngineException {
        try {
            ReferenceManager<HavenaskDirectoryReader> referenceManager = getReferenceManager(scope);
            HavenaskDirectoryReader acquire = referenceManager.acquire();
            return new SearcherSupplier(wrapper) {
                @Override
                public Searcher acquireSearcherInternal(String source) {
                    return new HavenaskSearcher(
                        client,
                        shardId,
                        "search",
                        acquire,
                        engineConfig.getSimilarity(),
                        engineConfig.getQueryCache(),
                        engineConfig.getQueryCachingPolicy(),
                        () -> {}
                    );
                }

                @Override
                public void doClose() {
                    try {
                        referenceManager.release(acquire);
                    } catch (IOException e) {
                        throw new UncheckedIOException("failed to close", e);
                    } catch (AlreadyClosedException e) {
                        // This means there's a bug somewhere: don't suppress it
                        throw new AssertionError(e);
                    }
                }
            };
        } catch (Exception ex) {
            maybeFailEngine("acquire_reader", ex);
            ensureOpen(ex); // throw EngineCloseException here if we are already closed
            logger.error(() -> new ParameterizedMessage("failed to acquire reader"), ex);
            throw new EngineException(shardId, "failed to acquire reader", ex);
        }
    }

    public static class HavenaskSearcher extends Engine.Searcher {
        private final Client client;
        private final ShardId shardId;

        public HavenaskSearcher(
            Client client,
            ShardId shardId,
            String source,
            IndexReader reader,
            Similarity similarity,
            QueryCache queryCache,
            QueryCachingPolicy queryCachingPolicy,
            Closeable onClose
        ) {
            super(source, reader, similarity, queryCache, queryCachingPolicy, onClose);
            this.client = client;
            this.shardId = shardId;
        }

        @Override
        public ContextIndexSearcher createContextIndexSearcher(DefaultSearchContext searchContext, boolean lowLevelCancellation)
            throws IOException {
            return new HavenaskIndexSearcher(
                client,
                shardId,
                searchContext,
                getIndexReader(),
                getSimilarity(),
                getQueryCache(),
                getQueryCachingPolicy(),
                lowLevelCancellation
            );
        }
    }
}
