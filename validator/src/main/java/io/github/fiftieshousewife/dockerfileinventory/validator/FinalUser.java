package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

// P1: OpenShift replaces the image's user with an arbitrary UID in group 0.
final class FinalUser implements PlatformCheck {

    private static final String ARBITRARY = "OpenShift runs it as an arbitrary UID in group 0, without root's rights";

    @Override
    public Check id() {
        return Check.P1;
    }

    @Override
    public Certainty certainty() {
        return Certainty.READ;
    }

    @Override
    public Certainty certainty(Subject image) {
        return image.facts().finalUser() == null ? Certainty.INFERRED : Certainty.READ;
    }

    @Override
    public List<String> problems(Subject image, Profile profile) {
        String user = image.facts().finalUser();
        return switch (image.facts().userKind()) {
            case "ROOT" -> List.of(user == null ? "the image sets no user, so it runs as its base's, root unless "
                    + "the base sets one; " + ARBITRARY
                    : "the image runs as root; " + ARBITRARY);
            case "NAMED" -> List.of("the image runs as the named user " + user
                    + "; OpenShift runs an arbitrary UID with no such name");
            default -> List.of();
        };
    }
}
