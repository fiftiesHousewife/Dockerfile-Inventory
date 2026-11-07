package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;

abstract sealed class FileInstructionBuilder permits MetadataInstructionBuilder {

    FileInstructionBuilder() {
    }

    abstract ImageBuilder add(Instruction instruction);

    public ImageBuilder copy(String source, String destination) {
        return add(new Instruction.Copy(List.of(source), destination, null, null));
    }

    /** A {@code COPY} as written, for the faithful path; {@code chown} and {@code fromStage} may be null. */
    public ImageBuilder copy(List<String> sources, String destination, String chown, String fromStage) {
        return add(new Instruction.Copy(sources, destination, chown, fromStage));
    }

    /** A {@code COPY} as written with its {@code --chmod}, {@code --link} and JSON form, for the faithful path. */
    public ImageBuilder copy(List<String> sources, String destination, String chown, String fromStage, String chmod,
            boolean link, boolean jsonForm) {
        return add(new Instruction.Copy(sources, destination, chown, fromStage, chmod, link, jsonForm));
    }

    /** A file written from lines, unexpanded; the target must allow heredocs. */
    public ImageBuilder copyHeredoc(String destination, List<String> lines) {
        return copyHeredoc(destination, "EOF", true, lines);
    }

    /** A {@code COPY} heredoc as written, for the faithful path. */
    public ImageBuilder copyHeredoc(String destination, String delimiter, boolean quoted, List<String> lines) {
        return add(new Instruction.CopyHeredoc(destination, delimiter, quoted, lines));
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
        return add(new Instruction.Add(List.of(source), destination, null, null, null, false, false, false));
    }

    /** An {@code ADD} as written, for the faithful path; any value may be null or false. */
    public ImageBuilder add(List<String> sources, String destination, String chown, String chmod, String checksum,
            boolean keepGitDir, boolean link, boolean jsonForm) {
        return add(new Instruction.Add(sources, destination, chown, chmod, checksum, keepGitDir, link, jsonForm));
    }
}
