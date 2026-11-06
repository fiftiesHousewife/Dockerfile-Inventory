package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.function.Function;

/** S4: the image cannot run as the arbitrary non-root UID OpenShift assigns (Q3). */
public final class ArbitraryUid implements EffectiveRule {

    /**
     * Steps that let an arbitrary UID in group 0 write: {@code chgrp 0}, {@code chmod g=u} or {@code g+w}/{@code g+rwX},
     * or a {@code COPY}/{@code ADD}/{@code chown} to group 0.
     */
    private static final Pattern GROUP_ZERO = Pattern.compile(
            "\\bchgrp\\s+(-\\S+\\s+)*0\\b"
                    + "|\\bchmod\\s+(-\\S+\\s+)*\\S*g(=u|\\+\\w*w)"
                    + "|--chown=\\S*:0\\b"
                    + "|\\bchown\\s+(-\\S+\\s+)*\\S*:0\\b");

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
        boolean groupZero = image.chain().stream()
                .flatMap(g -> g.blobShas().stream())
                .map(blobText)
                .filter(Objects::nonNull)
                .map(PipeToShell::joined)
                .anyMatch(text -> GROUP_ZERO.matcher(text).find());
        if (groupZero) {
            return List.of();
        }
        List<String> own = image.chain().isEmpty() ? List.of() : image.chain().getFirst().blobShas();
        return List.of(finding(image, "image %s runs as %s, but no Dockerfile in its chain makes paths writable "
                .formatted(image.configDigest(), image.user()) + "by group 0", own));
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
