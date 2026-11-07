package io.github.fiftieshousewife.dockerfileinventory.rules;

record Location(String repository, String path) {

    String organisation() {
        return repository.split("/", 2)[0];
    }
}
