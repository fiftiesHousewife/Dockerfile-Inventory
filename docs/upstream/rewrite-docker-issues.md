# rewrite-docker issues to raise upstream

Drafts for https://github.com/openrewrite/rewrite (module `rewrite-docker`), found against 8.90.4. Each has a
minimal reproduction; the first three were found on real public Dockerfiles in the `sclorg` organisation. This repo
works around each one (noted under "Workaround"); delete a workaround and its note when upstream fixes the bug.

## 1. A comment line inside a continued `RUN` is lexed: an apostrophe starts a string

Docker drops whole-line comments before it reads an instruction, even inside a continuation.

```dockerfile
FROM ubi9
RUN echo a && \
# because it's tested better \
    echo b
```

Expected: one `RUN` with `echo a && echo b`. Actual: `token recognition error at: ''s tested better ...'`.
Seen in `sclorg/s2i-python-container`, `3.12/Dockerfile.c10s` and six more.
Workaround: `FactsParser` retries with whole-line comments blanked.

## 2. A comment ending in a backslash continues into the next line

```dockerfile
FROM ubi9
# RUN yum -y module enable nginx:$NGINX_VERSION && \
RUN echo a
# UID=1001 && GID=0
RUN echo b
```

Expected: two `RUN`s; the comment's trailing backslash is part of the comment. Actual: a syntax error (`mismatched
input '# UID=1001 && GID=0'`). Seen in `sclorg/nginx-container`, `1.20/Dockerfile.c9s` and 18 more.
Workaround: as 1.

## 3. A `# syntax=` line after the first instruction is rejected

```dockerfile
FROM ubi9
# syntax=docker/dockerfile:1
RUN echo
```

Expected: an ordinary comment (Docker reads directives only before the first instruction). Actual: a syntax error.
Workaround: as 1.

## 4. An unrecognised line is silently appended to the previous instruction's last argument

```dockerfile
FROM ubi9
RUNN echo typo
```

Expected: a syntax error. Actual: no error; the `FROM` image name becomes `ubi9\nRUNN echo typo`.
Workaround: `FactsParser` reports a literal holding a newline not preceded by a backslash as a failure.

## 5. `LABEL a=$X b=$Y` is parsed as one pair

```dockerfile
FROM ubi9
LABEL a=$REV b=$SRC
```

Expected: two pairs, `a` and `b`. Actual: one pair `a` whose value is `=$REV b=$SRC` (quoted values parse correctly).
Workaround: rules and the converter read label keys from the printed instruction.

## 6. An empty `ENV KEY=` value is `=`

```dockerfile
FROM ubi9
ENV SECRET_KEY=
```

Expected: an empty value. Actual: the value's literal text is `=`. Workaround: rules and the converter treat `=` as
empty.

## 7. Variables in `ENV` values are not split out

```dockerfile
FROM ubi9
ENV PASSWORD_FILE=$SECRETS/db
```

Expected: an `EnvironmentVariable` for `$SECRETS`, as in `FROM`. Actual: one `Literal`, `$SECRETS/db`.
Workaround: rules count a `$` outside single quotes as a variable.
