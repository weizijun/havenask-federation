package org.havenask.engine.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VersionUtils {

    private static Logger logger = LogManager.getLogger(VersionUtils.class);

    public static long getMaxVersion(Path path, long defaultVersion) throws IOException {
        try (Stream<Path> stream = Files.list(path)) {
            long maxVersion = stream.map(path1 -> path1.getFileName().toString())
                .filter(StringUtils::isNumeric)
                .map(Long::valueOf)
                .max(Long::compare)
                .orElse(defaultVersion);
            logger.info("path[{}] get max version: {}", path, maxVersion);
            return maxVersion;
        }
    }

}
