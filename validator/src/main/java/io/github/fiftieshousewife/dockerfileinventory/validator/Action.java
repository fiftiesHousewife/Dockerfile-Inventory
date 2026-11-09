package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

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
