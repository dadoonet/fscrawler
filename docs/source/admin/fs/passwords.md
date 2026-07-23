(password-settings)=
# Password providers

```{contents}
:backlinks: entry
```

FSCrawler can read passwords for protected documents from a dedicated password provider.
Use this when your crawl contains encrypted PDFs, Office files, or when the REST service
needs a default password lookup strategy.

Here is a list of password settings (under the `passwords.` prefix):

| Name                                   | Environment Variable                              | Default value | Documentation                          |
|----------------------------------------|---------------------------------------------------|---------------|----------------------------------------|
| `passwords.provider`                   | `FSCRAWLER_PASSWORDS_PROVIDER`                    | `"noop"`      | [Active provider](#active-provider)    |
| `passwords.providers.disk.url`         | `FSCRAWLER_PASSWORDS_PROVIDERS_DISK_URL`          | `fs.url`      | [Disk provider](#disk-provider)        |
| `passwords.providers.static.values`    | `FSCRAWLER_PASSWORDS_PROVIDERS_STATIC_VALUES`     | `null`        | [Static provider](#static-provider)    |
| `passwords.providers.chained.providers`| `FSCRAWLER_PASSWORDS_PROVIDERS_CHAINED_PROVIDERS` | `null`        | [Chained provider](#chained-provider)  |

## Active provider

`passwords.provider` selects which password provider FSCrawler uses for normal crawling.

Available providers are:

* `noop` (default): do not try any configured password
* `disk`: read password sidecar files from disk
* `static`: try one or more configured passwords in order
* `chained`: combine multiple providers and try them in order

For example:

```yaml
name: "contracts"
fs:
  url: "/data/contracts"
passwords:
  provider: "disk"
```

## Disk provider

The `disk` provider reads password sidecar files from a directory tree.
For a document, it tries candidates in this order:

1. `<relative/path/to/file.ext>.password`
2. `<relative/path/to/dir>/.password`, then each parent directory `.password`
3. `<disk-root>/.password`

When `passwords.providers.disk.url` is not set, the disk root defaults to `fs.url`.

### Disk mirror under `fs.url`

```yaml
name: "docs"
fs:
  url: "/srv/docs"
passwords:
  provider: "disk"
```

If FSCrawler reads `/srv/docs/finance/q1/report.pdf`, it looks for:

* `/srv/docs/finance/q1/report.pdf.password`
* `/srv/docs/finance/q1/.password`
* `/srv/docs/finance/.password`
* `/srv/docs/.password`

### External disk mirror

Use `passwords.providers.disk.url` when you want password files outside the crawled tree:

```yaml
name: "docs"
fs:
  url: "/srv/docs"
passwords:
  provider: "disk"
  providers:
    disk:
      url: "/run/secrets/fscrawler-passwords"
```

With the same document path `/srv/docs/finance/q1/report.pdf`, FSCrawler looks for:

* `/run/secrets/fscrawler-passwords/finance/q1/report.pdf.password`
* `/run/secrets/fscrawler-passwords/finance/q1/.password`
* `/run/secrets/fscrawler-passwords/finance/.password`
* `/run/secrets/fscrawler-passwords/.password`

This is useful when the document tree is mounted read-only or when a sidecar mirror is managed
by another process.

## Static provider

The `static` provider tries configured passwords in order until one works.

```yaml
name: "docs"
fs:
  url: "/srv/docs"
passwords:
  provider: "static"
  providers:
    static:
      values:
        - "${PDF_PASSWORD}"
        - "${OFFICE_PASSWORD}"
```

```{note}
For a single fallback password, `passwords.providers.static.value` is also supported.
Use `values` when you want an ordered list.
```

## Chained provider

The `chained` provider delegates to other providers in order.
This is useful when you want a disk mirror first and static fallbacks second.

```yaml
name: "docs"
fs:
  url: "/srv/docs"
passwords:
  provider: "chained"
  providers:
    disk:
      url: "/run/secrets/fscrawler-passwords"
    static:
      values:
        - "${PDF_PASSWORD}"
        - "${FALLBACK_PASSWORD}"
    chained:
      providers:
        - "disk"
        - "static"
```

```{important}
When `passwords.provider` is `chained`, `passwords.providers.chained.providers` is required.
The list cannot be empty and cannot contain `chained` itself.
```

## Security notes

* Prefer an external `passwords.providers.disk.url` when you do not want password sidecars mixed
  with the crawled content tree.
* FSCrawler automatically excludes `*.password` and `*/.password` from crawling, so password
  sidecars are not indexed as documents.
* FSCrawler does not log password values. Warnings may mention provider names or sidecar paths,
  but not the secret contents.
* Keep sidecar files and settings readable only by the FSCrawler process owner.
* Avoid putting passwords directly in shell history; placeholders and environment variables are
  safer than raw `curl` commands copied into terminals.

## REST uploads

REST uploads can also use passwords for protected documents. An explicit request password
short-circuits the configured job password provider for that request only.

For multipart uploads, FSCrawler checks password inputs in this order:

1. form field `password`
2. HTTP header `password`
3. query parameter `password`

Examples:

```sh
curl -F "file=@protected.pdf" \
  -F "password=secret" \
  "http://127.0.0.1:8080/_document"
```

```sh
curl -F "file=@protected.pdf" \
  -H "password: secret" \
  "http://127.0.0.1:8080/_document"
```

```sh
curl -F "file=@protected.pdf" \
  "http://127.0.0.1:8080/_document?password=secret"
```

For JSON 3rd-party uploads, only header and query parameters are available, and the header wins:

```sh
curl -X POST \
  -H "Content-Type: application/json" \
  -H "password: secret" \
  http://127.0.0.1:8080/_document \
  -d '{
    "type": "http",
    "http": {
      "url": "https://example.org/protected.pdf"
    }
  }'
```

See {ref}`rest-service` for the full REST API.
