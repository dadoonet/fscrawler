# Code Style

## License

Apache 2.0 license header required on **all** source files. Add it to any new file created.

## Logging

- Use **Log4j2** for logging (never SLF4J directly)
- Use `logger.fatal()` for unrecoverable errors
- Use `logger.error()` / `logger.warn()` / `logger.info()` / `logger.debug()` for other levels

## Serialization

- Use **Jackson** for JSON serialization
- Use **Gestalt** for configuration management (YAML)

## Error Handling

- `FsCrawlerIllegalConfigurationException` for configuration errors
- `FsCrawlerValidator.validateSettings()` to validate job settings
- Use try-with-resources for all `Closeable` objects

## Formatting

The project uses the **Spotless** Maven plugin to enforce formatting.

Before committing, verify formatting:
```bash
mvn spotless:check
```

If there are errors, fix them automatically:
```bash
mvn spotless:apply
```

Default style: 4-space indent. Follow existing patterns in the file being edited.
