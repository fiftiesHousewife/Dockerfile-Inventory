package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.HashMap;
import java.util.Map;
import org.openrewrite.docker.tree.Docker;

final class BaseImages {

    private BaseImages() {
    }

    static BaseImage finalBase(Docker.File file, Map<String, String> args) {
        Map<String, BaseImage> byStageName = new HashMap<>();
        BaseImage base = null;
        for (Docker.Stage stage : file.getStages()) {
            BaseImage from = base(stage.getFrom(), args);
            base = from.tag() == null && from.digest() == null ? byStageName.getOrDefault(from.name(), from) : from;
            Docker.From.As as = stage.getFrom().getAs();
            if (as != null) {
                byStageName.put(as.getName().getText(), base);
            }
        }
        return base;
    }

    // The parser splits name, tag and digest before variables are known, so a FROM ${BASE} whose default carries a
    // tag would keep the tag in the name: the reference is rebuilt, substituted, then split.
    static BaseImage base(Docker.From from, Map<String, String> args) {
        Docker.Argument tag = from.getTag();
        Docker.Argument digest = from.getDigest();
        String reference = ArgumentText.of(from.getImageName(), args)
                + (tag == null ? "" : ":" + ArgumentText.of(tag, args))
                + (digest == null ? "" : "@" + ArgumentText.of(digest, args));
        return split(reference);
    }

    static BaseImage split(String reference) {
        int at = reference.indexOf('@');
        String digest = at < 0 ? null : reference.substring(at + 1);
        String nameAndTag = at < 0 ? reference : reference.substring(0, at);
        int colon = nameAndTag.lastIndexOf(':');
        boolean hasTag = colon > nameAndTag.lastIndexOf('/');
        return new BaseImage(hasTag ? nameAndTag.substring(0, colon) : nameAndTag,
                hasTag ? nameAndTag.substring(colon + 1) : null, digest);
    }
}
