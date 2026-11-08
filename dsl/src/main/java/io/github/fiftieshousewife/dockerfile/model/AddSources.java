package io.github.fiftieshousewife.dockerfile.model;

import java.util.regex.Pattern;

final class AddSources {

    private static final Pattern ADDABLE = Pattern.compile(
            "(?:https?://|git@).+|.+\\.(?:tar|tgz|tar\\.(?:gz|bz2|xz|zst)|tbz2?|txz)");

    private AddSources() {
    }

    static boolean addable(String source) {
        return ADDABLE.matcher(source).matches();
    }
}
