(local-fs-settings)=
# Local FS settings

```{contents}
:backlinks: entry
```

Here is a list of Local FS settings (under `fs.` prefix):

| Name                     | Environment Variable               | Default value   | Documentation                                           |
|--------------------------|------------------------------------|-----------------|---------------------------------------------------------|
| `fs.provider`            | `FSCRAWLER_FS_PROVIDER`            | `"local"`       | [Crawler Provider](#crawler-provider)                   |
| `fs.url`                 | `FSCRAWLER_FS_URL`                 | `"/tmp/es"`     | [Root directory](#root-directory)                       |
| `fs.update_rate`         | `FSCRAWLER_FS_UPDATE_RATE`         | `"15m"`         | [Update Rate](#update-rate)                             |
| `fs.includes`            | `FSCRAWLER_FS_INCLUDES`            | `null`          | [Includes and excludes](#includes-and-excludes)         |
| `fs.excludes`            | `FSCRAWLER_FS_EXCLUDES`            | `["*/~*", "*/.ds_store"]` | [Includes and excludes](#includes-and-excludes)         |
| `fs.filters`             | `FSCRAWLER_FS_FILTERS`             | `null`          | [Filter content](#filter-content)                       |
| `fs.json_support`        | `FSCRAWLER_FS_JSON_SUPPORT`        | `false`         | [Indexing JSON docs](#indexing-json-docs)               |
| `fs.xml_support`         | `FSCRAWLER_FS_XML_SUPPORT`         | `false`         | [Indexing XML docs](#indexing-xml-docs)                 |
| `fs.add_as_inner_object` | `FSCRAWLER_FS_ADD_AS_INNER_OBJECT` | `false`         | [Add as Inner Object](#add-as-inner-object)             |
| `fs.index_folders`       | `FSCRAWLER_FS_INDEX_FOLDERS`       | `true`          | [Index folders](#index-folders)                         |
| `fs.attributes_support`  | `FSCRAWLER_FS_ATTRIBUTES_SUPPORT`  | `false`         | [Adding file attributes](#adding-file-attributes)       |
| `fs.acl_support`         | `FSCRAWLER_FS_ACL_SUPPORT`         | `false`         | [Collecting ACL metadata](#collecting-acl-metadata)     |
| `fs.raw_metadata`        | `FSCRAWLER_FS_RAW_METADATA`        | `false`         | [Enabling raw metadata](#enabling-raw-metadata)         |
| `fs.filename_as_id`      | `FSCRAWLER_FS_FILENAME_AS_ID`      | `false`         | {ref}`filename-as-id`                                   |
| `fs.hash_algorithm`      | `FSCRAWLER_FS_HASH_ALGORITHM`      | `"MD5"`         | {ref}`document-ids`                                     |
| `fs.add_filesize`        | `FSCRAWLER_FS_ADD_FILESIZE`        | `true`          | [Disabling file size field](#disabling-file-size-field) |
| `fs.remove_deleted`      | `FSCRAWLER_FS_REMOVE_DELETED`      | `true`          | [Ignore deleted files](#ignore-deleted-files)           |
| `fs.store_source`        | `FSCRAWLER_FS_STORE_SOURCE`        | `false`         | {ref}`store_binary`                                     |
| `fs.index_content`       | `FSCRAWLER_FS_INDEX_CONTENT`       | `true`          | [Ignore content](#ignore-content)                       |
| `fs.lang_detect`         | `FSCRAWLER_FS_LANG_DETECT`         | `false`         | [Language detection](#language-detection)               |
| `fs.continue_on_error`   | `FSCRAWLER_FS_CONTINUE_ON_ERROR`   | `false`         | {ref}`continue_on_error`                                |
| `fs.indexed_chars`       | `FSCRAWLER_FS_INDEXED_CHARS`       | `100000.0`      | [Extracted characters](#extracted-characters)           |
| `fs.ignore_above`        | `FSCRAWLER_FS_IGNORE_ABOVE`        | `null`          | [Ignore above](#ignore-above)                           |
| `fs.checksum`            | `FSCRAWLER_FS_CHECKSUM`            | `null`          | [File Checksum](#file-checksum)                         |
| `fs.temp_dir`            | `FSCRAWLER_FS_TEMP_DIR`            | `null`          | [Temporary Directory](#temporary-directory)             |
| `fs.follow_symlinks`     | `FSCRAWLER_FS_FOLLOW_SYMLINKS`     | `false`         | [Follow Symlinks](#follow-symlinks)                     |
| `fs.tika_config_path`    | `FSCRAWLER_FS_TIKA_CONFIG_PATH`    | `null`          | [Tika Config Path](#tika-config-path)                   |
| `fs.ocr.enabled`         | `FSCRAWLER_FS_OCR_ENABLED`         | `true`          | {ref}`ocr_integration`                                  |


(crawler-provider)=
## Crawler Provider

```{versionadded} 3.0
```

The `fs.provider` setting specifies which crawler plugin to use for scanning files.
Available providers are:

* `local` (default): Crawl files from the local filesystem
* `ftp`: Crawl files from a remote FTP server (see {ref}`ftp-settings`)
* `ssh`: Crawl files from a remote server via SSH/SFTP (see {ref}`ssh-settings`)

```yaml
name: "test"
fs:
  provider: "local"
  url: "/path/to/data/dir"
```

````{note}

The `fs.provider` setting replaces the deprecated `server.protocol` setting.
SSH/FTP connection details belong under `fs.providers.ssh` / `fs.providers.ftp`
(see {ref}`ssh-settings` and {ref}`ftp-settings`). The top-level `server.*` block is
deprecated and will be removed in a future version.

Old configuration (deprecated):

```yaml
name: "test"
fs:
  url: "/path/to/data/dir"
server:
  hostname: "mynode.mydomain.com"
  protocol: "ftp"
```

New configuration (recommended):

```yaml
name: "test"
fs:
  provider: "ftp"
  url: "/path/to/data/dir"
  providers:
    ftp:
      hostname: "mynode.mydomain.com"
```
````

(root-directory)=
## Root directory

Define `fs.url` property in your `~/.fscrawler/test/_settings.yaml`
file:

```yaml
name: "test"
fs:
  url: "/path/to/data/dir"
```

For Windows users, use a form like `c:/tmp` or `c:\\tmp`.

(local-fs-update_rate)=
## Update rate

By default, `update_rate` is set to `15m`. You can modify this value using any compatible 
[time unit](https://www.elastic.co/guide/en/elasticsearch/reference/current/common-options.html#time-units).

For example, here is a 15 minutes update rate:

```yaml
name: "test"
fs:
  update_rate: "15m"
```

Or a 3 hours update rate:

```yaml
name: "test"
fs:
  update_rate: "3h"
```

`update_rate` is the pause duration between the last time we read the
file system and another run. Which means that if you set it to `15m`,
the next scan will happen on 15 minutes after the end of the current
scan, whatever its duration.

The supported units for duration are:

* `d` for days
* `h` for hours
* `m` for minutes
* `s` for seconds
* `ms` for milliseconds

```{note}

 If you don't want to wait for the next scan, you can manually edit the `~/.fscrawler/{job_name}/_checkpoint.json` file and
 set `next_check` to the current time or to `null`. FSCrawler will then start a new scan at most after 5 seconds.

 See {ref}`status-files` for more information.
```

(includes_excludes)=
## Includes and excludes

Use `fs.includes` to select files and `fs.excludes` to reject files or skip
directories. These settings are lists of patterns:

* If `includes` is omitted or empty, all files are eligible unless excluded.
* Otherwise, a file must match **at least one** include pattern.
* A match against **any** exclude pattern rejects the file, even if it also
  matches an include. The order of the patterns does not change this precedence.
* Directories remain traversable even when their names do not match an include
  pattern. An excluded directory is skipped, including its descendants.

For example, index Word and PDF documents, except files whose names start with
`resume`:

<!-- test:includes-excludes-opening:start -->

```yaml
name: 'test'
fs:
  url: '/data'
  includes:
    - '*\.doc'
    - '*\.pdf'
  excludes:
    - '*/resume*'
    - '*/~*'
    - '*/\.ds_store'
```

The backslash before each dot makes it a literal dot. Use single-quoted YAML
strings to preserve these backslashes; see the escaping examples below.

| File on disk | Path used for matching | Result |
|---|---|---|
| `/data/report.pdf` | `/report.pdf` | Included |
| `/data/reports/report.doc` | `/reports/report.doc` | Included |
| `/data/report.txt` | `/report.txt` | Does not match an include |
| `/data/resume_david.pdf` | `/resume_david.pdf` | Excluded, despite matching an include |
| `/data/reports/resume_david.pdf` | `/reports/resume_david.pdf` | Excluded |
| `/data/~draft.doc` | `/~draft.doc` | Excluded |

<!-- test:includes-excludes-opening:end -->

### Paths and wildcard behaviour

Patterns match the **entire virtual path**, relative to `fs.url`, with a leading
`/`. For `fs.url: '/data'`, use `/reports/report.pdf`, not
`/data/reports/report.pdf`. Windows backslashes in file paths are converted to
`/` for matching. Use `/` as the separator in patterns, including on Windows;
reserve backslashes in patterns for escaping special characters.

The current matcher retains its existing behaviour for compatibility. It
lowercases the path and pattern, replaces `*` with the regular expression `.*`
and `?` with `.?`, and then performs a full Java regular-expression match.
This is not a conventional shell glob implementation:

| Pattern feature | Current meaning | Example |
|---|---|---|
| `*` | Zero or more characters; can cross directory separators | `/images/*\.jpg` matches both `/images/photo.jpg` and `/images/archive/photo.jpg` |
| `?` | Zero or one character | `*/report?\.doc` matches `/report.doc` and `/report1.doc`, but not `/report12.doc` |
| Leading `/` | Starts at the crawl root | `/images/*` does not match `/archive/images/photo.jpg` |
| `*/` | Any path prefix ending in `/` | `*/old-*\.jpg` matches `/old-photo.jpg` and `/archive/old-photo.jpg` |
| Letter case | Both strings are lowercased using the JVM default locale | Usually `*\.pdf` also matches `/REPORT.PDF`; see the locale limitation below |

Java regular expressions normally do not let `.` match line terminators, so
the wildcard translations above do not cover every possible Unix filename.

```{warning}
Other Java regular-expression characters remain active. An unescaped dot in
`*.doc` matches any character, so that pattern also matches `/reportXdoc`.
An unescaped `+` changes repetition, and an unmatched `[` can cause a
`PatternSyntaxException` during matching. Use the escapes below when those
characters are intended literally.

Lowercasing depends on the JVM default locale. For example, under Turkish
locale, `/FILE.TXT` does not match `*/file.txt` because uppercase `I` lowercases
to dotless `ı`. These compatibility limitations are unchanged by this documentation.
```

### Escaping special characters in YAML

There are two stages: YAML first reads the string, then FSCrawler converts it
to a regular expression. To match a literal regex special character, prefix it
with a backslash in the string that reaches FSCrawler.

Use **single quotes** around patterns containing backslashes. YAML preserves
backslashes inside single-quoted strings:

```yaml
fs:
  includes:
    - '*\.doc'
    - '*/report\+final\.pdf'
    - '*/report\[draft\]\.pdf'
    - '*/report\(final\)\.pdf'
    - '*/price\$list\.pdf'
```

| Single-quoted YAML pattern | Matches | Does not match |
|---|---|---|
| `'*\.doc'` | `/report.doc` | `/reportXdoc` |
| `'*/report\+final\.pdf'` | `/report+final.pdf` | `/reportfinal.pdf` |
| `'*/report\[draft\]\.pdf'` | `/report[draft].pdf` | `/reportd.pdf` |
| `'*/report\(final\)\.pdf'` | `/report(final).pdf` | `/reportfinal.pdf` |
| `'*/price\$list\.pdf'` | `/price$list.pdf` | `/pricelist.pdf` |

The characters `.`, `+`, `(`, `)`, `[`, `]`, `{`, `}`, `^`, `$`, and `|` can have special meanings in regular expressions. When they are intended as literal filename characters outside regex character classes, escape them to avoid unexpected matches, missed matches, or invalid-pattern exceptions.

**What happens without escaping?**

The following patterns leave the character being discussed unescaped. Dots in extensions are escaped as `\.` in the other rows so that each example isolates the relevant behaviour.

| Character | Unescaped pattern | Current impact |
|---|---|---|
| `.` | `*.pdf` | The dot matches any character: `/report.pdf` and `/reportXpdf` both match. |
| `+` | `*/report+final\.pdf` | Repeats the preceding `t`: `/reportfinal.pdf` matches, but `/report+final.pdf` does not. |
| `(` and `)` | `*/report(final)\.pdf` | Groups the text: `/reportfinal.pdf` matches, but `/report(final).pdf` does not. |
| `[` and `]` | `*/report[12]\.pdf` | Selects one character: `/report1.pdf` and `/report2.pdf` match, but `/report[12].pdf` does not. |
| `{` and `}` | `*/report{2}\.pdf` | Repeats the preceding `t` twice: `/reportt.pdf` matches, but `/report{2}.pdf` does not. |
| `^` | `*/^report\.pdf` | Requires the start of the path after matching `/`; the intended `/^report.pdf` cannot match. |
| `$` | `*/budget$\.pdf` | Requires the end of the path before matching the extension; `/budget$.pdf` cannot match. |
| `\|` | `*/report(a\|b)\.pdf` | Means either `a` or `b` in the group: `/reporta.pdf` and `/reportb.pdf` match, but `/report(a\|b).pdf` does not. |

With `includes`, an unexpected match can make an unintended file eligible for indexing, while a missed match can omit the file you wanted. With `excludes`, an unexpected match can skip an unintended file, while a missed match can allow the file you meant to reject.

For example, `excludes: ['*.pdf']` also rejects `/reportXpdf`. Use `excludes: ['*\.pdf']` to require a literal dot. The same patterns in `includes` determine which files are eligible instead.

An unmatched opening bracket in `*/report[\.pdf`, or an unmatched opening parenthesis, can produce an invalid regular expression and throw `PatternSyntaxException` when matching runs. Whether that stops the crawl depends on the caller's error handling.

The effect depends on context: a standalone closing `]` or `}` may be treated literally. Escaping literal punctuation consistently avoids relying on these distinctions. This advice preserves the current matcher; it does not change its behaviour.

Spaces, underscores, and hyphens do not need escaping in these examples.
To include an apostrophe in a single-quoted YAML string, double the apostrophe:
`'*/author''s report\.pdf'`.

With **double quotes**, YAML processes backslash escapes, so double each
backslash. These two configurations pass the same pattern to FSCrawler:

```yaml
fs:
  includes:
    - '*\.doc'       # Single quotes: one backslash
```

```yaml
fs:
  includes:
    - "*\\.doc"      # Double quotes: two backslashes
```

Do not write `"*\.doc"`: `\.` is not a valid YAML double-quoted escape, so the
settings file fails to load. Always quote patterns beginning with `*`; an
unquoted `*.pdf` is interpreted as YAML alias syntax rather than a pattern.

```{warning}
Do not assume that `\*` or `\?` matches a literal asterisk or question mark.
FSCrawler replaces these wildcard characters even when preceded by a
backslash. Likewise, patterns are not passed through as arbitrary Java regex:
the wildcard replacement and lowercasing still occur. The examples above
escape literal punctuation while retaining FSCrawler's wildcard behaviour.
```

### Default exclusions

When `excludes` is omitted, its default value is
`['*/~*', '*/.ds_store']`. This skips names starting with `~` and macOS Finder
metadata named `.DS_Store`, subject to the matching rules above. The default dot
in `.ds_store` is unescaped and therefore also matches other characters in
that position.

The current YAML settings loader combines lists with lower-priority defaults
**by index**. A custom list with one entry replaces `*/~*` at index 0 but leaves
`*/.ds_store` at index 1. Two custom entries replace both default positions.
This is different from treating every custom list as a complete replacement.

To retain the intended default exclusions when adding your own, include them
explicitly; use an escaped dot to target the literal `.DS_Store` name:

```yaml
fs:
  excludes:
    - '*/~*'
    - '*/\.ds_store'
    - '*/private/*'
```

An explicit YAML `excludes: []` does **not** clear the inherited defaults in
the current loader. For example:

| YAML value for `excludes` | Effective patterns with the built-in defaults |
|---|---|
| Omitted or `[]` | `['*/~*', '*/.ds_store']` |
| `['*/private/*']` | `['*/private/*', '*/.ds_store']` |
| `['*/private/*', '*/archive/*']` | `['*/private/*', '*/archive/*']` |

An empty `includes: []` imposes no inclusion restriction when no higher-level
configuration supplies include patterns. These examples assume the built-in
defaults and the YAML file, without additional environment or system-property
overrides.

### Excluding directories

Directories are matched with a trailing `/`. A pattern must account for this
separator to exclude the directory itself. For example, with `fs.url: '/data'`:

| Exclude pattern | Effect |
|---|---|
| `/folderB/subfolder*` | Skips directories such as `/data/folderB/subfolderA` and their descendants |
| `/folderB/subfolderB/*` | Skips `/data/folderB/subfolderB` and its descendants |
| `*/private/*` | Skips directories named `private` at any depth |
| `*/\.ignore/*` | Skips directories named `.ignore` at any depth |

<!-- test:includes-excludes-directory-tree:start -->

For example, with `fs.url: '/data'` and the exclude pattern `/folderB/subfolderB/*`:

```text
/data/                       [scanned]
├── folderA/                 [scanned]
└── folderB/                 [scanned]
    ├── subfolderA/          [scanned]
    ├── subfolderB/          [skipped, including all descendants]
    └── subfolderC/          [scanned]
```

<!-- test:includes-excludes-directory-tree:end -->

Here, **scanned** means the crawler visits the directory. Files inside scanned directories must still satisfy the include and exclude rules.

The pattern `/folderB/subfolderB` alone does not match the directory path
`/folderB/subfolderB/`. Similarly, `.ignore` alone is not a directory exclusion
pattern for a virtual path such as `/.ignore/`.

An include such as `*\.pdf` does not prevent traversal into `/reports/` to
find PDFs. However, an exclusion such as `*/private/*` prevents scanning that
directory even when includes contains `*`.

If a folder contains a file named `.fscrawlerignore`, that folder and its
subfolders are skipped entirely.


## Filter content

You can filter out documents you would like to index by adding one or more
regular expression that match the extracted content.
Documents which are not matching will be simply ignored and not indexed.

If you define the following `fs.filters` property in your
`~/.fscrawler/test/_settings.yaml` file:

```yaml
name: "test"
fs:
  filters:
  - ".*foo.*"
  - "^4\\d{3}([\\ \\-]?)\\d{4}\\1\\d{4}\\1\\d{4}$"
```

With this example, only documents which contains the word `foo` and a VISA credit card number
with the form like `4012888888881881`, `4012 8888 8888 1881` or `4012-8888-8888-1881`
will be indexed.

## Indexing JSON docs

If you want to index JSON files directly without parsing with Tika, you
can set `json_support` to `true`. JSON contents will be stored
directly under \_source. If you need to keep JSON documents synchronized
to the index, set option [Add as Inner Object](#add-as-inner-object)
which stores additional metadata and the JSON contents under field
`object`.

```yaml
name: "test"
fs:
  json_support: true
```

Of course, if you did not define a mapping before launching the crawler,
Elasticsearch will auto guess the mapping.

## Indexing XML docs

If you want to index XML files and convert them to JSON, you can set
`xml_support` to `true`. The content of XML files will be added
directly under \_source. If you need to keep XML documents synchronized
to the index, set option [Add as Inner Object](#add-as-inner-object)
which stores additional metadata and the XML contents under field
`object`.

```yaml
name: "test"
fs:
  xml_support: true
```

Of course, if you did not define a mapping before launching the crawler,
Elasticsearch will auto guess the mapping.

## Add as Inner Object

The default settings store the contents of JSON and XML documents
directly onto the \_source element of Elasticsearch documents. Thereby,
there is no metadata about file and path settings, which are necessary
to determine if a document is deleted or updated. New files will however
be added to the index, (determined by the file timestamp).

If you need to keep JSON or XML documents synchronized to elasticsearch,
you should set this option.

```yaml
name: "test"
fs:
  add_as_inner_object: true
```

## Index folders

By default, FSCrawler will index folder names in the folder index. If
you don’t want to index those folders, you can set `index_folders` to
`false`.

Note that in that case, FSCrawler won’t be able to detect removed
folders so any document has been indexed in Elasticsearch, it won’t be
removed when you remove or move the folder away.

See `elasticsearch.index_folder` below for the name of the index to be used to store the folder data (if `es.index_folders` is set to `true`).

```yaml
name: "test"
fs:
  index_folders: false
```

## Dealing with multiple types and multiple dirs

If you have more than one type, create as many crawlers as types and/or folders:

`~/.fscrawler/test_type1/_settings.yaml`:

```yaml
name: "test_type1"
fs:
  url: "/tmp/type1"
  json_support: true
elasticsearch:
  index: "mydocs1"
  index_folder: "myfolders1"
```

`~/.fscrawler/test_type2/_settings.yaml`:

```yaml
name: "test_type2"
fs:
  url: "/tmp/type2"
  json_support: true
elasticsearch:
  index: "mydocs2"
  index_folder: "myfolders2"
```

`~/.fscrawler/test_type3/_settings.yaml`:

```yaml
name: "test_type3"
fs:
  url: "/tmp/type3"
  xml_support: true
elasticsearch:
  index: "mydocs3"
  index_folder: "myfolders3"
```

## Dealing with multiple types within the same dir

You can also index many types from one single dir using two crawlers
scanning the same dir and by setting `includes` parameter:

`~/.fscrawler/test_type1.yaml`:

```yaml
name: "test_type1"
fs:
  url: "/tmp"
  includes:
  - "type1*.json"
  json_support: true
elasticsearch:
  index: "mydocs1"
  index_folder: "myfolders1"
```

`~/.fscrawler/test_type2.yaml`:

```yaml
name: "test_type2"
fs:
  url: "/tmp"
  includes:
  - "type2*.json"
  json_support: true
elasticsearch:
  index: "mydocs2"
  index_folder: "myfolders2"
```

`~/.fscrawler/test_type3.yaml`:

```yaml
name: "test_type3"
fs:
  url: "/tmp"
  includes:
  - "*.xml"
  xml_support: true
elasticsearch:
  index: "mydocs3"
  index_folder: "myfolders3"
```

(filename-as-id)=
## Using filename as elasticsearch `_id`

Please note that by default the document `_id` is generated as a hash of the file path
(see {ref}`document-ids`). You can force the `_id` to be the filename using the
`filename_as_id` attribute:

```yaml
name: "test"
fs:
  filename_as_id: true
```

When `filename_as_id` is `true`, `fs.hash_algorithm` is ignored.

## Adding file attributes

If you want to add file attributes such as `attributes.owner`, `attributes.group`
and `attributes.permissions`, you can set `attributes_support` to `true`.

```yaml
name: "test"
fs:
  attributes_support: true
```

```{note}

 On Windows systems, `attributes.group` and `attributes.permissions` are
 not generated.
```

## Collecting ACL metadata

To extract NTFS access control entries (principal, type, permissions and flags),
enable both `attributes_support` and `acl_support`:

```yaml
name: "test"
fs:
  attributes_support: true
  acl_support: true
```

When `acl_support` is disabled, FSCrawler skips resolving ACLs even if
`attributes_support` is active.

(enabling-raw-metadata)=
## Enabling raw metadata

FSCrawler can extract all found metadata within a `meta.raw` object in addition
to the standard metadata fields.
If you want to enable this feature, you can set `raw_metadata` to `true`.

```yaml
name: "test"
fs:
  raw_metadata: true
```

```{versionchanged} 3.0
The names of the keys stored in `meta.raw` changed compared to FSCrawler 2.9 because of
the Apache Tika 4 upgrade. For example, `resourceName` is now `tk:resource-name`, Tika
internal keys use the `tk:` prefix instead of `X-TIKA:`, ICC keys use `icc:` instead of
`ICC:`, and many PDF keys now use hyphens (`pdf:pdf-version` instead of `pdf:PDFVersion`).
See the {ref}`3.0 release notes <release-notes-3.0>` for a summary of the renamed keys.
```

Generated raw metadata depends on the file format itself.

For example, a PDF document could generate:

```json
{
   "access-permission:assemble-document" : "true",
   "access-permission:can-modify" : "true",
   "access-permission:can-print" : "true",
   "access-permission:can-print-faithful" : "true",
   "access-permission:extract-content" : "true",
   "access-permission:extract-for-accessibility" : "true",
   "access-permission:fill-in-form" : "true",
   "access-permission:modify-annotations" : "true",
   "Content-Length" : "101643",
   "Content-Type" : "application/pdf",
   "dc:creator" : "David Pilato",
   "dc:format" : "application/pdf; version=1.5",
   "dc:language" : "en-US",
   "dc:subject" : "keyword1, keyword2",
   "dc:title" : "Test Tika title",
   "dcterms:created" : "2016-07-07T08:37:42Z",
   "dcterms:modified" : "2016-07-07T08:37:42Z",
   "pdf:chars-per-page" : "42",
   "pdf:contains-damaged-font" : "false",
   "pdf:contains-non-embedded-font" : "false",
   "pdf:docinfo:created" : "2016-07-07T08:37:42Z",
   "pdf:docinfo:creator" : "David Pilato",
   "pdf:docinfo:creator-tool" : "Microsoft Word",
   "pdf:docinfo:keywords" : "keyword1, keyword2",
   "pdf:docinfo:modified" : "2016-07-07T08:37:42Z",
   "pdf:docinfo:subject" : "Test Tika Object",
   "pdf:docinfo:title" : "Test Tika title",
   "pdf:encrypted" : "false",
   "pdf:eof-offsets" : "101460",
   "pdf:has-collection" : "false",
   "pdf:has-marked-content" : "true",
   "pdf:has-xfa" : "false",
   "pdf:has-xmp" : "false",
   "pdf:incremental-update-count" : "1",
   "pdf:num-3d-annotations" : "0",
   "pdf:ocr-page-count" : "0",
   "pdf:overall-percentage-unmapped-unicode-chars" : "0.0",
   "pdf:pdf-version" : "1.5",
   "pdf:total-unmapped-unicode-chars" : "0",
   "pdf:unmapped-unicode-chars-per-page" : "0",
   "tk:content-type-magic-detected" : "application/pdf",
   "tk:parsed-by" : "org.apache.tika.parser.pdf.PDFParser",
   "tk:parsed-by-full-set" : "org.apache.tika.parser.pdf.PDFParser",
   "tk:resource-name" : "test.pdf",
   "tk:version-count" : "1",
   "xmp:CreatorTool" : "Microsoft Word",
   "xmpTPg:NPages" : "2"
}
```

Where a MP3 file would generate:

```json
{
   "audio:bitrate" : "192000",
   "audio:channels" : "2",
   "audio:is-variable-bitrate" : "false",
   "audio:raw-track-number" : "1",
   "Content-Type" : "audio/mpeg",
   "dc:creator" : "David Pilato",
   "dc:title" : "Test Tika",
   "mp3:version" : "MPEG 3 Layer III Version 1",
   "tk:content-type-magic-detected" : "audio/mpeg",
   "tk:parsed-by" : "org.apache.tika.parser.DefaultParser",
   "tk:parsed-by-full-set" : "org.apache.tika.parser.DefaultParser",
   "tk:resource-name" : "test.mp3",
   "xmpDM:album" : "FS Crawler",
   "xmpDM:artist" : "David Pilato",
   "xmpDM:audioChannelType" : "Stereo",
   "xmpDM:audioCompressor" : "MP3",
   "xmpDM:audioSampleRate" : "44100",
   "xmpDM:duration" : "1.0187751054763794",
   "xmpDM:genre" : "Vocal",
   "xmpDM:logComment" : "Hello but reverted",
   "xmpDM:releaseDate" : "2016",
   "xmpDM:trackNumber" : "1"
}
```

````{note}
All fields are generated as text even though they can be valid booleans or numbers.

The `meta.raw.*` fields have a default mapping applied:

```json
{
  "type": "text",
  "fields": {
    "keyword": {
      "type": "keyword",
      "ignore_above": 256
    }
  }
}
```

If you want specifically tell elasticsearch to use a date type or a
numeric type for some fields, you need to modify the default template
provided by FSCrawler.
````

```{note}
Note that dots in metadata names will be replaced by a `:`. For example `PTEX.Fullbanner` will be indexed as
`PTEX:Fullbanner`.
```

```{note}
Note that if you have a lot of different type of files, that can generate a lot of raw metadata which can make you hit 
the total number of field limit in elasticsearch mappings. In which case you will need to change the index settings 
`foo`.

See [elasticsearch documentation](https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping.html#mapping-limit-settings)
```

## Disabling file size field

By default, FSCrawler will create a field to store the original file
size in octets. You can disable it using \`add_filesize’ option:

```yaml
name: "test"
fs:
  add_filesize: false
```

## Ignore deleted files

If you don’t want to remove indexed documents when you remove a file or a directory, you can set `remove_deleted` to 
`false` (default to `true`):

```yaml
name: "test"
fs:
  remove_deleted: false
```

```{note}
Setting `remove_deleted` is forced to `false` when using the Workplace Search output.
```

When recursively removing a deleted directory, FSCrawler queries Elasticsearch
for up to 10,000 direct files and 10,000 direct subfolders. If either query
returns 10,000 results, FSCrawler logs a warning and retains the affected
folder records so deletion can continue during subsequent crawls.

## Ignore content

If you don’t want to extract file content but only index filesystem
metadata such as filename, date, size and path, you can set
`index_content` to `false` (default to `true`):

```yaml
name: "test"
fs:
  index_content: false
```

(continue_on_error)=
## Continue on Error

By default, FSCrawler will immediately stop indexing if he hits a
Permission denied exception. If you want to just skip this File and
continue with the rest of the directory tree you can set
`continue_on_error` to `true` (default to `false`):

```yaml
name: "test"
fs:
  continue_on_error: true
```

If a directory cannot be listed completely (for example, a Windows share is unavailable
or access is denied), FSCrawler preserves its indexed documents and keeps the directory
pending. With `continue_on_error: true`, other accessible directories are processed,
but the scan is reported as failed and its successful-scan timestamp is not advanced.
Failed listings are attempted once per scan and retried on the next scheduled run or
after restarting the job. With `continue_on_error: false`, the scan stops at the first
listing failure. Successfully listed empty directories still reconcile deletions normally.

## Language detection

You can ask for language detection using `lang_detect` option:

```yaml
name: "test"
fs:
  lang_detect: true
```

In that case, a new field named `meta.language` is added to the
generated JSON document.

If you are using elasticsearch 5.0 or superior, you can use this value
to send your document to a specific index using a {ref}`Node Ingest pipeline <ingest_node>`.

For example, you can define a pipeline named `langdetect` with:

```sh
PUT _ingest/pipeline/langdetect
{
  "description" : "langdetect pipeline",
  "processors" : [
    {
      "set": {
        "field": "_index",
        "value": "myindex-{{meta.language}}"
      }
    }
  ]
}
```

In FSCrawler settings, set both `fs.lang_detect` and
`elasticsearch.pipeline` options:

```yaml
name: "test"
fs:
  lang_detect: true
elasticsearch:
  pipeline: "langdetect"
```

And then, a document containing french text will be sent to
`myindex-fr`. A document containing english text will be sent to
`myindex-en`.

You can also imagine changing the field name from `content` to
`content-fr` or `content-en`. That will help you to define the
correct analyzer to use.

Language detection might detect more than one language in a given text
but only the most accurate will be set. Which means that if you have a
document containing 80% of french and 20% of english, the document will
be marked as `fr`.

Note that language detection is CPU and time consuming.

(store_binary)=
## Storing binary source document

You can store in elasticsearch itself the binary document (BASE64 encoded)
using `store_source` option:

```yaml
name: "test"
fs:
  store_source: true
```

In that case, a new field named `attachment` is added to the generated
JSON document. This field is not indexed. Default mapping for
`attachment` field is:

```json
{
  "_doc" : {
    "properties" : {
      "attachment" : {
        "type" : "binary",
        "doc_values" : false
      }
      // ... Other properties here
    }
  }
}
```

## Extracted characters

By default, FSCrawler will extract only the first 100 000 characters.
But, you can set `indexed_chars` to `5000` in FSCrawler settings in
order to overwrite this default settings.

```yaml
name: "test"
fs:
  indexed_chars: "5000"
```

This number can be either a fixed size, number of characters that is, or
a percent using `%` sign. The percentage value will be applied to the
filesize to determine the number of character the crawler needs to
extract.

If you want to index only `80%` of filesize, define `indexed_chars`
to `"80%"`. Of course, if you want to index the full document, you can
set this property to `"100%"`. Double values are also supported so
`"0.01%"` is also a correct value.

**Compressed files**: If your file is compressed, you might need to
increase `indexed_chars` to more than `"100%"`. For example,
`"150%"`.

If you want to extract the full content, define `indexed_chars` to
`"-1"`.

```{note}

 Tika requires to allocate in memory a data structure to
 extract text. Setting `indexed_chars` to a high number will require
 more memory!
```

## Ignore Above

By default, and if `index_content` set to `true`, FSCrawler will send every single file to Tika, whatever its size.
But some files on your file system might be a way too big to be parsed.

Set `ignore_above` to the desired value of the limit.

```yaml
name: "test"
fs:
  ignore_above: "512mb"
```

(file-checksum)=
## File checksum

If you want FSCrawler to generate a checksum for each file, set
`checksum` to the algorithm you wish to use to compute the checksum,
such as `MD5` or `SHA-1`.

```{note}

 You MUST set `index_content` to true to allow this feature to work. Nevertheless you MAY set `indexed_chars` to 0 if you do not need any content in the index.

 You MUST NOT set `json_support` or `xml_support` to allow this feature to work also.
```

```yaml
name: "test"
fs:
   # required
  index_content: true
  #indexed_chars: 0
  checksum: "MD5"
```

## Temporary Directory

```{versionadded} 3.0
```

When `checksum` or `store_source` is enabled, FSCrawler may need to create
temporary files to process large documents without loading them entirely into memory.
By default, temporary files are created in `~/.fscrawler/<job_name>/tmp/`.

You can override this location using the `temp_dir` option:

```yaml
name: "test"
fs:
  checksum: "MD5"
  temp_dir: "/path/to/custom/temp"
```

```{note}

 For small files (64KB or less), FSCrawler uses an in-memory buffer instead of
 temporary files for better performance. Temporary files are only created for
 larger files to avoid `OutOfMemoryError`.
```

```{note}

 Temporary files are automatically deleted after processing each document.
```

## Follow Symlinks

If you want FSCrawler to follow the symbolic links, you need to be explicit about it and set
`follow_symlinks` to `true`. Starting from version 2.7, symbolic links are not followed anymore.

```yaml
name: "test"
fs:
  follow_symlinks: true
```

## Tika Config Path

```{versionadded} 3.0
```

If you want to override the default Tika parser configuration, you can set the path to a custom Tika
configuration file, which will be used instead.

```{note}
Apache Tika 4 replaced the XML-based configuration file mechanism with a JSON one. If you were using an
XML Tika configuration with a previous version of FSCrawler, you need to convert it to the equivalent
JSON configuration. If the configuration file cannot be loaded, FSCrawler fails fast with a
configuration error instead of silently ignoring the setting.
```

```yaml
name: "test"
fs:
  tika_config_path: '/path/to/tikaConfig.json'
```

An example Tika JSON config file is shown below. It excludes the HTML parser (`jsoup-parser`) from the
default parser (HTML files are then handled as plain text), routes XHTML files to the XML parser, and
keeps the Vision Language Model (VLM) OCR parsers disabled (see {ref}`vlm-ocr`):

```json
{
  "parsers": [
    { "default-parser": {
        "exclude": [
          "jsoup-parser",
          "openai-vlm-parser",
          "openai-vlm-deterministic-parser",
          "claude-vlm-parser",
          "gemini-vlm-parser"
        ]
      } },
    { "xml-parser": { "_mime-include": ["application/xhtml+xml"] } }
  ]
}
```

```{note}
Since FSCrawler 3.0, the distribution includes Apache Tika's `tika-vlm` module. If your JSON
configuration uses `default-parser`, add the VLM parser component names to the `exclude` list unless
you explicitly configure one of them in the `parsers` array. Without these exclusions, Tika may load
the VLM parsers through SPI and run a startup health check against their configured endpoint.
```

The `parsers` array lists the parsers to load, in order. Each entry is either a Tika component name (like
`pdf-parser` or `xml-parser`) with its configuration object, or the special
`default-parser` marker which enables SPI loading of all remaining parsers (with optional `exclude`
list). Parsers support mime filtering via the `_mime-include` and `_mime-exclude` decorations.
