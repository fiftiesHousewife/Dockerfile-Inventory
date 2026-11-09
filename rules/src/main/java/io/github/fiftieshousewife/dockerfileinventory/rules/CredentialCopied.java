package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.openrewrite.Cursor;

// Judged by the file's name only: nothing is read from it. .npmrc and .yarnrc.yml are left out: they mostly hold
// settings, and a token only sometimes.
final class CredentialCopied implements Rule {

    private static final Pattern CREDENTIAL = Pattern.compile("id_(rsa|dsa|ecdsa|ed25519)|.*\\.(key|p12|pfx|jks"
            + "|keystore)|\\.netrc|\\.git-credentials|\\.pgpass|\\.pypirc|kubeconfig"
            + "|\\.dockercfg", Pattern.CASE_INSENSITIVE);

    @Override
    public String id() {
        return "S7";
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public List<Finding> check(Subject subject) {
        if (!subject.isParsed()) {
            return List.of();
        }
        Lines lines = Lines.of(subject.file());
        Cursor cursor = new Cursor(null, subject.file());
        return FinalImage.instructions(subject.file()).stream()
                .filter(i -> i instanceof Docker.Copy || i instanceof Docker.Add)
                .flatMap(copy -> {
                    String line = Continuations.oneLine(copy.printTrimmed(cursor));
                    String keyword = line.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
                    return AddSources.of(line).sources().stream().filter(CredentialCopied::credential)
                            .map(source -> finding(subject, lines.of(copy), keyword + " puts " + source
                                    + ", named like a key or credential, into the image"));
                })
                .toList();
    }

    static boolean credential(String source) {
        String path = source.endsWith("/") ? source.substring(0, source.length() - 1) : source;
        return CREDENTIAL.matcher(path.substring(path.lastIndexOf('/') + 1)).matches();
    }
}
