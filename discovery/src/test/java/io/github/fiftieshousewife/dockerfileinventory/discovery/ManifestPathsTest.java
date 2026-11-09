package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ManifestPathsTest {

    @ParameterizedTest
    @CsvSource({
        "deploy/deployment.yaml, true", "config/manager/manager.yaml, true", "openshift/template.YML, true",
        "manifests/0000_50_operator.yml, true",
        "vendor/k8s.io/api/testdata/pod.yaml, false", "pkg/steps/testdata/pod.yaml, false",
        "node_modules/x/deploy.yaml, false", ".github/workflows/ci.yaml, false", ".tekton/pull-request.yaml, false",
        ".gitlab-ci.yml, false", ".ci-operator.yaml, false", ".circleci/config.yml, false",
        "charts/api/templates/deployment.yaml, false", "charts/api/Chart.yaml, false",
        "charts/api/values.yaml, false", "Dockerfile, false", "deploy/README.md, false",
        "test/integration/expected/deployment.yaml, false", "tests/e2e/pod.yaml, false",
        "e2e/manifests/job.yaml, false", "docs/snippets/deployment.yaml, false", "doc/example.yml, false",
        "bitnami/autoscaler/aws-examples/one.yaml, false",
        "examples/basic/deploy.yaml, false", "samples/app.yaml, false", "config/samples/cr.yaml, false",
        "latest/deploy.yaml, true", "contest/deploy.yaml, true",
        "openshift/installer/vendored/openshift-ansible-3.11/roles/node/files/sync.yaml, false"})
    void aCandidateManifestIsYamlOutsideVendoredTestDocsExampleCiAndHelmFiles(String path, boolean candidate) {
        assertThat(ManifestPaths.isCandidate(path)).isEqualTo(candidate);
    }
}
