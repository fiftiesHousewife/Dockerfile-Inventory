package io.github.fiftieshousewife.dockerfileinventory.cli;

// fallback: what the command uses when the setting is left out; requiredWith: a setting that, once set, makes this one
// required too; requiredUnless: a setting that, once set, stands in for this one, which is required otherwise.
record Setting(String name, boolean required, String fallback, String requiredWith, String requiredUnless,
               String description) {

    static Setting required(String name, String description) {
        return new Setting(name, true, null, null, null, description);
    }

    static Setting requiredWith(String name, String other, String description) {
        return new Setting(name, false, null, other, null, description);
    }

    static Setting requiredUnless(String name, String other, String description) {
        return new Setting(name, false, null, null, other, description);
    }

    static Setting optional(String name, String description) {
        return new Setting(name, false, null, null, null, description);
    }

    static Setting optional(String name, String fallback, String description) {
        return new Setting(name, false, fallback, null, null, description);
    }
}
