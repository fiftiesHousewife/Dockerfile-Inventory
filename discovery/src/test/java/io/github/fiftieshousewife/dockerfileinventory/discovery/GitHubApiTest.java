package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GitHubApiTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "https://github.mycompany.com           | https://github.mycompany.com/api/ | v3/ | -",
        "https://github.mycompany.com/          | https://github.mycompany.com/api/ | v3/ | -",
        "https://github.mycompany.com/api       | https://github.mycompany.com/api/ | v3/ | -",
        "https://github.mycompany.com/api/v3/   | https://github.mycompany.com/api/ | v3/ | -",
        "https://git.internal:8443/api          | https://git.internal:8443/api/    | v3/ | -",
        "https://github.mycompany.com/Payments  | https://github.mycompany.com/api/ | v3/ | Payments",
        "https://github.com                     | https://api.github.com/           |     | -",
        "https://github.com/MyInternalOrg       | https://api.github.com/           |     | MyInternalOrg",
        "https://github.com/MyInternalOrg/      | https://api.github.com/           |     | MyInternalOrg",
        "https://api.github.com                 | https://api.github.com/           |     | -",
        "https://myco.ghe.com                   | https://api.myco.ghe.com/         |     | -",
        "https://myco.ghe.com/Platform          | https://api.myco.ghe.com/         |     | Platform",
        "https://api.myco.ghe.com/              | https://api.myco.ghe.com/         |     | -",
    })
    void everyFormOfGitHubUrlGivesItsApiAndAnyOrganisationInThePath(String url, String base, String prefix,
                                                                     String organization) {
        GitHubApi api = GitHubApi.from(url);

        assertThat(api.base()).hasToString(base);
        assertThat(api.restPrefix()).isEqualTo(prefix == null ? "" : prefix);
        assertThat(api.organization()).isEqualTo(java.util.Optional.ofNullable(organization));
    }
}
