package io.github.fiftieshousewife.dockerfileinventory.discovery;

/** Whether discovery goes on to fetch, once its first pass has found what there is. */
@FunctionalInterface
public interface Confirmation {

    /** Goes on whatever the first pass found: for a run nobody watches. */
    Confirmation ALWAYS = pass -> true;

    /**
     * Asks whether to go on.
     *
     * @param pass what the first pass found
     * @return true to fetch
     */
    boolean proceed(FirstPass pass);
}
