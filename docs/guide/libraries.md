# The libraries

Three parts of the pipeline are published on their own, for Java 21 or newer, under `io.github.fiftieshousewife`:

| Library | What it does |
| --- | --- |
| [`dockerfile-parser`](../../parser/README.md) | A lossless Dockerfile parser and tree |
| [`dockerfile-model`](../../dsl/README.md) | An immutable Dockerfile model, a builder that enforces a base-image policy, and one renderer |
| [`dockerfile-reader`](../../reader/README.md) | A Dockerfile read into the model |
