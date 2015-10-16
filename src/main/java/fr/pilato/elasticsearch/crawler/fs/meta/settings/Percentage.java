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

public class Percentage {

    private double value;

    private boolean percentage;

    public Percentage() {
        value = 0;
        percentage = false;
    }

    public Percentage(double value) {
        this(value, false);
    }

    public Percentage(double value, boolean percentage) {
        this.value = value;
        this.percentage = percentage;
    }

    public double value() {
        return value;
    }

    public void value(double value) {
        this.value = value;
    }

    public boolean percentage() {
        return percentage;
    }

    public void percentage(boolean percentage) {
        this.percentage = percentage;
    }

    public double asDouble() {
        if (percentage) {
            return value / 100.0;
        } else {
            throw new UnsupportedOperationException("Can not convert to double a non percentage value");
        }
    }

    public String format() {
        return "" + value + (percentage ? "%" : "");
    }

    @Override
    public String toString() {
        return format();
    }

    public static Percentage parse(String sValue) {
       return parse(sValue, null);
    }

    public static Percentage parse(String sValue, Percentage defaultValue) {
        if (sValue == null) {
            return defaultValue;
        }
        try {
            String lowerSValue = sValue.toLowerCase(Locale.ROOT).trim();
            if (lowerSValue.endsWith("%")) {
                double value = Double.parseDouble(lowerSValue.substring(0, lowerSValue.length() - 1));
                return new Percentage(value, true);
            } else {
                double value = Double.parseDouble(lowerSValue);
                return new Percentage(value, false);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Failed to parse percentage [" + sValue + "].");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Percentage that = (Percentage) o;

        if (Double.compare(that.value, value) != 0) return false;
        return percentage == that.percentage;

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(value);
        result = (int) (temp ^ (temp >>> 32));
        result = 31 * result + (percentage ? 1 : 0);
        return result;
    }
}
