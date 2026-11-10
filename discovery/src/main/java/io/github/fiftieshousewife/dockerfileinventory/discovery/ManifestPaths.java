package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.Locale;
import java.util.regex.Pattern;

// A chart's templates are rendered, not read, and Chart.yaml and values hold no workload. Measured on ten cloned
// repositories, about one candidate in a hundred held a workload before tests, docs and examples were left out.
final class ManifestPaths {

    private static final Pattern YAML = Pattern.compile(".*\\.ya?ml$");
    private static final Pattern NOT_DEPLOYED = Pattern.compile("(^|/)(vendor(ed)?|node_modules|third_party|testdata"
            + "|testfiles|testfiles-negative|\\.github|\\.tekton|\\.circleci|templates|tests?|e2e|docs?"
            + "|[^/]*examples?|samples?)/");
    private static final Pattern NOT_MANIFESTS = Pattern.compile("(^|/)(\\.gitlab-ci\\.ya?ml|\\.ci-operator\\.yaml"
            + "|\\.travis\\.yml|chart\\.ya?ml|values[^/]*\\.ya?ml)$");

    private ManifestPaths() {
    }

    static boolean isCandidate(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return YAML.matcher(lower).matches() && !NOT_DEPLOYED.matcher(lower).find()
                && !NOT_MANIFESTS.matcher(lower).find();
    }
}
