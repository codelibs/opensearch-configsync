# OpenSearch Config Sync Plugin

[![Java CI with Maven](https://github.com/codelibs/opensearch-configsync/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/opensearch-configsync/actions/workflows/maven.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.codelibs.opensearch/opensearch-configsync)](https://central.sonatype.com/artifact/org.codelibs.opensearch/opensearch-configsync)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

OpenSearch Config Sync Plugin distributes configuration files across the nodes of
an OpenSearch cluster. Files such as dictionaries, synonym lists and scripts are
uploaded once through a REST API, stored in an index, and written to the
configuration directory of every node by a scheduled updater. This removes the
need to copy files to each node by hand or to rebuild node images whenever a
dictionary changes.

## Compatibility

| Plugin Version | OpenSearch Version | Java Version |
|----------------|--------------------|--------------|
| 3.8.x          | 3.8.0+             | 21+          |
| 3.7.x          | 3.7.0+             | 21+          |

Released versions are listed on
[Maven Central](https://central.sonatype.com/artifact/org.codelibs.opensearch/opensearch-configsync/versions).

## Installation

```bash
$OPENSEARCH_HOME/bin/opensearch-plugin install org.codelibs.opensearch:opensearch-configsync:3.8.0
```

Restart the node, then confirm that the plugin is loaded:

```bash
$OPENSEARCH_HOME/bin/opensearch-plugin list
# configsync
```

To install a locally built package instead:

```bash
mvn clean package
$OPENSEARCH_HOME/bin/opensearch-plugin install file:target/releases/opensearch-configsync-3.8.0-SNAPSHOT.zip
```

Use `opensearch-plugin remove configsync` to uninstall.

## Getting Started

Register a file. The `path` parameter is where the file will be written on each
node, relative to the OpenSearch configuration directory:

```bash
curl -XPOST -H 'Content-Type: application/json' \
  'localhost:9200/_configsync/file?path=user-dict.txt' \
  --data-binary @user-dict.txt
```

The file is stored in the index and picked up by every node on the next flush
interval. To distribute it immediately:

```bash
curl -XPOST -H 'Content-Type: application/json' 'localhost:9200/_configsync/flush'
```

## REST API

### `POST /_configsync/file`

Registers or replaces a file. The request body is the file content.

| Parameter | Description |
|-----------|-------------|
| `path` | Destination path relative to the OpenSearch configuration directory. |

### `GET /_configsync/file`

With `path`, returns the content of that file. Without it, returns the list of
registered paths.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `path` | none | Return the content of this file instead of the list. |
| `sort` | `path` | Sort key for the list, as `field` or `field:order`. |
| `fields` | all | Comma-separated fields to return for each entry. |
| `from` | `0` | Offset into the list. |
| `size` | `10` | Number of entries to return. |

```bash
curl -XGET 'localhost:9200/_configsync/file'
# {"acknowledged":true,"path":["user-dict.txt"]}

curl -XGET 'localhost:9200/_configsync/file?path=user-dict.txt'
```

### `DELETE /_configsync/file`

Removes a file from the index. Copies already written to the nodes are left in
place.

| Parameter | Description |
|-----------|-------------|
| `path` | Path of the file to remove. |

### `POST /_configsync/flush`

Writes the registered files to every node immediately, without waiting for the
next scheduled run.

### `POST /_configsync/reset`

Restarts the synchronization scheduler on every node. Useful after changing
`configsync.flush_interval` at runtime.

### `GET /_configsync/wait`

Blocks until the config sync index reaches the requested health status. Intended
for startup scripts that must not proceed until the plugin is ready.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `status` | `yellow` | Cluster health status to wait for. |
| `timeout` | `30s` | Maximum time to wait. |

## Configuration

Set these in `opensearch.yml`:

```yaml
configsync.flush_interval: 1m
```

| Setting | Default | Description |
|---------|---------|-------------|
| `configsync.flush_interval` | `1m` | Interval between synchronization runs. Can be updated through the cluster settings API. |
| `configsync.file_updater.enabled` | `true` | Whether this node writes synchronized files to disk. Set to `false` on nodes that should only serve the API. |
| `configsync.config_path` | OpenSearch config directory | Directory the `path` parameter is resolved against. |
| `configsync.index` | `configsync` | Index used to store the files. |
| `configsync.scroll_size` | `1` | Number of files fetched per scroll request during a run. |
| `configsync.scroll_time` | `1m` | Scroll timeout used during a run. |
| `configsync.xpack.security.user` | none | `user:password` credentials sent as HTTP Basic authorization when the cluster requires authentication. |

The plugin also registers `.configsync` as a system index.

## Building from Source

Java 21 and Maven 3.6 or later are required.

```bash
git clone https://github.com/codelibs/opensearch-configsync.git
cd opensearch-configsync
mvn clean package
```

The plugin package is written to `target/releases/`.

```bash
mvn test              # run the test suite
mvn license:check     # verify license headers
mvn license:format    # apply license headers
```

## Contributing

Issues and pull requests are welcome at
[github.com/codelibs/opensearch-configsync](https://github.com/codelibs/opensearch-configsync).
Please add tests for behaviour changes, keep the Apache License 2.0 headers in
place, and make sure `mvn test` and `mvn license:check` pass before opening a pull
request.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
