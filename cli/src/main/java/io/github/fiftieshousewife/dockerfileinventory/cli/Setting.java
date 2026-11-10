package io.github.fiftieshousewife.dockerfileinventory.cli;

// fallback: what the command uses when the setting is left out, null when it is required or has no default.
record Setting(String name, boolean required, String fallback, String description) {

    static Setting required(String name, String description) {
        return new Setting(name, true, null, description);
    }

    static Setting optional(String name, String description) {
        return new Setting(name, false, null, description);
    }

    static Setting optional(String name, String fallback, String description) {
        return new Setting(name, false, fallback, description);
    }
}
