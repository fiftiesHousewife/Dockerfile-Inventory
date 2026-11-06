package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;
import java.util.function.Function;

/** S1, effective: the image's chain starts from a base that is not approved, however many bases lie between. */
public final class UnapprovedRoot implements EffectiveRule {

    private final BaseList list;

    public UnapprovedRoot(BaseList list) {
        this.list = list;
    }

    @Override
    public String id() {
        return "S1";
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public List<Finding> check(ImageView image, Function<String, String> blobText) {
        if (image.rootNames().isEmpty() || image.rootNames().stream().anyMatch(this::approved)) {
            return List.of();
        }
        List<ImageView.Group> chain = image.chain();
        List<String> builtOnRoot = chain.size() >= 2 ? chain.get(chain.size() - 2).blobShas() : List.of();
        List<String> own = chain.isEmpty() ? List.of() : chain.getFirst().blobShas();
        return List.of(new Finding(id(), severity(), own.isEmpty() ? null : own.getFirst(), null,
                "image %s is built on %s, which is not on the approved list".formatted(image.configDigest(),
                        String.join(", ", image.rootNames())), null, builtOnRoot));
    }

    /** A root name is {@code path:tag}, as Artifactory names the image, without the repository. */
    private boolean approved(String name) {
        int colon = name.lastIndexOf(':');
        boolean tagged = colon > name.lastIndexOf('/');
        return list.lookup(tagged ? name.substring(0, colon) : name, tagged ? name.substring(colon + 1) : null)
                .isPresent();
    }
}
