# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**FSCrawler** is a Java-based file system crawler for Elasticsearch that indexes binary documents (PDF, MS Office, etc.). It supports local filesystem, SSH/FTP, S3, and HTTP sources, and provides a REST API for document uploads. Supports Elasticsearch 7.x, 8.x, and 9.x.

- **Language**: Java 17+
- **Build**: Maven multi-module (16 modules)
- **Document parsing**: Apache Tika
- **REST framework**: Jersey (JAX-RS)
- **Plugin system**: PF4J
- **Testing**: JUnit 4 + Randomized Testing Framework + TestContainers

## Detailed Instructions

@.claude/rules/architecture.md
@.claude/rules/build-commands.md
@.claude/rules/testing.md
@.claude/rules/code-style.md
@.claude/rules/git-workflow.md

## Documentation

- User docs: https://fscrawler.readthedocs.io/
- Source: `docs/source/` (reStructuredText, built by ReadTheDocs)
- Build guide: `docs/source/dev/build.rst`
