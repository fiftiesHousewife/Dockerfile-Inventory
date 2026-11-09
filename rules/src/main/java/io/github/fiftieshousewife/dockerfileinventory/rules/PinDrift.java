package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// One base and tag pinned at several digests across the estate, so the images built on it drift apart.
final class PinDrift implements CatalogueRule {

    // Examples write placeholders such as "sha256..." where a digest goes.
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");

    record Base(String name, String tag) {

        String written() {
            return tag == null ? name : name + ":" + tag;
        }
    }

    record Pin(String blobSha, String digest) {
    }

    @Override
    public String id() {
        return "R4";
    }

    @Override
    public Severity severity() {
        return Severity.LOW;
    }

    @Override
    public List<Finding> check(List<CatalogueView.Entry> estate) {
        Map<Base, Set<Pin>> pins = new LinkedHashMap<>();
        estate.forEach(entry -> entry.pinned().stream().filter(pinned -> DIGEST.matcher(pinned.digest()).matches())
                .forEach(pinned -> pins.computeIfAbsent(
                new Base(pinned.name(), pinned.tag()), base -> new HashSet<>())
                .add(new Pin(entry.blobSha(), pinned.digest()))));
        return pins.entrySet().stream()
                .filter(base -> base.getValue().stream().map(Pin::digest).distinct().count() > 1)
                .flatMap(base -> {
                    long digests = base.getValue().stream().map(Pin::digest).distinct().count();
                    long dockerfiles = base.getValue().stream().map(Pin::blobSha).distinct().count();
                    return base.getValue().stream().map(Pin::blobSha).distinct().sorted()
                            .map(sha -> new Finding(id(), severity(), sha, null, "pins " + base.getKey().written()
                                    + " at one of " + digests + " digests the estate's " + dockerfiles
                                    + " Dockerfiles pin it at", null, List.of()));
                })
                .toList();
    }
}
