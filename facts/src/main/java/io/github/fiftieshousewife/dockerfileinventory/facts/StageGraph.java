package io.github.fiftieshousewife.dockerfileinventory.facts;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openrewrite.Cursor;

final class StageGraph {

    private static final Pattern COPY_FROM = Pattern.compile("^COPY\\s+(?:--\\S+\\s+)*?--from=(\\S+)",
            Pattern.CASE_INSENSITIVE);

    private StageGraph() {
    }

    static List<ImageStage> of(Docker.File file) {
        Map<String, String> globalArgs = FactExtractor.globalArgs(file);
        Set<String> earlier = new HashSet<>();
        List<ImageStage> stages = new ArrayList<>();
        for (Docker.Stage stage : file.getStages()) {
            Docker.From from = stage.getFrom();
            String image = ArgumentText.of(from.getImageName(), globalArgs);
            boolean onStage = from.getTag() == null && from.getDigest() == null && earlier.contains(image);
            Docker.From.As as = from.getAs();
            String name = as == null ? null : as.getName().getText();
            stages.add(new ImageStage(name, onStage ? null : BaseImages.base(from, globalArgs), onStage ? image : null,
                    copiesFrom(file, stage)));
            if (name != null) {
                earlier.add(name);
            }
        }
        return stages;
    }

    static List<String> copiesFrom(Docker.File file, Docker.Stage stage) {
        return stage.getInstructions().stream()
                .filter(Docker.Copy.class::isInstance)
                .map(copy -> COPY_FROM.matcher(Continuations.oneLine(copy.printTrimmed(new Cursor(null, file)))))
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .distinct()
                .toList();
    }
}
