package io.github.fiftieshousewife.dockerfileinventory.lineage;

record NormalStep(String keyword, String text, String from, boolean builderAdded) {

    NormalStep withFrom(String reference) {
        return new NormalStep(keyword, text, reference, builderAdded);
    }
}
