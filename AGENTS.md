# Kestra Dagger Plugin

## What

- Provides plugin components under `io.kestra.plugin.dagger`.
- Includes classes such as `Commands`, `Script`.

## Why

- This plugin integrates Kestra with Dagger.
- It provides tasks that run Dagger pipelines, commands, and scripts from Kestra.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `templates`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.templates.Example`

### Project Structure

```
plugin-dagger/
├── src/main/java/io/kestra/plugin/templates/
├── src/test/java/io/kestra/plugin/templates/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
