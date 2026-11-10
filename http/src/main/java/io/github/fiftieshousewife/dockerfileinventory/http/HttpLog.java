package io.github.fiftieshousewife.dockerfileinventory.http;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * The debug lines for a call: its method, host and path only, never its query or headers, which can hold a token.
 */
public final class HttpLog {

    private HttpLog() {
    }

    /**
     * A call answered.
     *
     * @param request the request
     * @param status its response's status
     * @param took how long it took
     * @return the line
     */
    public static String call(HttpRequest request, int status, Duration took) {
        return target(request) + " " + status + " in " + took.toMillis() + " ms";
    }

    /**
     * A call that failed before any response.
     *
     * @param request the request
     * @param error why
     * @return the line
     */
    public static String failed(HttpRequest request, Exception error) {
        return target(request) + " failed: " + error.getClass().getSimpleName();
    }

    /**
     * A wait before a call is made again.
     *
     * @param request the request
     * @param wait how long
     * @return the line
     */
    public static String waiting(HttpRequest request, Duration wait) {
        return "waiting " + wait.toMillis() / 1000.0 + " s before " + target(request);
    }

    static String target(HttpRequest request) {
        URI uri = request.uri();
        return request.method() + " " + uri.getHost() + uri.getRawPath();
    }
}
