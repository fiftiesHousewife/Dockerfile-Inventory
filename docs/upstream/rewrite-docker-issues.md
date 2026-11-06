# rewrite-docker issues to raise upstream

Drafts for https://github.com/openrewrite/rewrite (module `rewrite-docker`), found against 8.90.4. Each has a
minimal reproduction; the first two were found on real public Dockerfiles in the `sclorg` organisation. This repo
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

## 2. A comment of the form `# key=value` after the first instruction is rejected

Docker reads parser directives (`# syntax=...`, `# escape=...`) only before the first instruction; after it, any
`#` line is an ordinary comment.

```dockerfile
FROM ubi9
# a=b
RUN echo
```

Expected: an ordinary comment. Actual: `extraneous input '# a=b' expecting {<EOF>, 'FROM'}`. Any word before the
`=` triggers it (`# UID=1001 && GID=0`, `# syntax=docker/dockerfile:1`, `# a = b`); `# words then a=b` does not.
Seen in `sclorg/nginx-container`, `1.20/Dockerfile.c9s` (`# UID=1001 && GID=0`) and 18 more.
Workaround: `FactsParser` retries with whole-line comments blanked.

## 3. An unrecognised line is silently appended to the previous instruction's last argument

```dockerfile
FROM ubi9
RUNN echo typo
```

Expected: a syntax error. Actual: no error; the `FROM` image name becomes `ubi9\nRUNN echo typo`.
Workaround: `FactsParser` reports a literal holding a newline not preceded by a backslash as a failure.

## 4. `LABEL a=$X b=$Y` is parsed as one pair

```dockerfile
FROM ubi9
LABEL a=$REV b=$SRC
```

Expected: two pairs, `a` and `b`. Actual: one pair `a` whose value is `=$REV b=$SRC` (quoted values parse correctly).
Workaround: rules and the converter read label keys from the printed instruction.

## 5. An empty `ENV KEY=` value is `=`

```dockerfile
FROM ubi9
ENV SECRET_KEY=
```

Expected: an empty value. Actual: the value's literal text is `=`. Workaround: rules and the converter treat `=` as
empty.

## 6. Variables in `ENV` values are not split out

```dockerfile
FROM ubi9
ENV PASSWORD_FILE=$SECRETS/db
```

Expected: an `EnvironmentVariable` for `$SECRETS`, as in `FROM`. Actual: one `Literal`, `$SECRETS/db`.
Workaround: rules count a `$` outside single quotes as a variable.

## 7. A line holding only a backslash is glued onto the instruction before it

Docker joins a line ending in a backslash with the next one, so ` \` followed by `RUN ...` reads as ` RUN ...`;
the instruction before has already ended.

```dockerfile
FROM ubi9
COPY a /b
 \
RUN A="x"
```

Expected: a `COPY` and a `RUN`. Actual: `extraneous input '=' expecting ...` at the `RUN` (the same after `EXPOSE`).
Seen in `sclorg/postgresql-container`, `13/Dockerfile.rhel9`, between comment lines and the `RUN`.
Workaround: `FactsParser`'s retry blanks such a line.
