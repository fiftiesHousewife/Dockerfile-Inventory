package io.github.fiftieshousewife.dockerfileinventory.http;

/** What a call sends as its bearer token, and how to get a fresh one after a 401. Never shown. */
public interface Credential {

    /**
     * The token to send now.
     *
     * @return the token
     */
    String token();

    /**
     * Gets another token after the server refused this one.
     *
     * @return true when the next call will send a different token, so it is worth one more try
     */
    boolean refresh();
}
