`s2i-python-3.12-comments.Dockerfile` and `nginx-1.20-comments.Dockerfile` are trimmed from sclorg's public
`s2i-python-container` (`3.12/Dockerfile.c10s`) and `nginx-container` (`1.20/Dockerfile.c9s`): valid Dockerfiles
that rewrite-docker 8.90.4 rejects because of comment lines (an apostrophe in a comment inside a continued `RUN`; a
comment ending in a backslash before a block of comments holding `=` and `&&`).
