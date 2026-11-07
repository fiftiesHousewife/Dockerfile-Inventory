package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;
import java.util.function.Function;

final class UnapprovedRoot implements EffectiveRule {

    private final BaseList list;

    UnapprovedRoot(BaseList list) {
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
        return List.of(new Finding(id(), severity(), image.ownBlob(), null,
                "image %s is built on %s, which is not on the approved list".formatted(image.configDigest(),
                        String.join(", ", image.rootNames())), null, builtOnRoot(image.chain())));
    }

    static List<String> builtOnRoot(List<ImageView.Group> chain) {
        return chain.size() >= 2 ? chain.get(chain.size() - 2).blobShas() : List.of();
    }

    boolean approved(String name) {
        int colon = name.lastIndexOf(':');
        boolean tagged = colon > name.lastIndexOf('/');
        return list.lookup(tagged ? name.substring(0, colon) : name, tagged ? name.substring(colon + 1) : null)
                .isPresent();
    }
}
