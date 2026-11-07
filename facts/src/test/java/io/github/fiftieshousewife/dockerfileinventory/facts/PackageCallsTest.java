package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PackageCallsTest {

    private static List<PackageCall> calls(String script) {
        return PackageCalls.of(ShellTokeniser.parse(script).orElseThrow(), Map.of());
    }

    @Test
    void readsAMicrodnfInstall() {
        assertThat(calls("microdnf install -y git tar")).containsExactly(
                new PackageCall("microdnf", "install", List.of("-y"), List.of("git", "tar"), false));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "dnf install -y git              | dnf      | install | -y",
        "yum -y install git              | yum      | install | -y",
        "apk --no-cache add git          | apk      | add     | --no-cache",
        "apt-get install -y git          | apt-get  | install | -y",
        "apt install --no-install-recommends git | apt | install | --no-install-recommends",
        "microdnf -y install git         | microdnf | install | -y",
    })
    void readsEachManagerWithFlagsBeforeOrAfterTheSubcommand(String script, String manager, String subcommand,
            String flag) {
        assertThat(calls(script)).containsExactly(new PackageCall(manager, subcommand, List.of(flag), List.of("git"),
                manager.equals("apk")));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "microdnf install -y git && microdnf clean all                         | true",
        "dnf install -y git && dnf clean all                                   | true",
        "yum install -y git && rm -rf /var/cache/yum                           | true",
        "dnf install -y git && rm -rf /var/cache/{dnf,yum}                     | true",
        "microdnf install -y git && rm -rf /var/cache/dnf /var/cache/yum       | true",
        "apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/* | true",
        "microdnf clean all && microdnf install -y git                         | false",
        "microdnf install -y git && rm -rf /tmp/build                          | false",
        "apt-get install -y git && dnf clean all                               | false",
    })
    void notesWhetherTheCacheIsCleanedLaterInTheSameRun(String script, boolean cleaned) {
        assertThat(calls(script)).filteredOn(c -> c.subcommand().equals("install"))
                .singleElement().extracting(PackageCall::cleanedInSameRun).isEqualTo(cleaned);
    }

    @Test
    void recordsUpdatesWithoutPackages() {
        assertThat(calls("microdnf update -y openssl && apt-get update")).containsExactly(
                new PackageCall("microdnf", "update", List.of("-y"), List.of(), false),
                new PackageCall("apt-get", "update", List.of(), List.of(), false));
    }

    @Test
    void ignoresOtherInstallers() {
        assertThat(calls("pip install requests && npm install -g yarn")).isEmpty();
    }

    @Test
    void aPackageListFromACommandSubstitutionIsNotCountedOrGuessed() {
        assertThat(calls("dnf install -y git $(cat /tmp/pkgs.txt) `cat more.txt` \"$(echo x)\""))
                .containsExactly(new PackageCall("dnf", "install", List.of("-y"), List.of("git"), false));
    }

    @Test
    void aVariableHoldingASubstitutionIsNotSplitIntoPackages() {
        assertThat(PackageCalls.of(ShellTokeniser.parse("dnf install -y $PKGS").orElseThrow(),
                Map.of("PKGS", "$(cat /tmp/pkgs.txt)")))
                .containsExactly(new PackageCall("dnf", "install", List.of("-y"), List.of("$PKGS"), false));
    }

    @Test
    void minidebsInstallPackagesIsAnAptGetInstallThatCleansItsCache() {
        assertThat(calls("install_packages ca-certificates curl && uninstall_packages curl")).containsExactly(
                new PackageCall("apt-get", "install", List.of(), List.of("ca-certificates", "curl"), true),
                new PackageCall("apt-get", "remove", List.of(), List.of(), false));
    }

    @Test
    void expandedWordSplitsAKnownVariablesUnquotedValueIntoWords() {
        assertThat(PackageCalls.expandedWord("${PKGS}", Map.of("PKGS", " \"git \\\n  tar\" ")))
                .containsExactly("git", "tar");
    }

    @Test
    void expandedWordKeepsAnUnknownVariableOrAWordThatIsNotOnlyAReference() {
        assertThat(PackageCalls.expandedWord("$NOPE", Map.of())).containsExactly("$NOPE");
        assertThat(PackageCalls.expandedWord("a-$PKGS", Map.of("PKGS", "git"))).containsExactly("a-$PKGS");
    }

    @Test
    void packagesAreTheWordsAfterAnInstallSubcommandWithoutSubstitutions() {
        assertThat(PackageCalls.packages("install", List.of("install", "git", "$(cat x)"))).containsExactly("git");
        assertThat(PackageCalls.packages("update", List.of("update", "git"))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "dnf clean all              | RPM | true",
        "apt-get clean              | APT | false",
        "rm -rf /var/lib/apt/lists  | APT | true",
        "rm -rf /var/cache/apk      | RPM | false",
        "yum clean all              | APK | false",
    })
    void cleansIsTheFamilysOwnCleanOrRemovingItsCacheDirectory(String command, PackageCalls.Family family,
            boolean cleans) {
        assertThat(PackageCalls.cleans(List.of(command.split(" ")), family)).isEqualTo(cleans);
    }

    @Test
    void managerCallSeparatesFlagsFromTheSubcommandAndPackages() {
        assertThat(PackageCalls.managerCall(List.of("apk", "--no-cache", "add", "git"), List.of()))
                .isEqualTo(new PackageCall("apk", "add", List.of("--no-cache"), List.of("git"), true));
    }

    @Test
    void callUnwrapsAWrapperIntoTheManagersCommand() {
        assertThat(PackageCalls.call(List.of("uninstall_packages", "curl"),
                List.of(List.of("rm", "-rf", "/var/lib/apt/lists/x"))))
                .isEqualTo(new PackageCall("apt-get", "remove", List.of(), List.of(), true));
    }
}
