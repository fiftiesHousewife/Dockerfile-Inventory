package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

// One change made once: to an archetype's representative, as a template for its copies. inheriting: the Dockerfiles
// built on a copy that keep what the fix changes; locations: every location of the copies and those; because: the
// representative's findings.
record Fix(Check check, Severity severity, Certainty certainty, String what, String representative,
           List<String> because, List<String> copies, List<String> inheriting, int locations) {

    Fix {
        because = List.copyOf(because);
        copies = List.copyOf(copies);
        inheriting = List.copyOf(inheriting);
    }
}
