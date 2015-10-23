/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package fr.pilato.elasticsearch.crawler.fs.meta.settings;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class TimeValue {
    /** How many nano-seconds in one milli-second */
    public static final long NSEC_PER_MSEC = 1000000;

    public static TimeValue timeValueNanos(long nanos) {
        return new TimeValue(nanos, TimeUnit.NANOSECONDS);
    }

    public static TimeValue timeValueMillis(long millis) {
        return new TimeValue(millis, TimeUnit.MILLISECONDS);
    }

    public static TimeValue timeValueSeconds(long seconds) {
        return new TimeValue(seconds, TimeUnit.SECONDS);
    }

    public static TimeValue timeValueMinutes(long minutes) {
        return new TimeValue(minutes, TimeUnit.MINUTES);
    }

    public static TimeValue timeValueHours(long hours) {
        return new TimeValue(hours, TimeUnit.HOURS);
    }

    private long duration;

    private TimeUnit timeUnit;

    public TimeValue() {

    }

    public TimeValue(long millis) {
        this(millis, TimeUnit.MILLISECONDS);
    }

    public TimeValue(long duration, TimeUnit timeUnit) {
        this.duration = duration;
        this.timeUnit = timeUnit;
    }

    public long duration() {
        return duration;
    }

    public void duration(long duration) {
        this.duration = duration;
    }

    public TimeUnit timeUnit() {
        return timeUnit;
    }

    public void timeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public long nanos() {
        return timeUnit.toNanos(duration);
    }

    public long micros() {
        return timeUnit.toMicros(duration);
    }

    public long millis() {
        return timeUnit.toMillis(duration);
    }

    public long seconds() {
        return timeUnit.toSeconds(duration);
    }

    public long minutes() {
        return timeUnit.toMinutes(duration);
    }

    public long hours() {
        return timeUnit.toHours(duration);
    }

    public long days() {
        return timeUnit.toDays(duration);
    }

    public double microsFrac() {
        return ((double) nanos()) / C1;
    }

    public double millisFrac() {
        return ((double) nanos()) / C2;
    }

    public double secondsFrac() {
        return ((double) nanos()) / C3;
    }

    public double minutesFrac() {
        return ((double) nanos()) / C4;
    }

    public double hoursFrac() {
        return ((double) nanos()) / C5;
    }

    public double daysFrac() {
        return ((double) nanos()) / C6;
    }

    @Override
    public String toString() {
        if (duration < 0) {
            return Long.toString(duration);
        }
        long nanos = nanos();
        if (nanos == 0) {
            return "0s";
        }
        double value = nanos;
        String suffix = "nanos";
        if (nanos >= C6) {
            value = daysFrac();
            suffix = "d";
        } else if (nanos >= C5) {
            value = hoursFrac();
            suffix = "h";
        } else if (nanos >= C4) {
            value = minutesFrac();
            suffix = "m";
        } else if (nanos >= C3) {
            value = secondsFrac();
            suffix = "s";
        } else if (nanos >= C2) {
            value = millisFrac();
            suffix = "ms";
        } else if (nanos >= C1) {
            value = microsFrac();
            suffix = "micros";
        }
        return format1Decimals(value, suffix);
    }

    public static String format1Decimals(double value, String suffix) {
        String p = String.valueOf(value);
        int ix = p.indexOf('.') + 1;
        int ex = p.indexOf('E');
        char fraction = p.charAt(ix);
        if (fraction == '0') {
            if (ex != -1) {
                return p.substring(0, ix - 1) + p.substring(ex) + suffix;
            } else {
                return p.substring(0, ix - 1) + suffix;
            }
        } else {
            if (ex != -1) {
                return p.substring(0, ix) + fraction + p.substring(ex) + suffix;
            } else {
                return p.substring(0, ix) + fraction + suffix;
            }
        }
    }

    public static TimeValue parseTimeValue(String sValue, TimeValue defaultValue) {
        if (sValue == null) {
            return defaultValue;
        }
        try {
            String lowerSValue = sValue.toLowerCase(Locale.ROOT).trim();
            long duration = Long.parseLong(lowerSValue.substring(0, lowerSValue.length() - 1));
            TimeUnit unit;
            if (lowerSValue.endsWith("ms")) {
                // Well, with ms, we need to substring 2 chars
                duration = Long.parseLong(lowerSValue.substring(0, lowerSValue.length() - 2));
                unit = TimeUnit.MILLISECONDS;
            } else if (lowerSValue.endsWith("s")) {
                unit = TimeUnit.SECONDS;
            } else if (lowerSValue.endsWith("m")) {
                unit = TimeUnit.MINUTES;
            } else if (lowerSValue.endsWith("h")) {
                unit = TimeUnit.HOURS;
            } else if (lowerSValue.endsWith("d")) {
                unit = TimeUnit.HOURS;
            } else {
                throw new IllegalArgumentException("Failed to parse timevalue [" + sValue + "]: unit is missing or unrecognized");
            }
            return new TimeValue(duration, unit);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Failed to parse timevalue [" + sValue + "].");
        }
    }

    static final long C0 = 1L;
    static final long C1 = C0 * 1000L;
    static final long C2 = C1 * 1000L;
    static final long C3 = C2 * 1000L;
    static final long C4 = C3 * 60L;
    static final long C5 = C4 * 60L;
    static final long C6 = C5 * 24L;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TimeValue timeValue = (TimeValue) o;

        if (duration != timeValue.duration) return false;
        return timeUnit == timeValue.timeUnit;

    }

    @Override
    public int hashCode() {
        int result = (int) (duration ^ (duration >>> 32));
        result = 31 * result + (timeUnit != null ? timeUnit.hashCode() : 0);
        return result;
    }
}
