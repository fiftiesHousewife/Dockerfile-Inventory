package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;

// A setting the manifest leaves out is null.
record Workload(String kind, String name, String namespace, Pod pod) {

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
}
