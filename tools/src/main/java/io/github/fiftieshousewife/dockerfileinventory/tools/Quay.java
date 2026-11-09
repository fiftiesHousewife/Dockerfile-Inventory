package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.util.List;

interface Quay {

    List<String> tags(String release);

    String config(String tag);
}
