package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

// One change to one Dockerfile: what to do, why (its findings), and every location it reaches.
record Action(Check check, Severity severity, Certainty certainty, String what, String blobSha,
              List<String> because, List<Location> where) {

    Action {
        because = List.copyOf(because);
        where = List.copyOf(where);
    }

    int reach() {
        return where.size();
    }
}
