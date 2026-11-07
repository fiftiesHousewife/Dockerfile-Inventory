package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;
import java.util.function.Function;

interface EffectiveRule {

    String id();

    Severity severity();

    List<Finding> check(ImageView image, Function<String, String> blobText);
}
