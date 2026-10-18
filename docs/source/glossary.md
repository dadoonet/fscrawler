# Glossary

Technical terms used in FSCrawler's documentation. Definitions describe their
meaning in FSCrawler; linked guides explain the configuration in more detail.

The usual processing flow is: a **job** runs a **scan**, a **provider** reads each
file, a **parser** extracts text and metadata, and FSCrawler sends the resulting
**documents** to an Elasticsearch **index**. OCR can recognise text in images;
an ingest pipeline can transform documents before indexing.

```{glossary}
:sorted:

Access control list (ACL)
    A list of permissions associated with a file or directory. FSCrawler can
    collect ACL metadata; collecting permissions does not itself enforce those
    permissions on search results. See [Local FS settings](admin/fs/local-fs.md).

Alias
    An Elasticsearch name that points to one or more indices. Applications can
    search through an alias without using the underlying index names directly.
    See [Elasticsearch settings](admin/fs/elasticsearch.md).

Apache Tika
    The library FSCrawler uses to identify file formats and extract text and
    metadata. It delegates work to parsers for different formats.
    See [Supported formats](user/formats.md).

API key
    A credential used to authenticate requests to a service. Elasticsearch and
    Kibana can use API keys; their required permissions depend on the operations
    FSCrawler performs. See [Elasticsearch settings](admin/fs/elasticsearch.md)
    and [Kibana settings](admin/fs/kibana.md).

Binary source
    The original bytes of a file, such as a PDF or Word document. Storing the
    binary source is separate from indexing its extracted text. It is also
    different from Elasticsearch's `_source` JSON field.
    See [Local FS settings](admin/fs/local-fs.md).

Bulk request
    An Elasticsearch request containing several operations, such as indexing or
    deleting documents. Batching operations reduces request overhead.
    See [Bulk settings](admin/fs/elasticsearch.md#bulk-settings).

Checkpoint
    Saved job state in `_checkpoint.json`, including scanning progress and
    scheduling information. FSCrawler uses it to coordinate later scans and
    recovery. See [Checkpoint file](admin/status.md).

Checksum
    A value calculated from file content using a hash algorithm. FSCrawler's
    `fs.checksum` setting can populate `file.checksum`. This is separate from
    hashing file paths to generate document IDs.
    See [Local FS settings](admin/fs/local-fs.md) and [Document IDs](admin/fs/document-ids.md).

Command-line interface (CLI)
    The commands and options used to start and control FSCrawler from a terminal.
    See [CLI options](admin/cli-options.md).

Crawler
    Software that scans a location for files and processes them. FSCrawler can
    repeat scans to detect added, changed and deleted files.
    See [Getting Started](user/getting_started.md).

Cross-origin resource sharing (CORS)
    Browser rules that determine whether a web page may access an HTTP service
    at another origin. CORS configuration is separate from authentication.
    See [REST service](admin/fs/rest.md).

Document
    Either an original file or the Elasticsearch record produced from it,
    depending on context. A PDF file is an input document; the indexed document
    is a JSON record containing extracted content and metadata. These are
    different representations, and indexing does not necessarily store the
    original file. See [Local FS settings](admin/fs/local-fs.md).

Document ID
    The `_id` identifying a document within an Elasticsearch index. FSCrawler
    normally derives IDs from file paths; a content checksum is a different
    value. See [Document IDs](admin/fs/document-ids.md).

Elasticsearch
    The service that stores FSCrawler's indexed documents and answers search
    requests. See [Elasticsearch settings](admin/fs/elasticsearch.md).

EDOT
    Elastic Distributions of OpenTelemetry: Elastic's distributions of
    OpenTelemetry components, including a collector and language instrumentation
    such as the Java agent used by FSCrawler.
    See [OTel Tracing / EDOT Collector](admin/otel.md) and
    [Elastic's EDOT documentation](https://www.elastic.co/docs/reference/opentelemetry/).

EDOT Collector
    Elastic's distribution of the OpenTelemetry Collector. It receives telemetry
    from applications, processes it and forwards it to a configured destination.
    In FSCrawler's Docker example, it receives tracing data from FSCrawler and
    forwards it to Elasticsearch for exploration in Kibana. It handles monitoring
    data about the crawler; file content is indexed through FSCrawler's separate
    Elasticsearch client.
    See [Docker example with EDOT Collector](admin/otel.md#docker-example-with-edot-collector).

Extraction
    Reading a file to obtain its text and metadata. Extracted text may come from
    an existing text layer or from OCR of images.
    See [Supported formats](user/formats.md) and [OCR integration](user/ocr.md).

Hash
    A value produced by applying an algorithm to input data. The input matters:
    hashing a path identifies a location, while hashing file bytes produces a
    content checksum. Neither operation encrypts the input.
    See [Document IDs](admin/fs/document-ids.md).

Image-only PDF
    A PDF whose pages contain images without an existing text layer. Recognising
    words in those images generally requires OCR.
    See [OCR integration](user/ocr.md).

Incremental crawling
    Using later scans and saved state to process file additions, modifications
    and deletions instead of rebuilding the entire collection each time.
    See [Crawler options](user/options.md) and [Checkpoint file](admin/status.md).

Index
    An Elasticsearch collection of documents with field mappings and settings.
    As a verb, to index means to add or update a document so it can be searched.
    See [Elasticsearch settings](admin/fs/elasticsearch.md).

Ingest pipeline
    A sequence of Elasticsearch processors that can transform or enrich a
    document before it is indexed. This processing takes place in Elasticsearch.
    See [Elasticsearch settings](admin/fs/elasticsearch.md).

Job
    A named FSCrawler configuration defining where to find files and how to
    process and index them. Job settings are commonly stored in `_settings.yaml`.
    See [Job file specifications](admin/fs/index.md).

JVM
    The Java Virtual Machine that runs FSCrawler. Its options control behaviour
    such as available heap memory. See [JVM settings](admin/jvm-settings.md).

Kibana
    A user interface for exploring Elasticsearch data and building searches,
    visualisations and dashboards. See [Kibana settings](admin/fs/kibana.md).

Language detection
    Identifying the language of extracted text. This differs from selecting an
    OCR language, which tells the recognition engine how to interpret images.
    See [Local FS settings](admin/fs/local-fs.md) and [OCR integration](user/ocr.md).

Language pack
    Recognition data installed for an OCR language, such as English (`eng`) or
    Hebrew (`heb`) in Tesseract. Selecting a language requires its data to be
    available. See [OCR integration](user/ocr.md).

Mapping
    Elasticsearch's definition of document fields and their types. Mappings
    affect how values are indexed and searched.
    See [Elasticsearch settings](admin/fs/elasticsearch.md).

Metadata
    Information about a file, such as its title, author, dates or media type.
    Raw metadata exposes additional properties returned by the parser, beyond
    FSCrawler's standard metadata fields. See [Local FS settings](admin/fs/local-fs.md).

MIME type
    A media type identifying a file's format, such as `application/pdf` or
    `text/plain`. It is more specific than describing a file as binary or text.
    See [Supported formats](user/formats.md).

OCR
    Optical character recognition: recognising text in images. This differs
    from extracting characters already stored as text in a document.
    See [OCR integration](user/ocr.md).

OCR strategy
    A policy controlling when OCR is applied. For PDFs, strategies can use the
    existing text, perform OCR, or decide based on the page's text content.
    See [OCR integration](user/ocr.md).

OTel (OpenTelemetry)
    An open-source framework of APIs, SDKs and tools for generating, collecting
    and exporting telemetry such as traces, metrics and logs. FSCrawler uses
    OpenTelemetry tracing to help diagnose processing times and failures.
    See [OTel Tracing / EDOT Collector](admin/otel.md).

OTLP
    OpenTelemetry Protocol: the protocol used to send telemetry between an
    instrumented application, a collector and a compatible backend. FSCrawler's
    `OTEL_EXPORTER_OTLP_ENDPOINT` setting specifies where its exporter sends data.
    See [Configuring the OTel exporter](admin/otel.md#configuring-the-otel-exporter).

Page segmentation mode
    A Tesseract setting describing the expected arrangement of text in an image,
    such as a page, a block or a single line. It can affect recognition results.
    See [OCR integration](user/ocr.md).

Parser
    A component that understands a particular format and reads its structure to
    extract content or metadata. Apache Tika selects suitable parsers for files.
    See [Supported formats](user/formats.md).

Plugin
    An extension supplying a capability to FSCrawler. A provider is the selected
    implementation of a capability, such as reading files or supplying passwords.
    See [Local FS settings](admin/fs/local-fs.md) and [Password providers](admin/fs/passwords.md).

Provider
    An implementation selected for a particular task. A filesystem provider reads
    files from a local or remote location; a password provider supplies candidate
    passwords for protected documents. See [Local FS settings](admin/fs/local-fs.md)
    and [Password providers](admin/fs/passwords.md).

Regular expression (regex)
    A pattern language for matching text. FSCrawler content filters use regular
    expressions; path includes and excludes use wildcard patterns instead.
    See [Local FS settings](admin/fs/local-fs.md).

Reindexing
    Processing documents again to rebuild or change their indexed representation.
    Changes to extraction settings may require reprocessing files; changing ID
    generation needs particular care to avoid duplicate records.
    See [Document IDs](admin/fs/document-ids.md).

REST API
    An HTTP interface for applications to send requests to a service. FSCrawler's
    REST service supports document uploads and crawler control.
    See [REST service](admin/fs/rest.md).

Scan
    One pass through a job's configured file location. A loop controls repeated
    scans; the update rate controls the pause between scans.
    See [Crawler options](user/options.md).

Semantic search
    Searching by meaning rather than only matching words. It uses model-generated
    representations of text and requires suitable Elasticsearch configuration.
    See [Elasticsearch settings](admin/fs/elasticsearch.md).

Source field
    Elasticsearch's `_source` field contains the JSON representation of an
    indexed document. It is different from the original binary file and from
    FSCrawler's option to store that file's bytes.
    See [Local FS settings](admin/fs/local-fs.md).

Symbolic link (symlink)
    A filesystem entry pointing to another file or directory. FSCrawler has a
    setting controlling whether to follow symbolic links.
    See [Local FS settings](admin/fs/local-fs.md).

Tesseract
    An OCR engine used through Apache Tika to recognise text in images. Its
    executable and language data must be available to the parsing environment.
    See [OCR integration](user/ocr.md).

Text layer
    Characters stored as text within a document, even when the page also contains
    images. A scanned PDF may gain a text layer through OCR; its presence does
    not guarantee complete or accurate text. See [OCR integration](user/ocr.md).

TLS
    Transport Layer Security, used by HTTPS to protect network communication.
    Older configuration names may refer to SSL. Certificate verification checks
    the identity of the service being contacted.
    See [Elasticsearch settings](admin/fs/elasticsearch.md).

Update rate
    The pause after a scan finishes before the next scan starts. It is not a
    guarantee that every file is checked at that exact interval.
    See [Local FS settings](admin/fs/local-fs.md#update-rate).

Vision Language Model (VLM)
    A model that processes images and language. FSCrawler's Tika integration can
    use a VLM for OCR as an alternative to conventional recognition engines.
    See [OCR integration](user/ocr.md).

Wildcard pattern
    A path-matching pattern using symbols such as `*`. FSCrawler uses these
    patterns for includes and excludes; they are not regular expressions.
    See [Local FS settings](admin/fs/local-fs.md).

YAML
    A text format used for FSCrawler configuration. Indentation expresses the
    structure of settings, so spacing matters.
    See [Job file specifications](admin/fs/index.md).
```
