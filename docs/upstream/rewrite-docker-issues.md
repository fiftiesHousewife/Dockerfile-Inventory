# rewrite-docker issues to raise upstream

Drafts for https://github.com/openrewrite/rewrite (module `rewrite-docker`), found against 8.90.4 on real public
Dockerfiles (`sclorg`, `openshift`, `konflux-ci`, `redhat-developer`); none is filed yet, as filing is public and the owner's call. Each
has a minimal reproduction. This repo works around twelve of them (noted under "Workaround"; most in the parser's retry,
second parse, which runs only after a failure and keeps every line number); delete a workaround and its note when
upstream fixes the bug. Issues 11, 14 and 15 have no workaround.

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
Workaround: the retry (`RetryText`) parses again with whole-line comments blanked.

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
Workaround: the retry (`RetryText`) parses again with whole-line comments blanked.

## 3. An unrecognised line is silently appended to the previous instruction's last argument

```dockerfile
FROM ubi9
RUNN echo typo
```

Expected: a syntax error. Actual: no error; the `FROM` image name becomes `ubi9\nRUNN echo typo`.
Workaround: `MergedLines` reports a literal holding a newline not preceded by a backslash as a failure.

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
Fixed in the fork (the lexer no longer reads a backslash before a newline as an escaped character inside a word, so
it is a line continuation).

## 8. Whitespace before a comma in a JSON array is "not print idempotent"

```dockerfile
FROM ubi9
CMD ["a" , "b"]
```

Expected: an exec form of `a` and `b`. Actual: `IllegalStateException: ... is not print idempotent`. Any spaces or
tabs between a closing quote and the comma trigger it, in any instruction with a JSON array (`CMD`, `ENTRYPOINT`,
`RUN`, `VOLUME`); spaces after the comma or inside the brackets do not.
Seen in 11 `openshift` files, e.g. `CMD ["/bin/bash", "-c" ,"exec cp -rp ..."]`.
Fixed in the fork (the whitespace is kept on the element before the comma, as a `CommaPrefix` marker, and printed).

## 9. A flag value `${NAME:-default}` is "not print idempotent"

```dockerfile
FROM --platform=${BUILDPLATFORM:-linux/amd64} rust:latest
```

Expected: a `FROM` whose platform flag holds the variable. Actual: `IllegalStateException: ... is not print
idempotent`. Any flag (`--platform`, `--chown`) with `:-` inside `${...}`; `${NAME-default}`, `${NAME}` and `$NAME`
parse, as does `${NAME:-default}` outside a flag.
Seen in 12 `openshift` files.
Fixed in the fork (the flag value's variable keeps its whole `${...}` text, as variables elsewhere do).

## 10. `ARG` with several names and no values is rejected

```dockerfile
FROM ubi9
ARG TARGETOS TARGETARCH
```

Expected: two build arguments without defaults, as BuildKit accepts. Actual: `extraneous input 'TARGETARCH'
expecting {<EOF>, 'FROM'}`. `ARG A=1 B=2` parses. Seen in 10 `openshift` files.
Workaround: the retry (`RetryText`) writes `ARG TARGETOS=$TARGETOS TARGETARCH=$TARGETARCH`, which leaves each unknown.

## 11. A heredoc after a command in `RUN` is rejected

```dockerfile
FROM ubi9
RUN cat > /etc/x.toml <<EOF
a = 1
EOF
```

Expected: a `RUN` whose command reads the heredoc, as BuildKit runs it. Actual: `extraneous input '<<EOF'
expecting {<EOF>, 'FROM'}`; the body's lines are then read as instructions. Only `RUN <<EOF` (the heredoc as the
script) and `COPY <<EOF` parse. Seen in at least 4 `openshift` files (bootc kernel arguments, systemd units).
Workaround: none safe; the file is counted as a parse failure.

## 12. `ARG A=1 B=2` is one argument whose value is `1 B=2`

```dockerfile
FROM ubi9
ARG A=1 B=2
```

Expected: two build arguments, `A` (1) and `B` (2). Actual: one `Docker.Arg`, name `A`, value `1 B=2`; `B` is lost.
The same family as issue 4 (`LABEL`). Found through a workaround for issue 10, which writes `ARG A=$A B=$B`.
Workaround: `FactExtractor` reads an `ARG`'s names and values from its text.

## 13. `ARG A=` (an empty default) is rejected

```dockerfile
FROM ubi9
ARG HTTP_PROXY=
```

Expected: a build argument whose default is empty. Actual: a syntax error at the next line or the end of the file.
`ARG HTTP_PROXY=""` parses. Seen in 4 `konflux-ci` files (proxy settings).
Workaround: the retry (`RetryText`) writes `ARG HTTP_PROXY=""`.

## 14. A `COPY` heredoc with a quoted delimiter is rejected

```dockerfile
FROM ubi9
COPY <<'EOF' /etc/x.conf
a = 1
EOF
```

Expected: the file written without variable expansion, as BuildKit does for a quoted delimiter. Actual: a syntax
error at the body. `COPY <<EOF` parses. Seen in 1 `konflux-ci` file.
Workaround: none safe (dropping the quotes would change what is copied); the file is counted as a parse failure.

## 15. A legacy `ENV KEY = value` (spaces around `=`) is "not print idempotent"

```dockerfile
FROM ubi9
ENV PERL_LWP_SSL_VERIFY_HOSTNAME = 0
```

Expected: the legacy form, one variable whose value is `= 0`, as Docker reads it. Actual: `IllegalStateException:
... is not print idempotent`. `ENV A "= 0"` parses. Seen in 1 `redhat-developer` file.
Workaround: none (one file); it is counted as a parse failure.

