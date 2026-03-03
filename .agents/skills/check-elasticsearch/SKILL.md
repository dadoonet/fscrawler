---
name: check-elasticsearch
description: This skill can call Elasticsearch APIs to check either the status of an index, its content or its mapping.
---

# Check Elasticsearch

Detailed instructions for the agent.

## When to Use

- Use this skill when you need to check some data in Elasticsearch. This is useful when a test ran but the expected
outcome is different than the actual outcome. You can use this skill to check if the data in Elasticsearch is correct, 
if the index is healthy, or if the mapping is correct.

## Instructions

- Check with `curl` that Elasticsearch is running and accessible.
- Consider by default that the cluster is running at http://localhost:9200 with the user `elastic` and the password `changeme`. 
If the user provided different connection details, use those instead.
- You can check the status of an index with the `_cat/indices` API, the content of an index with the `_search` API, and 
the mapping of an index with the `_mapping` API.
