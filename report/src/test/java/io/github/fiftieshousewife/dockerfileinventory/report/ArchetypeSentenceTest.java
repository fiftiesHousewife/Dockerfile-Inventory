package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ArchetypeSentenceTest {

    @Test
    void aSignatureReadsAsASentenceNamingTheFirstPackagesAndCountingTheRest() {
        assertThat(ArchetypeSentence.of(
                "s2i-base-c9s:|yum|hostname,mod_ssl,php,php-fpm,php-gd,tzdata|NUMERIC|8080,8443|EXEC|stages=1",
                "quay.io/sclorg/s2i-base-c9s:c9s"))
                .isEqualTo("Builds on quay.io/sclorg/s2i-base-c9s:c9s, installs 6 packages with yum (hostname, "
                        + "mod_ssl, php, php-fpm and 2 more), runs as a numeric user, exposes 8080 and 8443, "
                        + "starts with an exec-form command, in one stage.");
    }

    @Test
    void noPackagesRootNoPortsShellFormAndSeveralStagesReadPlainlyToo() {
        assertThat(ArchetypeSentence.of("golang:1|apt-get|jq|ROOT||SHELL|stages=2", "golang:1.22"))
                .isEqualTo("Builds on golang:1.22, installs 1 package with apt-get (jq), runs as root, exposes no "
                        + "ports, starts with a shell-form command, in 2 stages.");
        assertThat(ArchetypeSentence.of("scratch:|||UNRESOLVED||NONE|stages=1", "scratch"))
                .isEqualTo("Builds on scratch, installs no packages, runs as a user set by a variable, exposes no "
                        + "ports, sets no start command of its own, in one stage.");
    }

    @Test
    void templatesAndUnreadableFilesHaveTheirOwnSentence() {
        assertThat(ArchetypeSentence.of("templates", null))
                .isEqualTo("Templates saved under a Dockerfile's name (Jinja and similar), not Dockerfiles.");
        assertThat(ArchetypeSentence.of("parse-failures", null))
                .isEqualTo("Dockerfiles the parser could not read.");
    }

    @Test
    void installsNamesTheManagersJoinedWithAnd() {
        assertThat(ArchetypeSentence.installs("apt-get,pip", "a,b"))
                .isEqualTo("installs 2 packages with apt-get and pip (a, b)");
        assertThat(ArchetypeSentence.installs("", "")).isEqualTo("installs no packages");
    }

    @Test
    void eachUserKindReadsPlainly() {
        assertThat(ArchetypeSentence.user("ROOT")).isEqualTo("runs as root");
        assertThat(ArchetypeSentence.user("NUMERIC")).isEqualTo("runs as a numeric user");
        assertThat(ArchetypeSentence.user("NAMED")).isEqualTo("runs as a named user");
        assertThat(ArchetypeSentence.user("UNRESOLVED")).isEqualTo("runs as a user set by a variable");
    }

    @Test
    void portsAreJoinedWithAnd() {
        assertThat(ArchetypeSentence.ports("80,443")).isEqualTo("exposes 80 and 443");
        assertThat(ArchetypeSentence.ports("")).isEqualTo("exposes no ports");
    }

    @Test
    void eachEntrypointFormReadsPlainly() {
        assertThat(ArchetypeSentence.entrypoint("EXEC")).isEqualTo("starts with an exec-form command");
        assertThat(ArchetypeSentence.entrypoint("SHELL")).isEqualTo("starts with a shell-form command");
        assertThat(ArchetypeSentence.entrypoint("NONE")).isEqualTo("sets no start command of its own");
    }

    @Test
    void oneStageIsWordedAndMoreAreCounted() {
        assertThat(ArchetypeSentence.stages("stages=1")).isEqualTo("in one stage");
        assertThat(ArchetypeSentence.stages("stages=3")).isEqualTo("in 3 stages");
    }
}
