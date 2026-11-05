package io.github.fiftieshousewife.dockerfileinventory.facts;

/** How the image's {@code ENTRYPOINT} is written. Shell form wraps the process in {@code /bin/sh -c}, which eats signals. */
public enum EntrypointForm {
    SHELL,
    EXEC,
    NONE
}
