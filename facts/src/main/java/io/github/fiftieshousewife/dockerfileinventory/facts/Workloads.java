package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.yaml.YAMLMapper;

final class Workloads {

    private static final YAMLMapper YAML = new YAMLMapper();
    private static final String TEMPLATE_SPEC = "/spec/template/spec";
    private static final Map<String, String> POD_SPEC_POINTERS = Map.of(
            "Pod", "/spec",
            "Deployment", TEMPLATE_SPEC,
            "StatefulSet", TEMPLATE_SPEC,
            "DaemonSet", TEMPLATE_SPEC,
            "ReplicaSet", TEMPLATE_SPEC,
            "Job", TEMPLATE_SPEC,
            "DeploymentConfig", TEMPLATE_SPEC,
            "CronJob", "/spec/jobTemplate/spec/template/spec");

    record Read(List<Workload> workloads, String unread) {

        Read {
            workloads = List.copyOf(workloads);
        }
    }

    private Workloads() {
    }

    static Read read(String text) {
        return isTemplate(text) ? new Read(List.of(), "a template ({{ }} or {% %}), not plain YAML") : documents(text);
    }

    static boolean isTemplate(String text) {
        return text.contains("{{") || text.contains("{%");
    }

    // A file that stops being YAML part way (a documentation fragment) still gives the workloads before the break.
    static Read documents(String text) {
        List<Workload> workloads = new ArrayList<>();
        try (MappingIterator<JsonNode> documents = YAML.readerFor(JsonNode.class).readValues(text)) {
            documents.forEachRemaining(document -> {
                if (isWorkload(document)) {
                    workloads.add(workload(document));
                }
            });
        } catch (JacksonException e) {
            if (workloads.isEmpty()) {
                return new Read(List.of(), "not YAML: " + e.getOriginalMessage().lines().findFirst().orElse(""));
            }
        }
        return new Read(workloads, null);
    }

    static boolean isWorkload(JsonNode document) {
        return document != null && POD_SPEC_POINTERS.containsKey(document.path("kind").asString(""));
    }

    static Workload workload(JsonNode document) {
        String kind = document.path("kind").asString();
        JsonNode metadata = document.path("metadata");
        return new Workload(kind, metadata.path("name").stringValue(null), metadata.path("namespace").stringValue(null),
                pod(document.at(POD_SPEC_POINTERS.get(kind))));
    }

    static Workload.Pod pod(JsonNode spec) {
        List<Workload.Container> containers = Stream.concat(
                elements(spec.path("initContainers")).map(c -> container(c, true)),
                elements(spec.path("containers")).map(c -> container(c, false))).toList();
        List<String> hostPaths = elements(spec.path("volumes"))
                .map(volume -> volume.path("hostPath").path("path")).filter(JsonNode::isString)
                .map(JsonNode::asString).toList();
        return new Workload.Pod(spec.path("hostNetwork").asBoolean(false), spec.path("hostPID").asBoolean(false),
                spec.path("hostIPC").asBoolean(false), hostPaths, security(spec.path("securityContext")), containers);
    }

    static Workload.Container container(JsonNode container, boolean init) {
        JsonNode ports = container.path("ports");
        List<String> mounts = elements(container.path("volumeMounts"))
                .map(mount -> mount.path("mountPath").asString("")).filter(path -> !path.isEmpty()).toList();
        return new Workload.Container(container.path("name").stringValue(null),
                container.path("image").stringValue(null), init, ports(ports, "containerPort"),
                ports(ports, "hostPort"), new Workload.Probes(container.has("livenessProbe"),
                container.has("readinessProbe"), container.has("startupProbe")),
                mounts, security(container.path("securityContext")));
    }

    static List<Integer> ports(JsonNode ports, String field) {
        return elements(ports).map(port -> port.path(field)).filter(JsonNode::isNumber).map(JsonNode::asInt).toList();
    }

    static Workload.Security security(JsonNode context) {
        return new Workload.Security(flag(context, "privileged"),
                context.path("runAsUser").isNumber() ? context.path("runAsUser").asLong() : null,
                flag(context, "runAsNonRoot"), flag(context, "allowPrivilegeEscalation"),
                elements(context.path("capabilities").path("add")).map(JsonNode::asString).toList(),
                context.path("seccompProfile").path("type").stringValue(null));
    }

    static Boolean flag(JsonNode context, String name) {
        return context.path(name).isBoolean() ? context.path(name).asBoolean() : null;
    }

    static Stream<JsonNode> elements(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
    }
}
