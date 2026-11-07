# Dockerfile Parser

A Dockerfile parser that reads a Dockerfile into a lossless tree (every space, comment and line continuation kept, so
printing the tree gives back the original text), with a visitor for reading and changing it. It is a fork of
OpenRewrite's `rewrite-docker` 8.90.4, the last release published under the Apache License 2.0, with the parser bugs
found on public Dockerfile estates fixed (`../docs/upstream/rewrite-docker-issues.md`), and it stays compatible with
OpenRewrite's `rewrite-core` 8.90.4 trees.

```xml
<dependency>
  <groupId>io.github.fiftieshousewife</groupId>
  <artifactId>dockerfile-parser</artifactId>
  <version>1.0.0</version>
</dependency>
```

```java
DockerParser parser = DockerParser.builder().build();
List<SourceFile> files = parser.parse(new InMemoryExecutionContext(Throwable::printStackTrace), dockerfileText)
        .toList();
Docker.File file = (Docker.File) files.getFirst();
```

Public API: `io.github.fiftieshousewife.dockerfile.parser` (`DockerParser`, `DockerVisitor`, `DockerIsoVisitor`) and
`.tree` (`Docker` and its nodes, `Space`, `Comment`). `.internal` and `.internal.grammar` may change in any release.

Licence: Apache License 2.0 (`LICENSE`); attribution and changes in `NOTICE`.
