package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;
import java.util.function.Function;

/** S4: the image cannot run as the arbitrary non-root UID OpenShift assigns (Q3). */
public final class ArbitraryUid implements EffectiveRule {

    @Override
    public String id() {
        return "S4";
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public List<Finding> check(ImageView image, Function<String, String> blobText) {
        String kind = kind(image.user());
        if (!kind.equals("numeric")) {
            List<String> setBy = image.userSetBy() == null ? List.of() : image.userSetBy().blobShas();
            return List.of(finding(image, "image %s runs as %s user %s".formatted(image.configDigest(), kind,
                    image.user() == null ? "root (none set)" : image.user()), setBy));
        }
        return List.of();
    }

    private Finding finding(ImageView image, String message, List<String> contributing) {
        List<String> own = image.chain().isEmpty() ? List.of() : image.chain().getFirst().blobShas();
        return new Finding(id(), severity(), own.isEmpty() ? null : own.getFirst(), null, message, null,
                contributing);
    }

    /** The user part of {@code user:group}: none, {@code root} or {@code 0} is root, as the container runs it. */
    static String kind(String user) {
        String name = user == null ? "" : user.split(":", 2)[0].strip();
        if (name.isEmpty() || name.equals("root") || name.equals("0")) {
            return "root";
        }
        if (name.contains("$")) {
            return "unresolved";
        }
        return name.chars().allMatch(Character::isDigit) ? "numeric" : "named";
    }
}
