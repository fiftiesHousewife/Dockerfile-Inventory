`s2i-python-3.12-comments.Dockerfile` and `nginx-1.20-comments.Dockerfile` are trimmed from sclorg's public
`s2i-python-container` (`3.12/Dockerfile.c10s`) and `nginx-container` (`1.20/Dockerfile.c9s`): valid Dockerfiles
that rewrite-docker 8.90.4 rejects because of comment lines (an apostrophe in a comment inside a continued `RUN`; a
comment of the form `# key=value`, `# UID=1001 && GID=0`, after the first instruction).
`s2i-nodejs-jinja-template.Dockerfile` is the first 40 lines of sclorg's `s2i-nodejs-container` `src/Dockerfile`: a
Jinja template saved under a Dockerfile's name, which no Dockerfile parser can read.
`postgresql-13-lone-backslash.Dockerfile` is trimmed from sclorg's `postgresql-container` (`13/Dockerfile.rhel9`):
a line holding only a backslash after a `COPY`, which rewrite-docker 8.90.4 glues onto the `COPY`.
