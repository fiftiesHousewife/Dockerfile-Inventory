Lineage output from a real run (`scripts/lineage-demo.sh`, 6 Nov): CentOS Stream 9 mirrored into a local JCR, and
four sclorg images built with Buildah from the Dockerfiles discovery found at their scanned commits:
`s2i-core-c9s` on CentOS, `s2i-base-c9s` and nginx 1.24 on `s2i-core-c9s`, Python 3.12 on `s2i-base-c9s`. Every
parent is right; nginx links to four Dockerfiles that differ only in `ENV` values. Unedited. `configs/` holds the
same run's image configurations, copied unedited from its output on 8 Nov.
