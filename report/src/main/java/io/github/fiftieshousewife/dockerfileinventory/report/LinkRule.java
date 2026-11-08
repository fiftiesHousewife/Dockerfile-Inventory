package io.github.fiftieshousewife.dockerfileinventory.report;

// When a partial alignment credits an image to a Dockerfile: one candidate, scoring at least the threshold and
// beating the runner-up by at least the margin. Measured 8 Nov on OKD: correct pairs score 0.8 to 1.0, the
// closest beating its runner-up by 0.06.
record LinkRule(double threshold, double margin) {

    static final LinkRule DEFAULT = new LinkRule(0.8, 0.05);
}
