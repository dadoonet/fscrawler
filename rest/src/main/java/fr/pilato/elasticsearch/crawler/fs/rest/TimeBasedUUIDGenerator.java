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

package fr.pilato.elasticsearch.crawler.fs.rest;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This code is an adaptation from elasticsearch codebase.
 *
 * These are essentially flake ids (<a href="http://boundary.com/blog/2012/01/12/flake-a-decentralized-k-ordered-unique-id-generator-in-erlang">http://boundary.com/blog/2012/01/12/flake-a-decentralized-k-ordered-unique-id-generator-in-erlang</a>) but
 *  we use 6 (not 8) bytes for timestamp, and use 3 (not 2) bytes for sequence number. */

public class TimeBasedUUIDGenerator {

    private static final SecureRandom INSTANCE = new SecureRandom();

    private static byte[] getMacAddress() throws SocketException {
        Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
        while (en.hasMoreElements()) {
            NetworkInterface nint = en.nextElement();
            if (!nint.isLoopback()) {
                // Pick the first valid non loopback address we find
                byte[] address = nint.getHardwareAddress();
                if (isValidAddress(address)) {
                    return address;
                }
            }
        }
        // Could not find a mac address
        return null;
    }

    private static boolean isValidAddress(byte[] address) {
        if (address == null || address.length != 6) {
            return false;
        }
        for (byte b : address) {
            if (b != 0x00) {
                return true; // If any of the bytes are nonzero assume a good address
            }
        }
        return false;
    }

    private static byte[] getSecureMungedAddress() {
        byte[] address = null;
        try {
            address = getMacAddress();
        } catch (SocketException e) {
            // address will be set below
        }

        if (!isValidAddress(address)) {
            address = constructDummyMulticastAddress();
        }

        byte[] mungedBytes = new byte[6];
        INSTANCE.nextBytes(mungedBytes);
        for (int i = 0; i < 6; ++i) {
            mungedBytes[i] ^= address[i];
        }

        return mungedBytes;
    }

    private static byte[] constructDummyMulticastAddress() {
        byte[] dummy = new byte[6];
        INSTANCE.nextBytes(dummy);
        /*
         * Set the broadcast bit to indicate this is not a _real_ mac address
         */
        dummy[0] |= (byte) 0x01;
        return dummy;
    }

    // We only use bottom 3 bytes for the sequence number.  Paranoia: init with random int so that if JVM/OS/machine goes down, clock slips
    // backwards, and JVM comes back up, we are less likely to be on the same sequenceNumber at the same time:
    private final AtomicInteger sequenceNumber = new AtomicInteger(INSTANCE.nextInt());

    // Used to ensure clock moves forward:
    private long lastTimestamp;

    private static final byte[] SECURE_MUNGED_ADDRESS = getSecureMungedAddress();

    static {
        assert SECURE_MUNGED_ADDRESS.length == 6;
    }

    /** Puts the lower numberOfLongBytes from l into the array, starting index pos. */
    private static void putLong(byte[] array, long l, int pos, int numberOfLongBytes) {
        for (int i=0; i<numberOfLongBytes; ++i) {
            array[pos+numberOfLongBytes-i-1] = (byte) (l >>> (i*8));
        }
    }

    public String getBase64UUID()  {
        final int sequenceId = sequenceNumber.incrementAndGet() & 0xffffff;
        long timestamp = System.currentTimeMillis();

        synchronized (this) {
            // Don't let timestamp go backwards, at least "on our watch" (while this JVM is running).  We are still vulnerable if we are
            // shut down, clock goes backwards, and we restart... for this we randomize the sequenceNumber on init to decrease chance of
            // collision:
            timestamp = Math.max(lastTimestamp, timestamp);

            if (sequenceId == 0) {
                // Always force the clock to increment whenever sequence number is 0, in case we have a long time-slip backwards:
                timestamp++;
            }

            lastTimestamp = timestamp;
        }

        final byte[] uuidBytes = new byte[15];

        // Only use lower 6 bytes of the timestamp (this will suffice beyond the year 10000):
        putLong(uuidBytes, timestamp, 0, 6);

        // MAC address adds 6 bytes:
        System.arraycopy(SECURE_MUNGED_ADDRESS, 0, uuidBytes, 6, SECURE_MUNGED_ADDRESS.length);

        // Sequence number adds 3 bytes:
        putLong(uuidBytes, sequenceId, 12, 3);

        assert 9 + SECURE_MUNGED_ADDRESS.length == uuidBytes.length;

        return Base64.getUrlEncoder().withoutPadding().encodeToString(uuidBytes);
    }
}
