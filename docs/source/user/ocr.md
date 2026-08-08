(ocr_integration)=
# OCR integration

```{versionadded} 2.3
```

To deal with images containing text, just [install Tesseract](https://tesseract-ocr.github.io/tessdoc/).
Tesseract will be auto-detected by Tika or you can explicitly [set the path to tesseract binary](#ocr-path).
Then add an image (png, jpg, …) into your FSCrawler {ref}`root-directory`. After the next index update, the text will be
indexed and placed in `_source.content`.

## OCR settings

Here is a list of OCR settings (under `fs.ocr` prefix):

| Name                                | Environment Variable                          | Default value   | Documentation                                                     |
|-------------------------------------|-----------------------------------------------|-----------------|-------------------------------------------------------------------|
| `fs.ocr.enabled`                    | `FSCRAWLER_FS_OCR_ENABLED`                    | `true`          | [Disable/Enable OCR](#disableenable-ocr)                          |
| `fs.ocr.language`                   | `FSCRAWLER_FS_OCR_LANGUAGE`                   | `"eng"`         | [OCR Language](#ocr-language)                                     |
| `fs.ocr.path`                       | `FSCRAWLER_FS_OCR_PATH`                       | `null`          | [OCR Path](#ocr-path)                                             |
| `fs.ocr.data_path`                  | `FSCRAWLER_FS_OCR_DATA_PATH`                  | `null`          | [OCR Data Path](#ocr-data-path)                                   |
| `fs.ocr.output_type`                | `FSCRAWLER_FS_OCR_OUTPUT_TYPE`                | `txt`           | [OCR Output Type](#ocr-output-type)                               |
| `fs.ocr.pdf_strategy`               | `FSCRAWLER_FS_OCR_PDF_STRATEGY`               | `auto`          | [OCR PDF Strategy](#ocr-pdf-strategy)                             |
| `fs.ocr.page_seg_mode`              | `FSCRAWLER_FS_OCR_PAGE_SEG_MODE`              | `01`            | [OCR Page Seg Mode](#ocr-page-seg-mode)                           |
| `fs.ocr.preserve_interword_spacing` | `FSCRAWLER_FS_OCR_PRESERVE_INTERWORD_SPACING` | `false`         | [OCR Preserve Interword Spacing](#ocr-preserve-interword-spacing) |

## Disable/Enable OCR

```{versionadded} 2.7
```

You can completely disable using OCR by setting `fs.ocr.enabled` property in your
`~/.fscrawler/test/_settings.yaml` file:

```yaml
name: "test"
fs:
  url: "/path/to/data/dir"
  ocr:
    enabled: false
```

By default, OCR is activated if tesseract can be found on your system.

## OCR Language

If you are using the default Docker image (see {ref}`docker`) or if you have installed any of the
[Tesseract Languages](https://tesseract-ocr.github.io/tessdoc/Data-Files.html),
you can use them when parsing your documents by setting `fs.ocr.language` property in your
`~/.fscrawler/test/_settings.yaml` file:

```yaml
name: "test"
fs:
  url: "/path/to/data/dir"
  ocr:
    language: "eng"
```

````{note}
You can define multiple languages by using `+` sign as a separator:

```yaml
name: "test"
fs:
  url: "/path/to/data/dir"
  ocr:
    language: "eng+fas+fra"
```
````

## OCR Path

If your Tesseract application is not available in default system PATH,
you can define the path to use by setting `fs.ocr.path` property in
your `~/.fscrawler/test/_settings.yaml` file:

```yaml
name: "test"
fs:
  url: "/path/to/data/dir"
  ocr:
    path: "/path/to/tesseract/bin/"
```

You can point `fs.ocr.path` either at the `tesseract` executable itself or
at the directory that contains it: FSCrawler accepts both forms.

When you set it, it’s highly recommended to set the [OCR Data Path](#ocr-data-path).

## OCR Data Path

Set the path to the ‘tessdata’ folder, which contains language files and
config files if Tesseract can not be automatically detected. You can
define the path to use by setting `fs.ocr.data_path` property in your
`~/.fscrawler/test/_settings.yaml` file:

```yaml
name: "test"
fs:
  url: "/path/to/data/dir"
  ocr:
    path: "/path/to/tesseract/bin/"
    data_path: "/path/to/tesseract/share/tessdata/"
```

## OCR Output Type

```{versionadded} 2.5
```

Set the output type from ocr process. `fs.ocr.output_type` property can be defined to
`txt` or `hocr` in your `~/.fscrawler/test/_settings.yaml` file:

```yaml
name: "test"
fs:
  url: "/path/to/data/dir"
  ocr:
    output_type: "hocr"
```

```{note}
When omitted, `txt` value is used.
```

## OCR PDF Strategy

By default, FSCrawler will also try to extract also images from your PDF
documents and run OCR on them. This can be a CPU intensive operation. If
you don’t mean to run OCR on PDF but only on images, you can set
`fs.ocr.pdf_strategy` to `"no_ocr"` or  to `"auto"`:

```yaml
name: "test"
fs:
  ocr:
    pdf_strategy: "auto"
```

Supported strategies are:

* `auto`: No OCR is performed on PDF documents if there is more than 10 characters extracted. See [PDFParser OCR Options](https://cwiki.apache.org/confluence/pages/viewpage.action?pageId=109454066).

* `no_ocr`: No OCR is performed on PDF documents. OCR might be performed on images though if OCR is not disabled. See [Disable/Enable OCR](#disableenable-ocr).

* `ocr_only`: Only OCR is performed.

* `ocr_and_text`: OCR and text extraction is performed.

```{note}
When omitted, `auto` value is used. OCR is skipped on PDF pages that already contain more than
10 characters of text. If you need OCR on every page regardless, set `ocr_and_text` or `ocr_only`.
```

## OCR Page Seg Mode

Set Tesseract to only run a subset of layout analysis and assume a certain form of image. The options for N are:

* `0` = Orientation and script detection (OSD) only.
* `1` = Automatic page segmentation with OSD.
* `2` = Automatic page segmentation, but no OSD, or OCR. (not implemented)
* `3` = Fully automatic page segmentation, but no OSD.
* `4` = Assume a single column of text of variable sizes.
* `5` = Assume a single uniform block of vertically aligned text.
* `6` = Assume a single uniform block of text.
* `7` = Treat the image as a single text line.
* `8` = Treat the image as a single word.
* `9` = Treat the image as a single word in a circle.
* `10` = Treat the image as a single character.
* `11` = Sparse text. Find as much text as possible in no particular order.
* `12` = Sparse text with OSD.
* `13` = Raw line. Treat the image as a single text line, bypassing hacks that are Tesseract-specific.

## OCR Preserve Interword Spacing

Spaces between the words will be deleted.

(vlm-ocr)=
## Using a Vision Language Model (VLM) for OCR

```{versionadded} 3.0
```

FSCrawler ships with Apache Tika's `tika-vlm` module, which can send images — including PDF pages
rendered by the PDF parser — to a Vision Language Model (VLM) instead of Tesseract. The module
provides parsers for OpenAI-compatible chat completions endpoints (`openai-vlm-parser`, which works
with vLLM, Ollama, OpenRouter, Azure OpenAI, LiteLLM…), Anthropic Claude (`claude-vlm-parser`) and
Google Gemini (`gemini-vlm-parser`).

The VLM parsers are configured through a custom Tika configuration file (see
{ref}`local-fs-settings`, `fs.tika_config_path`). The example below routes OCR to a local
[vLLM](https://docs.vllm.ai) server running an OpenAI-compatible endpoint on
`http://localhost:8000`:

```json
{
  "parsers": [
    { "default-parser": { "exclude": ["tesseract-ocr-parser"] } },
    { "pdf-parser": {
        "ocr": {
          "strategy": "AUTO",
          "maxPagesToOcr": 10
        }
      } },
    { "openai-vlm-parser": {
        "baseUrl": "http://localhost:8000",
        "model": "Qwen/Qwen2.5-VL-7B-Instruct",
        "maxTokens": 4096,
        "timeoutSeconds": 300
      } }
  ]
}
```

```yaml
name: "test"
fs:
  tika_config_path: '/path/to/tikaConfig.json'
```

Some notes about this configuration:

* The VLM parser must be listed **explicitly** in the configuration: Tika only initializes explicitly
  configured components. Without a custom Tika configuration, FSCrawler's default parser chain keeps
  the VLM parsers disabled.
* `default-parser` with `exclude` removes Tesseract from the parser chain, so OCR-able images are
  claimed by the VLM parser instead. If you leave Tesseract in, it takes precedence when installed.
* The PDF parser renders pages according to the `ocr.strategy` and hands them to the OCR parser —
  the VLM in this setup. Always set `maxPagesToOcr` explicitly: every OCR'ed page is one VLM
  request, so an unbounded value can be slow and expensive on large documents.
* `apiKey` can be set for hosted endpoints; for a local vLLM server it can usually be omitted.
* Extracted metadata includes the model used and token usage (`vlm:model`,
  `vlm:prompt_tokens`, `vlm:completion_tokens`) in the raw metadata.

### Deterministic decoding (`openai-vlm-deterministic-parser`)

Tika's `openai-vlm-parser` does not send a `temperature` field, so the server decides the sampling
temperature (vLLM typically defaults to `1.0`). Small OCR-oriented vision models hallucinate
heavily under sampling — measured against a local PaddleOCR-VL-1.6-0.9B, the very same request
returned the actual page text with `temperature: 0` and Chinese hallucinations with
`<|LOC_*|>` layout tokens without it. FSCrawler therefore ships
`openai-vlm-deterministic-parser`, a drop-in variant of `openai-vlm-parser` that forces
`"temperature": 0` (greedy decoding) on every request. It accepts exactly the same configuration
keys; simply use it in place of `openai-vlm-parser` in the custom Tika configuration. For OCR
workloads it should be the default choice.

```{warning}
The VLM parser performs a **single health check** (`GET {baseUrl}/v1/models`) when it is
initialized, at crawler startup. If the VLM server is not reachable at that moment, the parser
marks itself unavailable and silently skips OCR **for the whole lifetime of the crawler** — no
per-document retry is attempted. Make sure the VLM server is up before starting FSCrawler, and
restart FSCrawler if the server was down when it started.
```
