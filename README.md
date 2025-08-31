# OpenSearch Config Sync Plugin

[![Java CI with Maven](https://github.com/codelibs/opensearch-configsync/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/opensearch-configsync/actions/workflows/maven.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.codelibs.opensearch/opensearch-configsync.svg)](https://repo1.maven.org/maven2/org/codelibs/opensearch/opensearch-configsync/)

## Overview

The OpenSearch Config Sync Plugin provides seamless distribution and synchronization of configuration files (such as scripts, dictionaries, or custom settings) across all nodes in your OpenSearch cluster. This plugin ensures consistent configuration management in distributed environments through automated file synchronization.

### Key Features

- **Distributed File Management**: Centrally manage and distribute files across all cluster nodes
- **Automatic Synchronization**: Files are automatically synced to all nodes at configurable intervals
- **RESTful API**: Complete REST API for file operations (upload, download, list, delete)
- **Secure Storage**: Files are securely stored in the `.configsync` system index
- **Cluster-wide Operations**: Flush and reset operations across the entire cluster
- **Real-time Updates**: Changes are propagated to all nodes without manual intervention

## Compatibility

| Plugin Version | OpenSearch Version | Java Version |
|---------------|--------------------|--------------|
| 3.2.x         | 3.2.0+            | 21+          |

## Version History

[View all versions in Maven Repository](https://repo1.maven.org/maven2/org/codelibs/opensearch/opensearch-configsync/)

### Issues/Questions

Please file an [issue](https://github.com/codelibs/opensearch-configsync/issues) if you encounter any problems or have questions.

## Installation

```bash
$OPENSEARCH_HOME/bin/opensearch-plugin install org.codelibs.opensearch:opensearch-configsync:3.2.0
```

**Note**: Replace `3.2.0` with the latest version available in the [Maven Repository](https://repo1.maven.org/maven2/org/codelibs/opensearch/opensearch-configsync/).

## Getting Started

### API Endpoints

The plugin provides RESTful API endpoints for managing configuration files:

#### Upload/Register File

Upload a file to be synchronized across all cluster nodes:

```bash
curl -XPOST -H 'Content-Type:application/json' \
  localhost:9200/_configsync/file?path=user-dict.txt \
  --data-binary @user-dict.txt
```

- **path**: Target file location under `$OPENSEARCH_CONF` directory (e.g., `/etc/opensearch/user-dict.txt`)
- The file will be stored in the `.configsync` index and distributed to all nodes

#### List All Files

Retrieve a list of all managed configuration files:

```bash
curl -XGET -H 'Content-Type:application/json' localhost:9200/_configsync/file
```

**Response:**
```json
{"acknowledged":true,"path":["user-dict.txt"]}
```

#### Download File

Retrieve a specific configuration file:

```bash
curl -XGET -H 'Content-Type:application/json' \
  localhost:9200/_configsync/file?path=user-dict.txt
```

Returns the file content as stored in the `.configsync` index.

#### Delete File

Remove a file from synchronization:

```bash
curl -XDELETE -H 'Content-Type:application/json' \
  localhost:9200/_configsync/file?path=user-dict.txt
```

This removes the file from the `.configsync` index, but does not delete existing files on nodes.

### Additional Operations

#### Force Flush

Force immediate synchronization across all nodes:

```bash
curl -XPOST -H 'Content-Type:application/json' localhost:9200/_configsync/flush
```

#### Reset Synchronization

Restart the synchronization scheduler:

```bash
curl -XPOST -H 'Content-Type:application/json' localhost:9200/_configsync/reset
```

## Configuration

### Automatic Synchronization

Files are automatically synchronized from the `.configsync` index at regular intervals. Configure the sync interval in your OpenSearch configuration file:

```yaml
# opensearch.yml
configsync.flush_interval: 1m  # Default: 1 minute
```

### Available Settings

- `configsync.flush_interval`: Interval for automatic file synchronization (default: `1m`)
- `configsync.file_updater.enabled`: Enable/disable the file updater (default: `true`)
- `configsync.scroll_size`: Number of files to process in each scroll request (default: `1000`)
- `configsync.scroll_time`: Scroll timeout for file processing (default: `1m`)
- `configsync.config_path`: Custom path for configuration files (default: OpenSearch config directory)
- `configsync.index`: Custom index name for storing files (default: `.configsync`)

## Development

### Building the Plugin

```bash
mvn package
```

The built plugin will be available in `target/releases/`.

### Running Tests

```bash
# Unit tests
mvn test
```

### Release

```bash
mvn release:prepare
mvn release:perform
```

## License

This project is licensed under the Apache Software License, Version 2.0.

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

