package io.github.fiftieshousewife.dockerfileinventory.report;

/** The estate after one collapse pass: distinct groups, and the locations they cover (D1). */
public record PassCount(String pass, int groups, int locations) {
}
