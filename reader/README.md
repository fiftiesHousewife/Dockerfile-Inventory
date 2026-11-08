# Dockerfile Reader

Reads a Dockerfile into the [Dockerfile model](../dsl/README.md): every instruction typed, the package installs in its
`RUN`s read as installs, and anything the model cannot type kept as written, with its reason, so rendering the model
gives the Dockerfile back. Built on the [Dockerfile parser](../parser/README.md). Java 21 or newer.

```xml
<dependency>
  <groupId>io.github.fiftieshousewife</groupId>
  <artifactId>dockerfile-reader</artifactId>
  <version>0.1.0</version>
</dependency>
```

```java
Conversion conversion = DockerfileReader.read("FROM ubi9\nRUN dnf install -y git && dnf clean all\nUSER 1001\n");
Dockerfile model = conversion.model();
List<Raw> untyped = conversion.raw();
```

`read` throws the parser's `DockerParsingException`, with the line and column, for text that is not a Dockerfile;
`read(Docker.File)` takes a tree already parsed. `raw()` lists what the model kept as written, for review.

Public API: `io.github.fiftieshousewife.dockerfile.reader` (`DockerfileReader`, `Conversion`); `.internal` may change in
any release. The module is named `io.github.fiftieshousewife.dockerfile.reader` in its manifest. Its dependencies:
`dockerfile-parser`, `dockerfile-model` and Jackson, for JSON arrays.

Licence: Apache License 2.0 (`../LICENSE`).
