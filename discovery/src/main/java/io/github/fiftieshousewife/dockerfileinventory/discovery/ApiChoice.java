package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.Map;

record ApiChoice(boolean restOnly, boolean lastChanges) {

    static ApiChoice from(Map<String, String> env) {
        return new ApiChoice(restOnly(env), lastChanges(env));
    }

    static boolean restOnly(Map<String, String> env) {
        String value = env.getOrDefault("GHE_API", "").strip();
        if (!value.isEmpty() && !value.equals("auto") && !value.equals("rest")) {
            throw new IllegalStateException("GHE_API must be auto or rest, not " + value);
        }
        return value.equals("rest");
    }

    static boolean lastChanges(Map<String, String> env) {
        String value = env.getOrDefault("GHE_LAST_CHANGES", "").strip();
        if (!value.isEmpty() && !value.equals("true") && !value.equals("false")) {
            throw new IllegalStateException("GHE_LAST_CHANGES must be true or false, not " + value);
        }
        return value.equals("true");
    }
}
