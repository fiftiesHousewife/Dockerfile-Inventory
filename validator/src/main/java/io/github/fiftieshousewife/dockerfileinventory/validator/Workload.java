package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.nio.file.Path;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

record Workload(String blobSha, String kind, String name, String namespace, Pod pod, List<Location> locations) {

    private static final ObjectMapper JSON = new ObjectMapper();

    Workload {
        locations = List.copyOf(locations);
    }

    record Pod(boolean hostNetwork, boolean hostPid, boolean hostIpc, List<String> hostPaths, Security security,
               List<Container> containers) {

        Pod {
            hostPaths = List.copyOf(hostPaths);
            containers = List.copyOf(containers);
        }
    }

    record Container(String name, String image, boolean init, List<Integer> ports, List<Integer> hostPorts,
                     Probes probes, List<String> mountPaths, Security security) {

        Container {
            ports = List.copyOf(ports);
            hostPorts = List.copyOf(hostPorts);
            mountPaths = List.copyOf(mountPaths);
        }
    }

    record Probes(boolean liveness, boolean readiness, boolean startup) {
    }

    record Security(Boolean privileged, Long runAsUser, Boolean runAsNonRoot, Boolean allowPrivilegeEscalation,
                    List<String> addedCapabilities, String seccomp) {

        Security {
            addedCapabilities = List.copyOf(addedCapabilities);
        }
    }

    static List<Workload> read(Path file) {
        return JsonLines.read(file).stream().map(row -> JSON.treeToValue(row, Workload.class)).toList();
    }
}
