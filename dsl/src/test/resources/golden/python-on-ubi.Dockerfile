FROM registry.access.redhat.com/ubi9/python-311:1-77.1726664316@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
ARG REVISION
ARG SOURCE
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.source="${SOURCE}"
USER 0
RUN dnf remove sqlite3 -y \
    && wget https://www.sqlite.org/2023/sqlite-autoconf-3410200.tar.gz \
    && tar -xvzf sqlite-autoconf-3410200.tar.gz \
    && cd sqlite-autoconf-3410200 \
    && ./configure \
    && make \
    && make install \
    && mv /usr/local/bin/sqlite3 /usr/bin/sqlite3 \
    && cd .. \
    && rm -rf sqlite-autoconf-3410200 sqlite-autoconf-3410200.tar.gz
ENV LD_LIBRARY_PATH="/usr/local/lib"
WORKDIR /rag
COPY --chown=1001:0 requirements.txt .
RUN pip install --upgrade pip \
    && pip install --no-cache-dir --upgrade -r /rag/requirements.txt \
    && mkdir -p /rag/models/
COPY --chown=1001:0 rag_app.py manage_vectordb.py .
EXPOSE 8501
ENV HF_HUB_CACHE="/rag/models/"
RUN chgrp -R 0 /rag/models/ \
    && chmod -R g=u /rag/models/
USER 1001
ENTRYPOINT ["streamlit", "run", "rag_app.py"]
