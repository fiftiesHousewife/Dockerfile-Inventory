package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.util.List;

// What the OKD picker reads of quay.io's okd/scos-content: a release's component tags and each one's amd64 config.
interface Quay {

    List<String> tags(String release);

    String config(String tag);
}
