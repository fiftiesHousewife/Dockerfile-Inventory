package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SystemUpgradeTest {

    @Test
    void h4ReportsEveryPackageUpgradedInTheBuildButNotAnIndexRefreshOrANamedPackage() {
        assertThat(new SystemUpgrade().check(Subjects.of("""
                FROM debian AS build
                RUN apt-get update && apt-get -y upgrade && apt-get install -y make
                FROM ubi9
                RUN dnf -y update && dnf -y install python3 && dnf clean all
                RUN yum update -y openssl && apk update
                """))).extracting(Finding::line, Finding::message).containsExactly(
                        tuple(2, "apt-get upgrade upgrades every package the base has"),
                        tuple(4, "dnf update upgrades every package the base has"));
    }

    @Test
    void h4IgnoresAFileThatDidNotParse() {
        assertThat(new SystemUpgrade().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiterString = " => ", value = {
        "apt-get -y upgrade => apt-get upgrade", "sudo dnf -y update => dnf update",
        "microdnf upgrade => microdnf upgrade", "apk upgrade --no-cache => apk upgrade", "apt-get update => ",
        "yum update -y openssl => ",
        "dnf install -y make => ", "pip install -U pip => "})
    void anUpgradeIsTheManagersUpgradeSubcommandWithNoPackageNamed(String command, String upgrade) {
        assertThat(SystemUpgrade.upgrade(List.of(command.split(" "))).orElse(null)).isEqualTo(upgrade);
    }
}
