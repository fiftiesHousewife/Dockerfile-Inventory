package io.github.fiftieshousewife.dockerfileinventory.http;

import java.time.Duration;

/** How a client waits before trying again; tests pass one that does not wait. */
@FunctionalInterface
public interface Sleeper {

    Sleeper REAL = wait -> Thread.sleep(wait);

    void sleep(Duration wait) throws InterruptedException;
}
