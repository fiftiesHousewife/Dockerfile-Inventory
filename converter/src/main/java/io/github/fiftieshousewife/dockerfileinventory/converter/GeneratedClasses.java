package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.reader.Conversion;
import io.github.fiftieshousewife.dockerfile.model.ImageBuilder;
import io.github.fiftieshousewife.dockerfile.model.Policy;
import io.github.fiftieshousewife.dockerfile.model.PolicyViolation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class GeneratedClasses {

    private final Set<String> optedIn;
    private final Map<String, List<Location>> locations;
    private final Policy policy;
    private final boolean approvedBasesListed;
    private final RunFiles files;

    GeneratedClasses(Set<String> optedIn, Map<String, List<Location>> locations, Policy policy,
                     boolean approvedBasesListed, RunFiles files) {
        this.optedIn = Set.copyOf(optedIn);
        this.locations = Map.copyOf(locations);
        this.policy = policy;
        this.approvedBasesListed = approvedBasesListed;
        this.files = files;
    }

    void generate(String blobSha, Conversion conversion) {
        List<PolicyViolation.Breach> breaches = breaches(conversion);
        locations.getOrDefault(blobSha, List.of()).stream()
                .filter(location -> optedIn.contains(location.repository()))
                .forEach(location -> write(location, conversion, breaches));
    }

    List<PolicyViolation.Breach> breaches(Conversion conversion) {
        return ImageBuilder.of(conversion.model()).check(policy).stream()
                .filter(breach -> approvedBasesListed || !breach.rule().equals("S1"))
                .toList();
    }

    static Set<String> optedIn(String text) {
        return text.lines().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toSet());
    }

    private void write(Location location, Conversion conversion, List<PolicyViolation.Breach> breaches) {
        String packageName = ClassNames.packageName(location);
        String className = ClassNames.className(location);
        files.write("generated/" + packageName.replace('.', '/') + "/" + className + ".java",
                JavaEmitter.emit(packageName, className, conversion, breaches));
    }
}
