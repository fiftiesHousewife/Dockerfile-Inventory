package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShellTokeniserTest {

    @Test
    void splitsOneCommandIntoWords() {
        assertThat(ShellTokeniser.parse("microdnf install -y git"))
                .contains(List.of(List.of("microdnf", "install", "-y", "git")));
    }

    @Test
    void separatesCommandsOnAndOrAndSemicolon() {
        assertThat(ShellTokeniser.parse("microdnf install -y git && microdnf clean all; rm -rf /tmp/x || true"))
                .contains(List.of(
                        List.of("microdnf", "install", "-y", "git"),
                        List.of("microdnf", "clean", "all"),
                        List.of("rm", "-rf", "/tmp/x"),
                        List.of("true")));
    }

    @Test
    void groupsQuotedWordsAndRemovesTheQuotes() {
        assertThat(ShellTokeniser.parse("echo 'a b' \"c && d\" e\"f g\""))
                .contains(List.of(List.of("echo", "a b", "c && d", "ef g")));
    }

    @Test
    void joinsBackslashContinuations() {
        assertThat(ShellTokeniser.parse("microdnf install -y \\\n    git \\\n    tar && \\\n  microdnf clean all"))
                .contains(List.of(List.of("microdnf", "install", "-y", "git", "tar"), List.of("microdnf", "clean", "all")));
    }

    @Test
    void dropsLeadingAssignmentsButKeepsLaterOnes() {
        assertThat(ShellTokeniser.parse("DEBIAN_FRONTEND=noninteractive A=1 apt-get install -y --opt=x curl"))
                .contains(List.of(List.of("apt-get", "install", "-y", "--opt=x", "curl")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "if [ -f /etc/x ]; then echo y; fi",
        "case $ARCH in x86_64) echo a;; esac",
        "for p in a b; do echo $p; done",
        "while true; do sleep 1; done",
        "until false; do sleep 1; done",
        "echo $(uname -m)",
        "echo `uname -m`",
        "(cd /tmp && make)",
        "{ echo a; echo b; }",
        "install_all() { echo a; }",
        "cat <<EOF > /etc/x",
        "echo 'unterminated",
    })
    void declinesWhatItDoesNotUnderstand(String script) {
        assertThat(ShellTokeniser.parse(script)).isEmpty();
    }

    @Test
    void splitsAPipeIntoItsCommands() {
        assertThat(ShellTokeniser.parse("rpm -qa | sort"))
                .contains(List.of(List.of("rpm", "-qa"), List.of("sort")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"curl -sSL https://x.example/install | sh", "wget -qO- https://x | bash -s -- -y"})
    void declinesAPipeIntoAShell(String script) {
        assertThat(ShellTokeniser.parse(script)).isEmpty();
    }

    @Test
    void endsTheLineAtACommentButNotAtAHashInsideAWord() {
        assertThat(ShellTokeniser.parse("microdnf install -y git # tools\necho a#b"))
                .contains(List.of(List.of("microdnf", "install", "-y", "git"), List.of("echo", "a#b")));
    }

    @Test
    void dropsRedirectionsAndTheirTargets() {
        assertThat(ShellTokeniser.parse("dnf install -y git > /dev/null 2>&1 && dnf clean all >>/tmp/log 2> /tmp/err"))
                .contains(List.of(List.of("dnf", "install", "-y", "git"), List.of("dnf", "clean", "all")));
    }

    @Test
    void keepsAQuotedWordThatLooksLikeARedirection() {
        assertThat(ShellTokeniser.parse("grep \">\" /etc/hosts"))
                .contains(List.of(List.of("grep", ">", "/etc/hosts")));
    }

    @Test
    void aBackslashNewlineInsideDoubleQuotesIsRemovedAsTheShellRemovesIt() {
        String script = "INSTALL_PKGS=\"php php-fpm \\\n        tzdata\" && yum install -y $INSTALL_PKGS";

        assertThat(ShellTokeniser.assignments(script)).containsEntry("INSTALL_PKGS", "php php-fpm         tzdata");
    }

    @Test
    void anAssignmentBuiltUpFromItsEarlierValueKeepsEveryPart() {
        String script = "INSTALL_PKGS=\"rsync tar\" && INSTALL_PKGS=\"$INSTALL_PKGS pgaudit\" && "
                + "INSTALL_PKGS=\"${INSTALL_PKGS} util-linux\" && yum install -y $INSTALL_PKGS";

        assertThat(ShellTokeniser.assignments(script)).containsEntry("INSTALL_PKGS", "rsync tar pgaudit util-linux");
    }
}
