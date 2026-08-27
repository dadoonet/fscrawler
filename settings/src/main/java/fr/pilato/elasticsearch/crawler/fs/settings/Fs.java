/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.Percentage;
import fr.pilato.elasticsearch.crawler.fs.framework.SignTool;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import jakarta.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.github.gestalt.config.annotations.Config;

@SuppressWarnings("SameParameterValue")
public class Fs {
    @Config(defaultVal = Defaults.DEFAULT_DIR)
    private String url;

    @Config(defaultVal = "15m")
    private TimeValue updateRate;

    @Config
    @Nullable
    private List<String> includes;

    @Config(defaultVal = "*/~*,*/.ds_store")
    private List<String> excludes;

    @Config
    @Nullable
    private List<String> filters;

    @Config(defaultVal = "false")
    private boolean jsonSupport;

    @Config(defaultVal = "false")
    private boolean addAsInnerObject;

    @Config(defaultVal = "false")
    private boolean xmlSupport;

    @Config(defaultVal = "false")
    private boolean followSymlinks;

    @Config(defaultVal = "true")
    private boolean removeDeleted;

    @Config(defaultVal = "false")
    private boolean continueOnError;

    @Config
    @Nullable
    private ByteSizeValue ignoreAbove;

    @Config(defaultVal = "false")
    private boolean filenameAsId;

    @Config(defaultVal = "true")
    private boolean addFilesize;

    @Config(defaultVal = "false")
    private boolean attributesSupport;

    @Config(defaultVal = "false")
    private boolean aclSupport;

    @Config(defaultVal = "false")
    private boolean storeSource;

    @Config(defaultVal = "true")
    private boolean indexContent;

    @Config
    @Nullable
    private Percentage indexedChars;

    @Config(defaultVal = "false")
    private boolean rawMetadata;

    @Config
    @Nullable
    private String checksum;

    @Config(defaultVal = SignTool.LEGACY_MD5_ALGORITHM)
    private String hashAlgorithm;

    @Config(defaultVal = "true")
    private boolean indexFolders;

    @Config(defaultVal = "false")
    private boolean langDetect;

    @Config
    @Nullable
    private String tikaConfigPath;

    @Config
    @Nullable
    private String tempDir;

    @Config
    @Nullable
    private Ocr ocr;

    @Config
    @Nullable
    private String provider;

    /**
     * Opaque per-provider configuration ({@code fs.providers.<type>}). Loaded from YAML/JSON by
     * {@link FsSettingsLoader}; Gestalt cannot decode mixed nested maps into {@code Map&lt;String, Object&gt;}. Each
     * filesystem plugin parses its own section; this class does not model those shapes.
     */
    @Nullable
    private Map<String, Object> providers;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public TimeValue getUpdateRate() {
        return updateRate;
    }

    public void setUpdateRate(TimeValue updateRate) {
        this.updateRate = updateRate;
    }

    public List<String> getIncludes() {
        return includes;
    }

    public void setIncludes(List<String> includes) {
        this.includes = includes;
    }

    public List<String> getExcludes() {
        return excludes;
    }

    public void setExcludes(List<String> excludes) {
        this.excludes = excludes;
    }

    public List<String> getFilters() {
        return filters;
    }

    public void setFilters(List<String> filters) {
        this.filters = filters;
    }

    public boolean isJsonSupport() {
        return jsonSupport;
    }

    public void setJsonSupport(boolean jsonSupport) {
        this.jsonSupport = jsonSupport;
    }

    public boolean isFilenameAsId() {
        return filenameAsId;
    }

    public void setFilenameAsId(boolean filenameAsId) {
        this.filenameAsId = filenameAsId;
    }

    public boolean isAddFilesize() {
        return addFilesize;
    }

    public void setAddFilesize(boolean addFilesize) {
        this.addFilesize = addFilesize;
    }

    public boolean isRemoveDeleted() {
        return removeDeleted;
    }

    public void setRemoveDeleted(boolean removeDeleted) {
        this.removeDeleted = removeDeleted;
    }

    public boolean isAddAsInnerObject() {
        return addAsInnerObject;
    }

    public void setAddAsInnerObject(boolean addAsInnerObject) {
        this.addAsInnerObject = addAsInnerObject;
    }

    public boolean isStoreSource() {
        return storeSource;
    }

    public void setStoreSource(boolean storeSource) {
        this.storeSource = storeSource;
    }

    public Percentage getIndexedChars() {
        return indexedChars;
    }

    public void setIndexedChars(Percentage indexedChars) {
        this.indexedChars = indexedChars;
    }

    public boolean isIndexContent() {
        return indexContent;
    }

    public void setIndexContent(boolean indexContent) {
        this.indexContent = indexContent;
    }

    public boolean isAttributesSupport() {
        return attributesSupport;
    }

    public void setAttributesSupport(boolean attributesSupport) {
        this.attributesSupport = attributesSupport;
    }

    public boolean isAclSupport() {
        return aclSupport;
    }

    public void setAclSupport(boolean aclSupport) {
        this.aclSupport = aclSupport;
    }

    public boolean isRawMetadata() {
        return rawMetadata;
    }

    public void setRawMetadata(boolean rawMetadata) {
        this.rawMetadata = rawMetadata;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public void setHashAlgorithm(String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    public boolean isXmlSupport() {
        return xmlSupport;
    }

    public void setXmlSupport(boolean xmlSupport) {
        this.xmlSupport = xmlSupport;
    }

    public boolean isIndexFolders() {
        return indexFolders;
    }

    public void setIndexFolders(boolean indexFolders) {
        this.indexFolders = indexFolders;
    }

    public boolean isLangDetect() {
        return langDetect;
    }

    public void setLangDetect(boolean langDetect) {
        this.langDetect = langDetect;
    }

    public boolean isContinueOnError() {
        return continueOnError;
    }

    public void setContinueOnError(boolean continueOnError) {
        this.continueOnError = continueOnError;
    }

    /**
     * Enables or disables OCR on PDF documents.
     *
     * @param pdfOcr {@code true} to enable OCR on PDF files
     * @deprecated since 3.0, replaced by the {@code ocr.pdf_strategy} setting
     */
    @Deprecated(since = "3.0", forRemoval = true)
    public void setPdfOcr(boolean pdfOcr) {
        String strategy;
        if (pdfOcr) {
            strategy = "ocr_and_text";
        } else {
            strategy = "no_ocr";
        }
        LogManager.getLogger()
                .warn("pdf_ocr setting has been deprecated and is replaced by ocr.pdf_strategy: {}.", strategy);
        if (this.ocr == null) {
            this.ocr = new Ocr();
        }

        this.ocr.setPdfStrategy(strategy);
    }

    public Ocr getOcr() {
        return ocr;
    }

    public void setOcr(Ocr ocr) {
        this.ocr = ocr;
    }

    public ByteSizeValue getIgnoreAbove() {
        return ignoreAbove;
    }

    public void setIgnoreAbove(ByteSizeValue ignoreAbove) {
        this.ignoreAbove = ignoreAbove;
    }

    public boolean isFollowSymlinks() {
        return followSymlinks;
    }

    public void setFollowSymlinks(boolean followSymlinks) {
        this.followSymlinks = followSymlinks;
    }

    public String getTikaConfigPath() {
        return tikaConfigPath;
    }

    public void setTikaConfigPath(String tikaConfigPath) {
        this.tikaConfigPath = tikaConfigPath;
    }

    public String getTempDir() {
        return tempDir;
    }

    public void setTempDir(String tempDir) {
        this.tempDir = tempDir;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    @Nullable
    public Map<String, Object> getProviders() {
        return providers;
    }

    public void setProviders(@Nullable Map<String, Object> providers) {
        this.providers = providers;
    }

    /**
     * Return the configuration map for one filesystem provider type, or {@code null} when absent.
     *
     * @param type provider type key under {@code fs.providers}
     * @return mutable copy of that section, or {@code null}
     */
    @Nullable
    public Map<String, Object> getProviderConfig(String type) {
        if (providers == null || type == null) {
            return null;
        }
        Object section = providers.get(type);
        if (section == null) {
            return null;
        }
        if (!(section instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("fs.providers." + type + " must be a map, got "
                    + section.getClass().getName());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        return copyMap(typed);
    }

    @Nullable
    private static Map<String, Object> copyMap(@Nullable Map<String, Object> section) {
        if (section == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : section.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Fs fs = (Fs) o;
        return jsonSupport == fs.jsonSupport
                && filenameAsId == fs.filenameAsId
                && addFilesize == fs.addFilesize
                && removeDeleted == fs.removeDeleted
                && addAsInnerObject == fs.addAsInnerObject
                && storeSource == fs.storeSource
                && indexContent == fs.indexContent
                && attributesSupport == fs.attributesSupport
                && aclSupport == fs.aclSupport
                && rawMetadata == fs.rawMetadata
                && xmlSupport == fs.xmlSupport
                && indexFolders == fs.indexFolders
                && langDetect == fs.langDetect
                && continueOnError == fs.continueOnError
                && followSymlinks == fs.followSymlinks
                && Objects.equals(url, fs.url)
                && Objects.equals(updateRate, fs.updateRate)
                && Objects.equals(includes, fs.includes)
                && Objects.equals(excludes, fs.excludes)
                && Objects.equals(filters, fs.filters)
                && Objects.equals(indexedChars, fs.indexedChars)
                && Objects.equals(checksum, fs.checksum)
                && Objects.equals(hashAlgorithm, fs.hashAlgorithm)
                && Objects.equals(ocr, fs.ocr)
                && Objects.equals(ignoreAbove, fs.ignoreAbove)
                && Objects.equals(tikaConfigPath, fs.tikaConfigPath)
                && Objects.equals(tempDir, fs.tempDir)
                && Objects.equals(provider, fs.provider)
                && Objects.equals(providers, fs.providers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                url,
                updateRate,
                includes,
                excludes,
                filters,
                jsonSupport,
                filenameAsId,
                addFilesize,
                removeDeleted,
                addAsInnerObject,
                storeSource,
                indexContent,
                indexedChars,
                attributesSupport,
                aclSupport,
                rawMetadata,
                xmlSupport,
                checksum,
                hashAlgorithm,
                indexFolders,
                langDetect,
                continueOnError,
                ocr,
                ignoreAbove,
                followSymlinks,
                tikaConfigPath,
                tempDir,
                provider,
                providers);
    }

    @Override
    public String toString() {
        return "Fs{" + "url='" + url + '\'' + ", updateRate="
                + updateRate + ", includes="
                + includes + ", excludes="
                + excludes + ", filters="
                + filters + ", jsonSupport="
                + jsonSupport + ", filenameAsId="
                + filenameAsId + ", addFilesize="
                + addFilesize + ", removeDeleted="
                + removeDeleted + ", addAsInnerObject="
                + addAsInnerObject + ", storeSource="
                + storeSource + ", indexContent="
                + indexContent + ", indexedChars="
                + indexedChars + ", attributesSupport="
                + attributesSupport + ", aclSupport="
                + aclSupport + ", rawMetadata="
                + rawMetadata + ", xmlSupport="
                + xmlSupport + ", checksum='"
                + checksum + '\'' + ", hashAlgorithm='"
                + hashAlgorithm + '\'' + ", indexFolders="
                + indexFolders + ", langDetect="
                + langDetect + ", continueOnError="
                + continueOnError + ", ocr="
                + ocr + ", ignoreAbove="
                + ignoreAbove + ", followSymlinks="
                + followSymlinks + ", tikaConfigPath='"
                + tikaConfigPath + '\'' + ", tempDir='"
                + tempDir + '\'' + ", provider='"
                + provider + '\'' + ", providers="
                + providers + '}';
    }
}
