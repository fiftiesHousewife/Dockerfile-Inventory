package io.github.fiftieshousewife.dockerfileinventory.facts;

/**
 * The image a stage builds {@code FROM}. Tag and digest are null when absent. A variable the Dockerfile alone
 * cannot resolve stays in the text as {@code ${NAME}}.
 */
public record BaseImage(String name, String tag, String digest) {

    public boolean resolved() {
        return !(name + tag + digest).contains("${");
    }

    public boolean digestPinned() {
        return digest != null;
    }
}
