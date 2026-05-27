# How to use the Dagger plugin

Run Dagger pipelines and inline scripts from Kestra flows using the Dagger CLI.

## Tasks

`Commands` runs one or more Dagger pipeline expressions — set `commands` (required list; each entry is passed individually to `dagger shell -c`). `Script` runs an inline Dagger script — set `script` (required; written to a temp file and piped to `dagger shell` via stdin). Use `Script` for multi-step pipelines authored inline; use `Commands` for discrete pipeline calls.

Both tasks default `containerImage` to `curlimages/curl:latest` — override this with an image that includes the Dagger CLI when using a Docker task runner. When using the Process task runner, `containerImage` is ignored and Dagger must already be installed on the host. Use `beforeCommands` for setup steps that run before the pipeline, and `env` to pass environment variables. Apply [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults) to share runner configuration across tasks.
