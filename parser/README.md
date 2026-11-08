# Dockerfile Parser

A Dockerfile parser that reads a Dockerfile into a lossless tree (every space, comment and line continuation kept, so
printing the tree gives back the original text), with a visitor for reading and changing it. It is a fork of
OpenRewrite's `rewrite-docker` 8.90.4, the last release published under the Apache License 2.0, with the parser bugs
found on public Dockerfile estates fixed (`../docs/upstream/rewrite-docker-issues.md`), and it stays compatible with
OpenRewrite's `rewrite-core` 8.90.4 trees. Java 21 or newer.

```xml
<dependency>
  <groupId>io.github.fiftieshousewife</groupId>
  <artifactId>dockerfile-parser</artifactId>
  <version>1.0.0</version>
</dependency>
```

```java
Docker.File dockerfile = DockerParser.parseText("FROM ubi9\nUSER 1001\n");
String printed = dockerfile.printAll();
```

`parseText` throws a `DockerParsingException` with the line and column of the first syntax error. To parse many files
through OpenRewrite's `Parser` API, `DockerParser.builder().build().parseInputs(...)` reports each syntax error to the
execution context's error handler instead.

Public API: `io.github.fiftieshousewife.dockerfile.parser` (`DockerParser`, `DockerVisitor`, `DockerIsoVisitor`) and
`.tree` (`Docker` and its nodes, `Space`, `Comment`). `.internal` and `.internal.grammar` may change in any release.
The module is named `io.github.fiftieshousewife.dockerfile.parser` in its manifest.

Licence: Apache License 2.0 (`LICENSE`); attribution and changes in `NOTICE`.
