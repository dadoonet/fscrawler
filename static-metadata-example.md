# Static Metadata Feature Example

This demonstrates the new static metadata feature that adds consistent metadata to all documents indexed by FSCrawler.

## Configuration Example

Create a `_settings.yaml` file with static metadata:

```yaml
name: "my-crawler"

fs:
  url: "/path/to/your/documents"
  update_rate: "15m"

tags:
  meta_filename: ".meta.yml"  # Optional: for per-directory metadata files
  staticTags:
    external:
      hostname: "web-server-01"
      environment: "production"
      datacenter: "us-east-1"
    custom:
      category: "company-documents"
      source: "file-system"
      department: "marketing"

elasticsearch:
  index: "documents"
```

## Result

With this configuration, every document indexed by FSCrawler will automatically have the following metadata fields added:

- `external.hostname`: "web-server-01"
- `external.environment`: "production"  
- `external.datacenter`: "us-east-1"
- `custom.category`: "company-documents"
- `custom.source`: "file-system"
- `custom.department`: "marketing"

## Benefits

1. **Consistency**: All documents get the same metadata without individual `.meta.yml` files
2. **Operational Context**: Add server, environment, and datacenter information automatically
3. **Classification**: Categorize all documents from a specific crawler instance
4. **Compliance**: Add required metadata for regulatory or organizational requirements

## Priority

Static metadata is applied first, then any `.meta.yml` file metadata will override static metadata if there are conflicts. This allows for global defaults with per-directory customization.

This feature implements the enhancement requested in issue #2120.