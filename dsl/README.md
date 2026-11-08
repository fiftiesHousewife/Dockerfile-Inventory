# Dockerfile Model

An immutable Dockerfile model (a record for each instruction), a fluent builder that refuses an image breaking its
policy, and one renderer that writes the Dockerfile, so Dockerfiles written in code all come out in the same shape.
No dependencies. Java 21 or newer.

```xml
<dependency>
  <groupId>io.github.fiftieshousewife</groupId>
  <artifactId>dockerfile-model</artifactId>
  <version>0.1.0</version>
</dependency>
```

```java
BaseImage base = new BaseImage("registry.access.redhat.com/ubi9/ubi-minimal", "9.6",
        "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
Built built = ImageBuilder.finalStage(base)
        .install("python3")
        .copy("app/", "/opt/app/")
        .user(1001)
        .entrypoint("python3", "/opt/app/main.py")
        .build(Policy.of(List.of(new Policy.Approved("ubi9/ubi-minimal", "9"))));
String dockerfile = DockerfileRenderer.render(built.dockerfile(), Target.DEFAULT);
```

`build` throws a `PolicyViolation` listing every breach at once: the final base approved and pinned by digest, the
final user numeric and not root, paths the process writes made writable by group 0 (so any UID can run it, as
OpenShift does), an exec-form entrypoint, installs cleaned in the same `RUN`. `ImageBuilder.of(model).check(policy)`
lists the breaches without throwing. `Target` says what the builder that will read the Dockerfile supports (heredocs,
`RUN --mount`); the default supports neither.

Public API: the package `io.github.fiftieshousewife.dockerfile.model`, the module of the same name. Before 1.0 a minor
version may change it.

Licence: Apache License 2.0 (`../LICENSE`).
