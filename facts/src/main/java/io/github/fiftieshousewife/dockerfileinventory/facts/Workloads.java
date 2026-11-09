package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.yaml.YAMLMapper;

// Plain workload YAML read into workloads, document by document; a template (Go's or Helm's {{ }}, Liquid's {% %})
// is left unread, and a file that stops
// being YAML part way (a documentation fragment) gives what came before.
final class Workloads {

    private static final YAMLMapper YAML = new YAMLMapper();
    // Where each kind keeps its pod's spec.
    private static final Map<String, List<String>> POD = Map.of(
            "Pod", List.of("spec"),
            "Deployment", List.of("spec", "template", "spec"),
            "StatefulSet", List.of("spec", "template", "spec"),
            "DaemonSet", List.of("spec", "template", "spec"),
            "ReplicaSet", List.of("spec", "template", "spec"),
            "Job", List.of("spec", "template", "spec"),
            "DeploymentConfig", List.of("spec", "template", "spec"),
            "CronJob", List.of("spec", "jobTemplate", "spec", "template", "spec"));

    record Read(List<Workload> workloads, String unread) {

        Read {
            workloads = List.copyOf(workloads);
        }
    }

    private Workloads() {
    }

    static Read read(String text) {
        if (text.contains("{{") || text.contains("{%")) {
            return new Read(List.of(), "a template ({{ }} or {% %}), not plain YAML");
        }
        List<Workload> workloads = new ArrayList<>();
        try (MappingIterator<JsonNode> documents = YAML.readerFor(JsonNode.class).readValues(text)) {
            while (documents.hasNext()) {
                JsonNode document = documents.next();
                if (document != null && POD.containsKey(document.path("kind").asString(""))) {
                    workloads.add(workload(document));
                }
            }
        } catch (JacksonException e) {
            if (workloads.isEmpty()) {
                return new Read(List.of(), "not YAML: " + e.getOriginalMessage().lines().findFirst().orElse(""));
            }
        }
        return new Read(workloads, null);
    }

    static Workload workload(JsonNode document) {
        String kind = document.path("kind").asString();
        JsonNode spec = document;
        for (String step : POD.get(kind)) {
            spec = spec.path(step);
        }
        JsonNode metadata = document.path("metadata");
        return new Workload(kind, metadata.path("name").stringValue(null), metadata.path("namespace").stringValue(null),
                pod(spec));
    }

    static Workload.Pod pod(JsonNode spec) {
        List<Workload.Container> containers = new ArrayList<>();
        spec.path("initContainers").forEach(c -> containers.add(container(c, true)));
        spec.path("containers").forEach(c -> containers.add(container(c, false)));
        List<String> hostPaths = StreamSupport.stream(spec.path("volumes").spliterator(), false)
                .map(volume -> volume.path("hostPath").path("path")).filter(JsonNode::isString)
                .map(JsonNode::asString).toList();
        return new Workload.Pod(spec.path("hostNetwork").asBoolean(false), spec.path("hostPID").asBoolean(false),
                spec.path("hostIPC").asBoolean(false), hostPaths, security(spec.path("securityContext")), containers);
    }

    static Workload.Container container(JsonNode container, boolean init) {
        List<Integer> ports = new ArrayList<>();
        List<Integer> hostPorts = new ArrayList<>();
        container.path("ports").forEach(port -> {
            if (port.path("containerPort").isNumber()) {
                ports.add(port.path("containerPort").asInt());
            }
            if (port.path("hostPort").isNumber()) {
                hostPorts.add(port.path("hostPort").asInt());
            }
        });
        List<String> mounts = StreamSupport.stream(container.path("volumeMounts").spliterator(), false)
                .map(mount -> mount.path("mountPath").asString("")).filter(path -> !path.isEmpty()).toList();
        return new Workload.Container(container.path("name").stringValue(null),
                container.path("image").stringValue(null), init, ports, hostPorts, new Workload.Probes(
                container.has("livenessProbe"), container.has("readinessProbe"), container.has("startupProbe")),
                mounts, security(container.path("securityContext")));
    }

    static Workload.Security security(JsonNode context) {
        return new Workload.Security(
                context.path("privileged").isBoolean() ? context.path("privileged").asBoolean() : null,
                context.path("runAsUser").isNumber() ? context.path("runAsUser").asLong() : null,
                context.path("runAsNonRoot").isBoolean() ? context.path("runAsNonRoot").asBoolean() : null,
                context.path("allowPrivilegeEscalation").isBoolean()
                        ? context.path("allowPrivilegeEscalation").asBoolean() : null,
                StreamSupport.stream(context.path("capabilities").path("add").spliterator(), false)
                        .map(JsonNode::asString).toList(),
                context.path("seccompProfile").path("type").stringValue(null));
    }
}
