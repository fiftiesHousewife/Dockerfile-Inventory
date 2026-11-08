package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;

/** A {@code chmod}, {@code chown} or {@code chgrp} in a {@code RUN}: its mode or owner, its paths, recursive or not. */
public record PermissionChange(String command, String setting, List<String> paths, boolean recursive) {

    public PermissionChange {
        paths = List.copyOf(paths);
    }
}
