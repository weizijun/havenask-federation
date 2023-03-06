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
 *    http://www.apache.org/licenses/LICENSE-2.0
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

package org.havenask.search.profile;

import org.havenask.LegacyESVersion;
import org.havenask.common.ParseField;
import org.havenask.common.io.stream.StreamInput;
import org.havenask.common.io.stream.StreamOutput;
import org.havenask.common.io.stream.Writeable;
import org.havenask.common.unit.TimeValue;
import org.havenask.common.xcontent.InstantiatingObjectParser;
import org.havenask.common.xcontent.ToXContentObject;
import org.havenask.common.xcontent.XContentBuilder;
import org.havenask.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.havenask.common.xcontent.ConstructingObjectParser.constructorArg;
import static org.havenask.common.xcontent.ConstructingObjectParser.optionalConstructorArg;

/**
 * This class is the internal representation of a profiled Query, corresponding
 * to a single node in the query tree.  It is built after the query has finished executing
 * and is merely a structured representation, rather than the entity that collects the timing
 * profile (see InternalProfiler for that)
 * <p>
 * Each InternalProfileResult has a List of InternalProfileResults, which will contain
 * "children" queries if applicable
 */
public final class ProfileResult implements Writeable, ToXContentObject {
    static final ParseField TYPE = new ParseField("type");
    static final ParseField DESCRIPTION = new ParseField("description");
    static final ParseField BREAKDOWN = new ParseField("breakdown");
    static final ParseField DEBUG = new ParseField("debug");
    static final ParseField NODE_TIME = new ParseField("time");
    static final ParseField NODE_TIME_RAW = new ParseField("time_in_nanos");
    static final ParseField CHILDREN = new ParseField("children");

    private final String type;
    private final String description;
    private final Map<String, Long> breakdown;
    private final Map<String, Object> debug;
    private final long nodeTime;
    private final List<ProfileResult> children;

    public ProfileResult(String type, String description, Map<String, Long> breakdown, Map<String, Object> debug,
            long nodeTime, List<ProfileResult> children) {
        this.type = type;
        this.description = description;
        this.breakdown = Objects.requireNonNull(breakdown, "required breakdown argument missing");
        this.debug = debug == null ? org.havenask.common.collect.Map.of() : debug;
        this.children = children == null ? org.havenask.common.collect.List.of() : children;
        this.nodeTime = nodeTime;
    }

    /**
     * Read from a stream.
     */
    public ProfileResult(StreamInput in) throws IOException{
        this.type = in.readString();
        this.description = in.readString();
        this.nodeTime = in.readLong();
        breakdown = in.readMap(StreamInput::readString, StreamInput::readLong);
        if (in.getVersion().onOrAfter(LegacyESVersion.V_7_9_0)) {
            debug = in.readMap(StreamInput::readString, StreamInput::readGenericValue);
        } else {
            debug = org.havenask.common.collect.Map.of();
        }
        children = in.readList(ProfileResult::new);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(type);
        out.writeString(description);
        out.writeLong(nodeTime);            // not Vlong because can be negative
        out.writeMap(breakdown, StreamOutput::writeString, StreamOutput::writeLong);
        if (out.getVersion().onOrAfter(LegacyESVersion.V_7_9_0)) {
            out.writeMap(debug, StreamOutput::writeString, StreamOutput::writeGenericValue);
        }
        out.writeList(children);
    }

    /**
     * Retrieve the lucene description of this query (e.g. the "explain" text)
     */
    public String getLuceneDescription() {
        return description;
    }

    /**
     * Retrieve the name of the entry (e.g. "TermQuery" or "LongTermsAggregator")
     */
    public String getQueryName() {
        return type;
    }

    /**
     * The timing breakdown for this node.
     */
    public Map<String, Long> getTimeBreakdown() {
        return Collections.unmodifiableMap(breakdown);
    }

    /**
     * The debug information about the profiled execution.
     */
    public Map<String, Object> getDebugInfo() {
        return Collections.unmodifiableMap(debug);
    }

    /**
     * Returns the total time (inclusive of children) for this query node.
     *
     * @return  elapsed time in nanoseconds
     */
    public long getTime() {
        return nodeTime;
    }

    /**
     * Returns a list of all profiled children queries
     */
    public List<ProfileResult> getProfiledChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(TYPE.getPreferredName(), type);
        builder.field(DESCRIPTION.getPreferredName(), description);
        if (builder.humanReadable()) {
            builder.field(NODE_TIME.getPreferredName(), new TimeValue(getTime(), TimeUnit.NANOSECONDS).toString());
        }
        builder.field(NODE_TIME_RAW.getPreferredName(), getTime());
        builder.field(BREAKDOWN.getPreferredName(), breakdown);
        if (false == debug.isEmpty()) {
            builder.field(DEBUG.getPreferredName(), debug);
        }

        if (false == children.isEmpty()) {
            builder.startArray(CHILDREN.getPreferredName());
            for (ProfileResult child : children) {
                builder = child.toXContent(builder, params);
            }
            builder.endArray();
        }

        return builder.endObject();
    }

    private static final InstantiatingObjectParser<ProfileResult, Void> PARSER;
    static {
        InstantiatingObjectParser.Builder<ProfileResult, Void> parser =
                InstantiatingObjectParser.builder("profile_result", true, ProfileResult.class);
        parser.declareString(constructorArg(), TYPE);
        parser.declareString(constructorArg(), DESCRIPTION);
        parser.declareObject(constructorArg(), (p, c) -> p.map(), BREAKDOWN);
        parser.declareObject(optionalConstructorArg(), (p, c) -> p.map(), DEBUG);
        parser.declareLong(constructorArg(), NODE_TIME_RAW);
        parser.declareObjectArray(optionalConstructorArg(), (p, c) -> fromXContent(p), CHILDREN);
        PARSER = parser.build();
    }
    public static ProfileResult fromXContent(XContentParser p) throws IOException {
        return PARSER.parse(p, null);
    }
}
