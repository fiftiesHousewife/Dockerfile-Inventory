package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

record RuleSet(List<Rule> perFile, List<EffectiveRule> effective, List<CatalogueRule> catalogue,
               List<String> skipped) {

    RuleSet {
        perFile = List.copyOf(perFile);
        effective = List.copyOf(effective);
        catalogue = List.copyOf(catalogue);
        skipped = List.copyOf(skipped);
    }

    static RuleSet of(BaseList allowed, BaseList endOfLife, boolean lineage, boolean catalogue) {
        return new RuleSet(perFile(allowed, endOfLife), effective(allowed, lineage),
                catalogue ? List.of(new PinDrift()) : List.of(), skipped(allowed, endOfLife, lineage, catalogue));
    }

    static List<Rule> perFile(BaseList allowed, BaseList endOfLife) {
        List<Rule> perFile = new ArrayList<>(List.of(new UnpinnedBase(), new UncleanedInstall(),
                new ShellEntrypoint(), new UnversionedPackages(), new Unparseable(), new SecretValue(),
                new PipeToShell(), new AdjacentRuns(), new MissingOciLabels(), new WorldWritable(),
                new SystemUpgrade(), new UnverifiedDownload(), new PlainAdd(),
                new ChangedAfterCopy(), new ChangedAfterVolume(), new RemovedLater(),
                new CredentialCopied(), new BuildOnlyEnv()));
        if (allowed != null) {
            perFile.add(new UnapprovedBase(allowed));
        }
        if (endOfLife != null) {
            perFile.add(new EndOfLifeBase(endOfLife));
        }
        return perFile;
    }

    static List<EffectiveRule> effective(BaseList allowed, boolean lineage) {
        if (!lineage) {
            return List.of();
        }
        return allowed == null ? List.of(new ArbitraryUid()) : List.of(new ArbitraryUid(), new UnapprovedRoot(allowed));
    }

    static List<String> skipped(BaseList allowed, BaseList endOfLife, boolean lineage, boolean catalogue) {
        return Stream.of(allowed == null ? "S1" : null, endOfLife == null ? "R2" : null, lineage ? null : "S4",
                catalogue ? null : "R4").filter(Objects::nonNull).toList();
    }
}
