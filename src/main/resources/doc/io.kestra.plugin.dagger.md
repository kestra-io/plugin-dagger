# How to use the Dagger plugin

Run Dagger pipelines and inline scripts from Kestra flows using the Dagger CLI.

## Tasks

`Commands` runs one or more Dagger pipeline expressions — set `commands` (required list; each entry is passed individually to `dagger shell -c`). `Script` runs an inline Dagger script — set `script` (required; written to a temp file and piped to `dagger shell` via stdin). Use `Script` for multi-step pipelines authored inline; use `Commands` for discrete pipeline calls.

Both tasks default `containerImage` to `curlimages/curl:latest`. When the Dagger CLI is not already on `PATH`, a pinned version is downloaded and checksum-verified at runtime — this needs `curl`, `tar`, and `sha256sum` in the image, which the default image provides, so it works without modification. Override `containerImage` to use a custom base image, for example one with the Dagger CLI already installed. When using the Process task runner, `containerImage` is ignored and Dagger must be available on the host.

## Setup and environment

Use `beforeCommands` for setup steps that run before the pipeline, and `env` to pass environment variables. Store credentials such as registry tokens in [secrets](https://kestra.io/docs/concepts/secret) and reference them from `env`.

Set runner configuration on each task.
