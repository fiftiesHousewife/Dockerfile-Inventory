package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeploymentsTest {

    private static ActionPlanView.PlannedContainer runs(String name, String image, boolean floating,
                                                        String... dockerfiles) {
        return new ActionPlanView.PlannedContainer(name, image, List.of(dockerfiles), floating,
                dockerfiles.length == 0 ? "no lineage" : null);
    }

    static ActionPlanView.PlannedWorkload workload(String name, ActionPlanView.PlannedContainer... containers) {
        return new ActionPlanView.PlannedWorkload("yaml-" + name, "Deployment", name, "shop",
                List.of(new ActionPlanView.Where("team/shop", "deploy/" + name + ".yaml")), "BREAKS",
                List.of(containers), List.of());
    }

    @Test
    void eachDockerfileGathersTheWorkloadsWhoseContainersRunItOnceEach() {
        ActionPlanView.PlannedWorkload web = workload("web", runs("app", "app:1", false, "aaa"),
                runs("sidecar", "app:1", false, "aaa"));
        ActionPlanView.PlannedWorkload jobs = workload("jobs", runs("app", "app:1", false, "aaa", "bbb"),
                runs("db", "postgres", true));

        Map<String, List<ActionPlanView.PlannedWorkload>> byDockerfile = Deployments.byDockerfile(List.of(web, jobs));

        assertAll(
                () -> assertThat(byDockerfile.get("aaa")).containsExactly(web, jobs),
                () -> assertThat(byDockerfile.get("bbb")).containsExactly(jobs),
                () -> assertThat(byDockerfile).hasSize(2));
    }

    @Test
    void theSectionNamesEachWorkloadItsFileAndTheContainersRunningTheDockerfile() {
        String section = Deployments.section("aaa", List.of(workload("web", runs("app", "quay.io/app", true, "aaa"),
                runs("db", "postgres:16", false, "ccc"))));

        assertAll(
                () -> assertThat(section).contains("<h2>Runs in</h2>", "<td>breaks</td>",
                        "<td>Deployment web (shop)</td>", "<td>team/shop deploy/web.yaml</td>",
                        "<td>app: quay.io/app (floats)</td>", "href=\"../openshift.html\""),
                () -> assertThat(section).doesNotContain("postgres"));
    }

    @Test
    void aDockerfileNoWorkloadRunsHasNoSection() {
        assertThat(Deployments.section("aaa", List.of())).isEmpty();
    }

    @Test
    void onlyTheContainersRunningTheDockerfileAreNamed() {
        assertThat(Deployments.running("aaa", workload("web", runs("app", "app:1", false, "aaa"),
                runs("db", "postgres:16", false, "ccc")))).isEqualTo("app: app:1");
    }
}
