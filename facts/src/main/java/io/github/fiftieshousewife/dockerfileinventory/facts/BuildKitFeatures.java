package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.fiftieshousewife.dockerfile.parser.DockerIsoVisitor;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class BuildKitFeatures {

    private static final Pattern DIRECTIVE = Pattern.compile("#\\s*(\\w+)\\s*=.*");

    private BuildKitFeatures() {
    }

    static Set<String> of(Docker.File file) {
        Set<String> features = new TreeSet<>();
        new DockerIsoVisitor<Integer>() {
            @Override
            public Docker.HeredocForm visitHeredocForm(Docker.HeredocForm heredoc, Integer p) {
                features.add("heredoc");
                return heredoc;
            }

            @Override
            public Docker.Run visitRun(Docker.Run run, Integer p) {
                if (mounts(run.getFlags())) {
                    features.add("run-mount");
                }
                return super.visitRun(run, p);
            }
        }.visit(file, 0);
        if (hasSyntaxDirective(file.printAll())) {
            features.add("syntax-directive");
        }
        return features;
    }

    static boolean mounts(List<Docker.Flag> flags) {
        return flags != null && flags.stream().anyMatch(f -> f.getName().replace("-", "").equals("mount"));
    }

    // Docker reads parser directives only from the leading # key=value lines, before any other line.
    static boolean hasSyntaxDirective(String text) {
        return text.lines().map(DIRECTIVE::matcher).takeWhile(Matcher::matches)
                .anyMatch(directive -> directive.group(1).equalsIgnoreCase("syntax"));
    }
}
