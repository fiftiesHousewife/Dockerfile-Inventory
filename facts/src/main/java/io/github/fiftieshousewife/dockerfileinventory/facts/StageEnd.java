package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;

record StageEnd(String user, List<String> ports, EntrypointForm entrypoint) {

    static final StageEnd NONE = new StageEnd(null, List.of(), EntrypointForm.NONE);

    StageEnd {
        ports = List.copyOf(ports);
    }
}
