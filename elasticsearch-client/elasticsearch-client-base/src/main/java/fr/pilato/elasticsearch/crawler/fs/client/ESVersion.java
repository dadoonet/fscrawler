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

package fr.pilato.elasticsearch.crawler.fs.client;

public class ESVersion implements Comparable<ESVersion> {
   /**
     * Returns the version given its string representation, current version if the argument is null or empty
     */
    public static ESVersion fromString(String version) {
        final boolean snapshot = version.endsWith("-SNAPSHOT");
        if (snapshot) {
            version = version.substring(0, version.length() - 9);
        }
        String[] parts = version.split("[.-]");
        if (parts.length < 3 || parts.length > 4) {
            throw new IllegalArgumentException(
                    "the version needs to contain major, minor, and revision, and optionally the build: " + version);
        }

        try {
            final int rawMajor = Integer.parseInt(parts[0]);
            if (rawMajor >= 5 && snapshot) { // we don't support snapshot as part of the version here anymore
                throw new IllegalArgumentException("illegal version format - snapshots are only supported until version 2.x");
            }
            final int betaOffset = rawMajor < 5 ? 0 : 25;
            //we reverse the version id calculation based on some assumption as we can't reliably reverse the modulo
            final int major = rawMajor * 1000000;
            final int minor = Integer.parseInt(parts[1]) * 10000;
            final int revision = Integer.parseInt(parts[2]) * 100;


            int build = 99;
            if (parts.length == 4) {
                String buildStr = parts[3];
                if (buildStr.startsWith("alpha")) {
                    assert rawMajor >= 5 : "major must be >= 5 but was " + major;
                    build = Integer.parseInt(buildStr.substring(5));
                    assert build < 25 : "expected a beta build but " + build + " >= 25";
                } else if (buildStr.startsWith("Beta") || buildStr.startsWith("beta")) {
                    build = betaOffset + Integer.parseInt(buildStr.substring(4));
                    assert build < 50 : "expected a beta build but " + build + " >= 50";
                } else if (buildStr.startsWith("RC") || buildStr.startsWith("rc")) {
                    build = Integer.parseInt(buildStr.substring(2)) + 50;
                } else {
                    throw new IllegalArgumentException("unable to parse version " + version);
                }
            }

            return new ESVersion(major + minor + revision + build);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("unable to parse version " + version, e);
        }
    }

    public final int id;
    public final byte major;
    public final byte minor;
    public final byte revision;
    public final byte build;

    ESVersion(int id) {
        this.id = id;
        this.major = (byte) ((id / 1000000) % 100);
        this.minor = (byte) ((id / 10000) % 100);
        this.revision = (byte) ((id / 100) % 100);
        this.build = (byte) (id % 100);
    }

    public boolean after(ESVersion version) {
        return version.id < id;
    }

    public boolean onOrAfter(ESVersion version) {
        return version.id <= id;
    }

    public boolean before(ESVersion version) {
        return version.id > id;
    }

    public boolean onOrBefore(ESVersion version) {
        return version.id >= id;
    }

    @Override
    public int compareTo(ESVersion other) {
        return Integer.compare(this.id, other.id);
    }

    @Override
    public String toString() {
        return String.valueOf(major) + '.' + minor + '.' + revision;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ESVersion version = (ESVersion) o;

        if (id != version.id) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return id;
    }
}
