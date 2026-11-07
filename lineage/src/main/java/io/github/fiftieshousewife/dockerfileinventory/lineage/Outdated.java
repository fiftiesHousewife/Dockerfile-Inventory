package io.github.fiftieshousewife.dockerfileinventory.lineage;

record Outdated(String image, String parent, String imageName, String newer) {
}
