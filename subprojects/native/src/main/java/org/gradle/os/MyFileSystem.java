/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.os;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.UUID;

import com.google.common.base.Charsets;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@ThreadSafe
public abstract class MyFileSystem {
    private static final Logger logger = LoggerFactory.getLogger(MyFileSystem.class);
    private static final MyFileSystem INSTANCE = new ProbingFileSystem();

    public static MyFileSystem current() {
        return INSTANCE;
    }

    public abstract boolean isCaseSensitive();
    public abstract boolean isSymlinkAware();
    public abstract boolean getImplicitlyLocksFileOnOpen();

    private static class ProbingFileSystem extends MyFileSystem {
        final boolean caseSensitive = probeCaseSensitive();
        final boolean symlinkAware = probeSymlinkAware();
        final boolean implicitLock = probeImplicitlyLocksFileOnOpen();

        @Override
        public boolean isCaseSensitive() {
            return caseSensitive;
        }

        @Override
        public boolean isSymlinkAware() {
            return symlinkAware;
        }

        @Override
        public boolean getImplicitlyLocksFileOnOpen() {
            return implicitLock;
        }

        boolean probeCaseSensitive() {
            File file = null;
            try {
                file = File.createTempFile("gradle_case_sensitive_check", null, null);
                File upperCased = new File(file.getPath().toUpperCase());
                return sameFiles(file, upperCased);
            } catch (IOException e) {
                logger.warn("Failed to determine if current file system is case sensitive. Assuming it isn't.");
                return false;
            } finally {
                FileUtils.deleteQuietly(file);
            }
        }


        boolean probeSymlinkAware() {
            File file = null;
            File symlink = null;
            try {
                file = File.createTempFile("gradle_symlink_check", null, null);
                symlink = createUniqueTempFileName();
                int errorCode = PosixUtil.current().symlink(file.getPath(), symlink.getPath());
                return errorCode == 0 && sameFiles(file, symlink);
            } catch (IOException e) {
                logger.warn("Failed to determine if current file system is symlink aware. Assuming it isn't.");
                return false;
            } finally {
                FileUtils.deleteQuietly(file);
                FileUtils.deleteQuietly(symlink);
            }
        }

        boolean probeImplicitlyLocksFileOnOpen() {
            File file = null;
            FileChannel channel = null;
            try {
                file = File.createTempFile("gradle_file_lock_check", null, null);
                channel = new FileOutputStream(file).getChannel();
                return channel.tryLock() == null;
            } catch (IOException e) {
                logger.warn("Failed to determine if current file system implicitly locks file on open. Assuming it doesn't.");
                return false;
            } finally {
                Closeables.closeQuietly(channel);
                FileUtils.deleteQuietly(file);
            }
        }

        File createUniqueTempFileName() throws IOException {
            return new File(System.getProperty("java.io.tmpdir"), "gradle_unique_file_name" + UUID.randomUUID().toString());
        }

        boolean sameFiles(File file1, File file2) throws IOException {
            String secret = UUID.randomUUID().toString();
            Files.write(secret, file1, Charsets.UTF_8);
            return Files.readFirstLine(file2, Charsets.UTF_8).equals(secret);
        }
    }
}