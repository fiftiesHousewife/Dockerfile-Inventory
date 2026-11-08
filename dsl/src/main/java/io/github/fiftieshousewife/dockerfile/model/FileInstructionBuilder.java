package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;

abstract sealed class FileInstructionBuilder permits MetadataInstructionBuilder {

    FileInstructionBuilder() {
    }

    abstract ImageBuilder add(Instruction instruction);

    /** One path from the build context; copies into the final stage are owned by {@code <uid>:0} (S4). */
    public ImageBuilder copy(String source, String destination) {
        return add(new Copy(List.of(source), destination, null, null));
    }

    /** A {@code COPY} with any of its parts: {@code copy(Copy.of("a").to("/b/").mode("755"))}. */
    public ImageBuilder copy(Copy copy) {
        return add(copy);
    }

    /** A file written from lines, unexpanded; the target must allow heredocs. */
    public ImageBuilder copyHeredoc(String destination, List<String> lines) {
        return copyHeredoc(CopyHeredoc.to(destination).lines(lines.toArray(String[]::new)));
    }

    /** A {@code COPY} heredoc with any of its parts; the target must allow heredocs. */
    public ImageBuilder copyHeredoc(CopyHeredoc heredoc) {
        return add(heredoc);
    }

    /** One path from an earlier stage, as {@code COPY --from}. */
    public ImageBuilder copyFrom(String stage, String source, String destination) {
        return add(new Copy(List.of(source), destination, null, stage));
    }

    /** A URL, a Git repository or a tar archive to unpack; anything else is refused: use {@code copy}. */
    public ImageBuilder add(String source, String destination) {
        if (!AddSources.addable(source)) {
            throw new IllegalArgumentException("ADD is for a URL, a Git repository or an archive; use copy for "
                    + source);
        }
        return add(Add.of(source).to(destination));
    }

    /** An {@code ADD} as written, for the faithful path: not checked against the policy's URL-or-archive rule. */
    public ImageBuilder add(Add add) {
        return add((Instruction) add);
    }
}
