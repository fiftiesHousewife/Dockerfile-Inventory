package io.github.fiftieshousewife.dockerfileinventory.report;

record Location(String repository, String path, String scope) {

    String organisation() {
        int slash = repository.indexOf('/');
        return slash < 0 ? repository : repository.substring(0, slash);
    }
}
