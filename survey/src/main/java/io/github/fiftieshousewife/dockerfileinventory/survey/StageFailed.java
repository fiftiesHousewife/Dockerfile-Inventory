package io.github.fiftieshousewife.dockerfileinventory.survey;

import java.util.Optional;

final class StageFailed extends Exception {

    private static final long serialVersionUID = 1L;

    StageFailed(String stage, Exception cause) {
        super(stage + " failed: " + Optional.ofNullable(cause.getMessage())
                .flatMap(message -> message.lines().findFirst()).orElse(cause.getClass().getSimpleName()), cause);
    }

    String explained() {
        return getMessage() + (getMessage().contains(" returned 401")
                ? "; GitHub refused the token: give one that can read the organisation as GHE_TOKEN or GHE_TOKEN_FILE"
                + " (with the GitHub CLI, `env -u GITHUB_TOKEN gh auth token`, so a GITHUB_TOKEN set elsewhere is not"
                + " taken instead)" : "");
    }
}
