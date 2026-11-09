package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

// OpenShift replaces the image's user with an arbitrary UID in group 0.
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
        return image.facts().finalUser() == null && image.effectiveUser() == null ? Certainty.INFERRED : Certainty.READ;
    }

    @Override
    public List<String> problems(Subject image, Profile profile) {
        EffectiveUser effective = image.effectiveUser();
        return effective == null ? written(image) : effective(effective);
    }

    static List<String> effective(EffectiveUser effective) {
        String setBy = effective.setBy() == null ? "" : ", set by " + effective.setBy();
        return switch (effective.kind()) {
            case ROOT -> List.of("its registry images run as root" + setBy + "; " + ARBITRARY);
            case NAMED -> List.of("its registry images run as the named user " + effective.user() + setBy
                    + "; OpenShift runs an arbitrary UID with no such name");
            case NUMERIC -> List.of();
        };
    }

    static List<String> written(Subject image) {
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
