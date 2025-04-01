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

package fr.pilato.elasticsearch.crawler.fs.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import org.github.gestalt.config.annotations.Config;

import java.util.Objects;

public class Server {

    public static final class PROTOCOL {
        public static final String LOCAL = "local";
        public static final String SSH = "ssh";
        public static final String FTP = "ftp";
        public static final int SSH_PORT = 22;
        public static final int FTP_PORT = 21;
    }

    @Config
    @Nullable private String hostname;
    @Config
    private int port;
    @Config
    @Nullable private String username;
    @Config
    @JsonIgnore @Nullable private String password;
    @Config(defaultVal = PROTOCOL.LOCAL)
    @Nullable private String protocol;
    @Config
    @Nullable private String pemPath;

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @JsonIgnore
    public String getPassword() {
        return password;
    }

    @JsonProperty
    public void setPassword(String password) {
        this.password = password;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;

        // Let's add some logic here. If SSH or FTP and the port is not set, we set it to the default SSH port
        if (PROTOCOL.SSH.equals(protocol) && port == 0) {
            port = PROTOCOL.SSH_PORT;
        } else if (PROTOCOL.FTP.equals(protocol) && port == 0) {
            port = PROTOCOL.FTP_PORT;
        }

        // For FTP, we set a default username if not set
        if (PROTOCOL.FTP.equals(protocol) && username == null) {
            username = "anonymous";
        }
    }

    public String getPemPath() {
        return pemPath;
    }

    public void setPemPath(String pemPath) {
        this.pemPath = pemPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Server server = (Server) o;

        if (port != server.port) return false;
        if (!Objects.equals(hostname, server.hostname)) return false;
        if (!Objects.equals(username, server.username)) return false;
        // We can't really test the password as it may be obfuscated
        if (!Objects.equals(protocol, server.protocol)) return false;
        return Objects.equals(pemPath, server.pemPath);

    }

    @Override
    public int hashCode() {
        int result = hostname != null ? hostname.hashCode() : 0;
        result = 31 * result + port;
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (protocol != null ? protocol.hashCode() : 0);
        result = 31 * result + (pemPath != null ? pemPath.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Server{" + "hostname='" + hostname + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                ", protocol='" + protocol + '\'' +
                ", pemPath='" + pemPath + '\'' +
                '}';
    }
}
