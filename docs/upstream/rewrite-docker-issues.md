# rewrite-docker issues to raise upstream

Drafts for https://github.com/openrewrite/rewrite (module `rewrite-docker`), found against 8.90.4 on real public
Dockerfiles (`sclorg`, `openshift`, `konflux-ci`, `redhat-developer`); none is filed yet, as filing is public and the
owner's call. Each has a minimal reproduction. The repo's fork of the parser (`parser/`) fixes them one by one; each
fixed issue says how ("Fixed in the fork"), and the rest say how the pipeline works around them, if it does. The
retry, a second parse with rewritten text, went when the last bug it worked around was fixed.

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
Fixed in the fork (with issue 7's fix the backslash before the comment line is a line continuation, so the line is
lexed as a comment; inside a quoted string spanning lines a comment line is part of the string, so a quote in it
neither ends nor starts one). Facts' RUN reader drops such lines, as Docker does, before the shell reader sees them.

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
Fixed in the fork (the lexer reads parser directives only before the first instruction).

## 3. An unrecognised line is silently appended to the previous instruction's last argument

```dockerfile
FROM ubi9
RUNN echo typo
```

Expected: a syntax error. Actual: no error; the `FROM` image name becomes `ubi9\nRUNN echo typo`.
Fixed in the fork (after parsing, a newline inside an instruction that no backslash, blank line or whole-line comment
continues is reported as a syntax error, `unrecognised line: ...`, at the glued line's first token).

## 4. `LABEL a=$X b=$Y` is parsed as one pair

```dockerfile
FROM ubi9
LABEL a=$REV b=$SRC
```

Expected: two pairs, `a` and `b`. Actual: one pair `a` whose value is `=$REV b=$SRC` (quoted values parse correctly).
Fixed in the fork (a label value is the tokens up to the next whitespace, variables included, split into literals and
variables; `key=` has an empty value). Rule R3 reads label keys from the tree. The converter still reads `ENV` and
`LABEL` from the printed instruction, as it needs each value as written, quotes and escapes kept, for the model's form.

## 5. An empty `ENV KEY=` value is `=`

```dockerfile
FROM ubi9
ENV SECRET_KEY=
```

Expected: an empty value. Actual: the value's literal text is `=`.
Fixed in the fork (an `=` with nothing after it gives a value with no contents, as for `ARG`).

## 6. Variables in `ENV` values are not split out

```dockerfile
FROM ubi9
ENV PASSWORD_FILE=$SECRETS/db
```

Expected: an `EnvironmentVariable` for `$SECRETS`, as in `FROM`. Actual: one `Literal`, `$SECRETS/db`.
Fixed in the fork (in `KEY=value` each unquoted variable is an `EnvironmentVariable` and the text between, quoted strings
as written, a `Literal`). Rule S2 still counts a `$` outside single quotes as a variable, as one inside double quotes
stays in the quoted text. Facts now expands such a variable in an `ENV` value, and writes one it cannot resolve as
`${NAME}`.

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
Fixed in the fork (`ARG` takes several declarations, told apart by the whitespace between them; each name after the
first is a `Docker.Arg.ArgPair`).

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
Fixed in the fork (with issue 10's fix; a value ends at the first whitespace outside quotes). `FactExtractor`, the
converter and rule S2 read every pair from the tree.

## 13. `ARG A=` (an empty default) is rejected

```dockerfile
FROM ubi9
ARG HTTP_PROXY=
```

Expected: a build argument whose default is empty. Actual: a syntax error at the next line or the end of the file.
`ARG HTTP_PROXY=""` parses. Seen in 4 `konflux-ci` files (proxy settings).
Fixed in the fork (an `=` with nothing after it gives a value with no contents; no `=` gives no value).

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
Fixed in the fork (the `KEY=value` form is taken only when the `=` is right after the key, tested at the start of the
pair so the parser's prediction sees it; otherwise the pair is the legacy form). The same holds for `LABEL`.

