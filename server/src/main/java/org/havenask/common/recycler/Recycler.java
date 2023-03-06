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

package org.havenask.common.recycler;

import org.havenask.common.lease.Releasable;

/**
 * A recycled object, note, implementations should support calling obtain and then recycle
 * on different threads.
 */
public interface Recycler<T> {

    interface Factory<T> {
        Recycler<T> build();
    }

    interface C<T> {

        /** Create a new empty instance of the given size. */
        T newInstance();

        /** Recycle the data. This operation is called when the data structure is released. */
        void recycle(T value);

        /** Destroy the data. This operation allows the data structure to release any internal resources before GC. */
        void destroy(T value);
    }

    interface V<T> extends Releasable {

        /** Reference to the value. */
        T v();

        /** Whether this instance has been recycled (true) or newly allocated (false). */
        boolean isRecycled();

    }

    V<T> obtain();

}
