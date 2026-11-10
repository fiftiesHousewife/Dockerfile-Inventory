package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

record ContainerLink(String name, String image, List<String> dockerfiles, boolean floating, String unlinked) {

    ContainerLink {
        dockerfiles = List.copyOf(dockerfiles);
    }

    static ContainerLink unlinked(Workload.Container container, String why) {
        return new ContainerLink(container.name(), container.image(), List.of(), false, why);
    }
}
