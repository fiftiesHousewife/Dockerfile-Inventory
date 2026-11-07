package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

abstract sealed class InstructionBuilder permits ImageBuilder {

    InstructionBuilder() {
    }

    abstract ImageBuilder add(Instruction instruction);

    abstract BaseImage currentBase();

    /** Packages with the base's own manager, cleaned in the same {@code RUN} (H1). */
    public ImageBuilder install(String... packages) {
        return install(PackageInstallRenderer.forBase(currentBase()), packages);
    }

    public ImageBuilder install(PackageManager manager, String... packages) {
        return add(new Instruction.Run(new RunBody.Install(new PackageInstall(manager, Arrays.asList(packages)))));
    }

    /** An install with its own flags, rendered exactly, so a converted Dockerfile keeps them. */
    public ImageBuilder install(PackageManager manager, List<String> flags, List<String> packages) {
        return add(new Instruction.Run(new RunBody.Install(new PackageInstall(manager, flags, packages))));
    }

    public ImageBuilder run(String... commands) {
        return add(new Instruction.Run(new RunBody.Shell(Arrays.asList(commands))));
    }

    public ImageBuilder copy(String source, String destination) {
        return add(new Instruction.Copy(List.of(source), destination, null, null));
    }

    /** A {@code COPY} as written, for the faithful path; {@code chown} and {@code fromStage} may be null. */
    public ImageBuilder copy(List<String> sources, String destination, String chown, String fromStage) {
        return add(new Instruction.Copy(sources, destination, chown, fromStage));
    }

    public ImageBuilder copyFrom(String stage, String source, String destination) {
        return add(new Instruction.Copy(List.of(source), destination, null, stage));
    }

    /** Declared in the current stage, so its instructions can use it. */
    public ImageBuilder arg(String name) {
        return add(new Instruction.Arg(name, null));
    }

    public ImageBuilder arg(String name, String defaultValue) {
        return add(new Instruction.Arg(name, defaultValue));
    }

    public ImageBuilder env(String name, String value) {
        return add(new Instruction.Env(Map.of(name, value)));
    }

    /** Several pairs in one {@code ENV}, in the map's order. */
    public ImageBuilder env(Map<String, String> values) {
        return add(new Instruction.Env(values));
    }

    /** Several pairs in one {@code LABEL}, in the map's order. */
    public ImageBuilder label(Map<String, String> values) {
        return add(new Instruction.Label(values));
    }

    public ImageBuilder label(String name, String value) {
        return add(new Instruction.Label(Map.of(name, value)));
    }

    public ImageBuilder workdir(String path) {
        return add(new Instruction.Workdir(path));
    }

    public ImageBuilder expose(int port) {
        return add(new Instruction.Expose(port));
    }

    public ImageBuilder user(int uid) {
        return add(new Instruction.User(Integer.toString(uid)));
    }

    /** A named user, which S4 refuses; kept so a refusal can say what was asked for. */
    public ImageBuilder user(String user) {
        return add(new Instruction.User(user));
    }

    public ImageBuilder entrypoint(String... command) {
        return add(new Instruction.Entrypoint(Arrays.asList(command)));
    }

    public ImageBuilder cmd(String... command) {
        return add(new Instruction.Cmd(Arrays.asList(command)));
    }

    /** Text the model cannot type; allowed, but every use is listed in {@link Built#raw}. */
    public ImageBuilder raw(String text, String reason) {
        return add(new Instruction.Raw(text, reason));
    }
}
