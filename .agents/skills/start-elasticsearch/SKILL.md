---
name: start-elasticsearch
description: This skill can help to start Elasticsearch locally, using start-local project from Elastic.
---

# Start Elasticsearch

Detailed instructions for the agent.

## When to Use

- Use this skill when you need to start Elasticsearch locally before running integration tests. If Elasticsearch is
  already running, there's no need to use this skill.

## Instructions

- Check with `curl` that Elasticsearch is running and accessible. By default, the cluster is expected to be running at
  http://localhost:9200 with the user `elastic` and the password `changeme`. If you can connect to it, there's no need to start a new instance.
- If not, run start-local project with:

```bash
curl -fsSL https://elastic.co/start-local | ES_LOCAL_PASSWORD="changeme" sh -s -- -v 9.3.1
```

- Please run this command within the `IGNORE_ME` directory of the project so it's never commited.

- Note that you can change the version, accordingly to the version of Elasticsearch you want to run. It's normally
  the one defined in the `pom.xml` file of the project. See `<elasticsearch.version>` property for more details.
