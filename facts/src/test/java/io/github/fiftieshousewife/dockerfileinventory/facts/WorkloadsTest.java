package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkloadsTest {

    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/fixtures/workloads").resolve(name));
    }

    @Test
    void aFilesWorkloadsAreReadAndItsOtherDocumentsLeftOut() throws IOException {
        Workloads.Read read = Workloads.read(fixture("ptp-operator-manager.yaml"));

        assertAll(
                () -> assertThat(read.unread()).isNull(),
                () -> assertThat(read.workloads()).extracting(Workload::kind, Workload::name, Workload::namespace)
                        .containsExactly(org.assertj.core.groups.Tuple.tuple("Deployment", "ptp-operator",
                                "openshift-ptp")),
                () -> assertThat(read.workloads().getFirst().pod().containers()).extracting(Workload.Container::image)
                        .containsExactly("controller"));
    }

    @Test
    void aContainersPortsAndProbesAreRead() {
        Workload.Container container = Workloads.read("""
                kind: Deployment
                metadata: {name: api}
                spec:
                  template:
                    spec:
                      containers:
                      - name: api
                        image: api:1
                        ports: [{containerPort: 8087}]
                        livenessProbe: {httpGet: {path: /, port: 8087}}
                        readinessProbe: {httpGet: {path: /, port: 8087}}
                """).workloads().getFirst().pod().containers().getFirst();

        assertAll(
                () -> assertThat(container.ports()).containsExactly(8087),
                () -> assertThat(container.probes()).isEqualTo(new Workload.Probes(true, true, false)));
    }

    @Test
    void aGoOrLiquidTemplateIsUnreadSayingSo() throws IOException {
        assertAll(
                () -> assertThat(Workloads.read(fixture("ptp-daemon-template.yaml")).unread())
                        .isEqualTo("a template ({{ }} or {% %}), not plain YAML"),
                () -> assertThat(Workloads.read(fixture("bitwarden-cli-deployment.yaml")).unread())
                        .isEqualTo("a template ({{ }} or {% %}), not plain YAML"),
                () -> assertThat(Workloads.read(fixture("ptp-daemon-template.yaml")).workloads()).isEmpty());
    }

    @Test
    void aDocumentationFragmentIsNotYamlAndUnread() throws IOException {
        assertThat(Workloads.read(fixture("1password-connect-server-deployment.yaml")).unread()).startsWith("not YAML");
    }

    @Test
    void aFileWhoseLaterDocumentIsBrokenGivesTheWorkloadsBeforeIt() {
        assertThat(Workloads.read("kind: Pod\nmetadata: {name: p}\nspec: {containers: [{name: c, image: a}]}\n"
                + "---\nkind: Pod\n  bad: [\n").workloads()).hasSize(1);
    }

    @Test
    void eachKindsPodIsFoundWhereThatKindKeepsIt() {
        assertAll(
                () -> assertThat(Workloads.read("""
                        kind: Pod
                        metadata: {name: p}
                        spec: {containers: [{name: c, image: a}]}
                        """).workloads().getFirst().pod().containers()).hasSize(1),
                () -> assertThat(Workloads.read("""
                        kind: CronJob
                        metadata: {name: nightly}
                        spec:
                          jobTemplate:
                            spec:
                              template:
                                spec: {containers: [{name: c, image: b}]}
                        """).workloads().getFirst().pod().containers().getFirst().image()).isEqualTo("b"),
                () -> assertThat(Workloads.read("""
                        apiVersion: apps.openshift.io/v1
                        kind: DeploymentConfig
                        metadata: {name: dc}
                        spec: {template: {spec: {containers: [{name: c, image: d}]}}}
                        """).workloads()).hasSize(1));
    }

    @Test
    void thePodsAndEachContainersSecuritySettingsAreRead() {
        Workload.Pod pod = Workloads.read("""
                kind: DaemonSet
                metadata: {name: agent}
                spec:
                  template:
                    spec:
                      hostNetwork: true
                      hostPID: true
                      securityContext: {runAsNonRoot: true, seccompProfile: {type: RuntimeDefault}}
                      volumes: [{name: root, hostPath: {path: /}}, {name: tmp, emptyDir: {}}]
                      initContainers: [{name: init, image: busybox}]
                      containers:
                      - name: agent
                        image: agent:1
                        ports: [{containerPort: 80, hostPort: 80}]
                        volumeMounts: [{name: tmp, mountPath: /var/lib/agent}]
                        securityContext:
                          privileged: true
                          runAsUser: 0
                          allowPrivilegeEscalation: true
                          capabilities: {add: [NET_ADMIN, SYS_TIME]}
                """).workloads().getFirst().pod();
        Workload.Container agent = pod.containers().get(1);

        assertAll(
                () -> assertThat(pod.hostNetwork()).isTrue(),
                () -> assertThat(pod.hostPid()).isTrue(),
                () -> assertThat(pod.hostPaths()).containsExactly("/"),
                () -> assertThat(pod.security()).isEqualTo(new Workload.Security(null, null, true, null, List.of(),
                        "RuntimeDefault")),
                () -> assertThat(pod.containers().getFirst().init()).isTrue(),
                () -> assertThat(agent.hostPorts()).containsExactly(80),
                () -> assertThat(agent.mountPaths()).containsExactly("/var/lib/agent"),
                () -> assertThat(agent.security()).isEqualTo(new Workload.Security(true, 0L, null, true,
                        List.of("NET_ADMIN", "SYS_TIME"), null)));
    }

    @Test
    void yamlThatDoesNotParseIsUnreadWithWhy() {
        assertThat(Workloads.read("kind: Deployment\n  bad: [").unread()).startsWith("not YAML: ");
    }
}
