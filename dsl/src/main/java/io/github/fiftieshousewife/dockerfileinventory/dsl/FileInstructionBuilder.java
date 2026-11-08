package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;

abstract sealed class FileInstructionBuilder permits MetadataInstructionBuilder {

    FileInstructionBuilder() {
    }

    abstract ImageBuilder add(Instruction instruction);

    public ImageBuilder copy(String source, String destination) {
        return add(new Instruction.Copy(List.of(source), destination, null, null));
    }

    /** A {@code COPY} with any of its parts: {@code copy(Instruction.Copy.of("a").to("/b/").mode("755"))}. */
    public ImageBuilder copy(Instruction.Copy copy) {
        return add(copy);
    }

    /** A file written from lines, unexpanded; the target must allow heredocs. */
    public ImageBuilder copyHeredoc(String destination, List<String> lines) {
        return copyHeredoc(Instruction.CopyHeredoc.to(destination).lines(lines.toArray(String[]::new)));
    }

    /** A {@code COPY} heredoc with any of its parts; the target must allow heredocs. */
    public ImageBuilder copyHeredoc(Instruction.CopyHeredoc heredoc) {
        return add(heredoc);
    }

    public ImageBuilder copyFrom(String stage, String source, String destination) {
        return add(new Instruction.Copy(List.of(source), destination, null, stage));
    }

    /** A URL, a Git repository or a tar archive to unpack; anything else is refused: use {@code copy}. */
    public ImageBuilder add(String source, String destination) {
        if (!AddSources.addable(source)) {
            throw new IllegalArgumentException("ADD is for a URL, a Git repository or an archive; use copy for "
                    + source);
        }
        return add(Instruction.Add.of(source).to(destination));
    }

    /** An {@code ADD} as written, for the faithful path: not checked against the policy's URL-or-archive rule. */
    public ImageBuilder add(Instruction.Add add) {
        return add((Instruction) add);
    }
}
