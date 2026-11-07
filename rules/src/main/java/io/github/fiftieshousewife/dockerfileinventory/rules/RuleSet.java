package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.ArrayList;
import java.util.List;

record RuleSet(List<Rule> perFile, List<EffectiveRule> effective, List<String> skipped) {

    RuleSet {
        perFile = List.copyOf(perFile);
        effective = List.copyOf(effective);
        skipped = List.copyOf(skipped);
    }

    static RuleSet of(BaseList allowed, BaseList endOfLife, boolean lineage) {
        List<Rule> perFile = new ArrayList<>(List.of(new UnpinnedBase(), new UncleanedInstall(),
                new ShellEntrypoint(), new UnversionedPackages(), new Unparseable(), new SecretValue(),
                new PipeToShell(), new AdjacentRuns(), new MissingOciLabels()));
        List<EffectiveRule> effective = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        if (allowed != null) {
            perFile.add(new UnapprovedBase(allowed));
        } else {
            skipped.add("S1");
        }
        if (endOfLife != null) {
            perFile.add(new EndOfLifeBase(endOfLife));
        } else {
            skipped.add("R2");
        }
        if (lineage) {
            effective.add(new ArbitraryUid());
            if (allowed != null) {
                effective.add(new UnapprovedRoot(allowed));
            }
        } else {
            skipped.add("S4");
        }
        return new RuleSet(perFile, effective, skipped);
    }
}
