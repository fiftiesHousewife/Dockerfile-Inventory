package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.NOTHING;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.container;
import static io.github.fiftieshousewife.dockerfileinventory.validator.WorkloadTest.pod;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class AddedCapabilityTest {

    private static final Profile V1 = Profile.shipped("openshift-4-restricted");

    private static Workload adding(String... capabilities) {
        return pod(NOTHING, container("app", new Workload.Security(null, null, null, null, List.of(capabilities),
                null)));
    }

    @Test
    void aCapabilityTheProfileDoesNotAllowIsNamed() {
        assertThat(new AddedCapability().problems(adding("SYS_ADMIN"), V2))
                .containsExactly(new Problem("container app adds SYS_ADMIN", Certainty.READ));
    }

    @Test
    void bindingLowPortsIsAllowedUnderV2AndNotUnderV1() {
        assertAll(
                () -> assertThat(new AddedCapability().problems(adding("NET_BIND_SERVICE"), V2)).isEmpty(),
                () -> assertThat(new AddedCapability().problems(adding("NET_BIND_SERVICE"), V1)).hasSize(1));
    }

    @Test
    void aCapabilityIsComparedWithoutItsPrefixOrCase() {
        assertThat(AddedCapability.name("cap_net_bind_service")).isEqualTo("NET_BIND_SERVICE");
    }
}
