package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CredentialCopiedTest {

    @Test
    void s7ReportsAKeyOrCredentialFileTheFinalImageCopiesIn() {
        assertThat(new CredentialCopied().check(Subjects.of("""
                FROM golang AS build
                COPY .netrc /root/.netrc
                FROM ubi9
                COPY ./ssh/id_rsa ./ssh/id_rsa.pub /home/app/.ssh/
                COPY certs/ca.pem /etc/pki/ca-trust/source/anchors/
                COPY .pypirc setup.py /app/
                """))).extracting(Finding::line, Finding::message).containsExactly(
                        tuple(4, "COPY puts ./ssh/id_rsa, named like a key or credential, into the image"),
                        tuple(6, "COPY puts .pypirc, named like a key or credential, into the image"));
    }

    @Test
    void s7IgnoresAFileThatDidNotParse() {
        assertThat(new CredentialCopied().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"id_rsa, true", "keys/id_ed25519, true", "tls.key, true", "store.p12, true", "app.jks, true",
        ".git-credentials, true", ".pgpass, true", ".pypirc, true", "kubeconfig, true", ".yarnrc.yml, false",
        ".npmrc, false",
        "id_rsa.pub, false", "ca.pem, false", "config.json, false", "RPM-GPG-KEY-redhat, false", "key.go, false"})
    void aCredentialIsAPrivateKeyOrAFileThatHoldsLogins(String source, boolean credential) {
        assertThat(CredentialCopied.credential(source)).isEqualTo(credential);
    }
}
