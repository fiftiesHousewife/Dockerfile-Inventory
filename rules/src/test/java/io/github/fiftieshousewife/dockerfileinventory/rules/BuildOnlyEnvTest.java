package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class BuildOnlyEnvTest {

    @Test
    void h9ReportsDebianFrontendSetForEveryContainerInTheFinalImage() {
        assertThat(new BuildOnlyEnv().check(Subjects.of("""
                FROM debian AS build
                ENV DEBIAN_FRONTEND=noninteractive
                FROM debian
                ARG DEBIAN_FRONTEND=noninteractive
                ENV LANG=C.UTF-8 DEBIAN_FRONTEND=noninteractive
                ENV DEBIAN_FRONTEND noninteractive
                """))).extracting(Finding::line, Finding::message).containsExactly(
                        tuple(5, "ENV DEBIAN_FRONTEND, a setting for the build, stays set in every container"),
                        tuple(6, "ENV DEBIAN_FRONTEND, a setting for the build, stays set in every container"));
    }

    @Test
    void h9IgnoresAFileThatDidNotParse() {
        assertThat(new BuildOnlyEnv().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @Test
    void anEnvSetsTheNamesOfItsPairsOrTheOneNameOfTheOlderForm() {
        assertAll(
                () -> assertThat(BuildOnlyEnv.names("ENV B=\"x y\" A=1")).containsExactly("A", "B"),
                () -> assertThat(BuildOnlyEnv.names("ENV PATH /usr/bin:/bin")).containsExactly("PATH"));
    }
}
