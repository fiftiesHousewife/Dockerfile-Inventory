package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.Locale;

enum Severity {
    BREAKS, DRIFT, INFORMATION, OFF;

    String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
