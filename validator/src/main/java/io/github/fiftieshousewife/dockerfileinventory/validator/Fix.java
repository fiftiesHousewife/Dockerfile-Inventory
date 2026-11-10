package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

record Fix(Check check, Severity severity, Certainty certainty, String what, String representative,
           List<String> because, List<String> copies, List<String> inheriting, int locations) {

    Fix {
        because = List.copyOf(because);
        copies = List.copyOf(copies);
        inheriting = List.copyOf(inheriting);
    }
}
