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

package org.havenask.search.aggregations.bucket.range;

import org.havenask.common.geo.GeoDistance;
import org.havenask.common.unit.DistanceUnit;
import org.havenask.search.DocValueFormat;
import org.havenask.search.aggregations.Aggregator;
import org.havenask.search.aggregations.AggregatorFactories;
import org.havenask.search.aggregations.CardinalityUpperBound;
import org.havenask.search.aggregations.support.ValuesSource;
import org.havenask.search.internal.SearchContext;

import java.io.IOException;
import java.util.Map;

public interface GeoDistanceAggregatorSupplier {
    Aggregator build(
        String name,
        AggregatorFactories factories,
        GeoDistance distanceType,
        org.havenask.common.geo.GeoPoint origin,
        DistanceUnit units,
        ValuesSource valuesSource,
        DocValueFormat format,
        InternalRange.Factory rangeFactory,
        RangeAggregator.Range[] ranges,
        boolean keyed,
        SearchContext context,
        Aggregator parent,
        CardinalityUpperBound cardinality,
        Map<String, Object> metadata) throws IOException;
}
