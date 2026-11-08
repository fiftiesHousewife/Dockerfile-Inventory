package io.github.fiftieshousewife.dockerfile.model;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

abstract sealed class InstructionBuilder extends MetadataInstructionBuilder permits ImageBuilder {

    InstructionBuilder() {
    }

    abstract BaseImage currentBase();

    /** Packages with the base's own manager, cleaned in the same {@code RUN} (H1). */
    public ImageBuilder install(String... packages) {
        return install(PackageInstallRenderer.forBase(currentBase()), packages);
    }

    public ImageBuilder install(PackageManager manager, String... packages) {
        return add(new Run(new RunBody.Install(new PackageInstall(manager, Arrays.asList(packages)))));
    }

    /** An install with its own flags, rendered exactly, so a converted Dockerfile keeps them. */
    public ImageBuilder install(PackageManager manager, List<String> flags, List<String> packages) {
        return add(new Run(new RunBody.Install(new PackageInstall(manager, flags, packages))));
    }

    public ImageBuilder run(String... commands) {
        return add(new Run(new RunBody.Shell(Arrays.asList(commands))));
    }

    /** A {@code RUN} with flags such as a cache mount; the target must allow mounts. */
    public ImageBuilder run(List<RunFlag> flags, String... commands) {
        return add(new Run(new RunBody.Shell(Arrays.asList(commands)), flags));
    }

    public ImageBuilder runExec(String... arguments) {
        return add(new Run(new RunBody.Exec(Arrays.asList(arguments))));
    }

    /** A {@code RUN} as written: its flags and any body (an install, commands, exec form or a heredoc). */
    public ImageBuilder run(List<RunFlag> flags, RunBody body) {
        return add(new Run(body, flags));
    }

    /** Declared in the current stage, so its instructions can use it. */
    public ImageBuilder arg(String name) {
        return add(new Arg(name, null));
    }

    public ImageBuilder arg(String name, String defaultValue) {
        return add(new Arg(name, defaultValue));
    }

    public ImageBuilder env(String name, String value) {
        return add(new Env(Map.of(name, value)));
    }

    /** Several pairs in one {@code ENV}, in the map's order. */
    public ImageBuilder env(Map<String, String> values) {
        return add(new Env(values));
    }

    /** Several pairs in one {@code LABEL}, in the map's order. */
    public ImageBuilder label(Map<String, String> values) {
        return add(new Label(values));
    }

    public ImageBuilder label(String name, String value) {
        return add(new Label(Map.of(name, value)));
    }

    public ImageBuilder workdir(String path) {
        return add(new Workdir(path));
    }

    public ImageBuilder user(int uid) {
        return add(new User(Integer.toString(uid)));
    }

    /** A named user, which S4 refuses; kept so a refusal can say what was asked for. */
    public ImageBuilder user(String user) {
        return add(new User(user));
    }

    public ImageBuilder entrypoint(String... command) {
        return add(new Entrypoint(Arrays.asList(command)));
    }

    public ImageBuilder cmd(String... command) {
        return add(new Cmd(Arrays.asList(command)));
    }

    /** An {@code ENTRYPOINT} as written, for the faithful path; shell form is an H2 breach. */
    public ImageBuilder entrypoint(List<String> command, CommandForm form) {
        return add(new Entrypoint(command, form));
    }

    /** A {@code CMD} as written, for the faithful path. */
    public ImageBuilder cmd(List<String> command, CommandForm form) {
        return add(new Cmd(command, form));
    }

    /** Text the model cannot type; allowed, but every use is listed in {@link Built#raw}. */
    public ImageBuilder raw(String text, String reason) {
        return add(new Raw(text, reason));
    }
}
