package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

interface PodCheck {

    Check id();

    List<Problem> problems(Workload workload, Profile profile);
}
