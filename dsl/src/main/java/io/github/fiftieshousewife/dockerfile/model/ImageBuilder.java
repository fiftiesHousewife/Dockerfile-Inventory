package io.github.fiftieshousewife.dockerfile.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds an image that meets the policy, or fails listing every breach with its rule ID, so one build shows
 * everything to fix:
 * <ul>
 *   <li>S4: the final user is numeric and non-root; writable paths get {@code chgrp 0} and {@code chmod g=u}
 *       before {@code USER}, and copies into the final stage are owned by {@code <uid>:0}, so any UID in group 0
 *       can run it.</li>
 *   <li>S1 and R1: the final base is approved and pinned by digest.</li>
 *   <li>H2: the entrypoint is exec form; a shell form can only be written as {@code raw}, and is a breach.</li>
 *   <li>H1: {@link #install} always cleans; a shell or raw install that does not is a breach.</li>
 *   <li>H3: adjacent {@code RUN}s are joined, unless the first changes shell state.</li>
 * </ul>
 * Calls apply to the stage begun last.
 */
public final class ImageBuilder extends InstructionBuilder {

    private final List<Arg> globalArgs = new ArrayList<>();
    private final List<Draft> stages = new ArrayList<>();
    private final List<String> writable = new ArrayList<>();

    private ImageBuilder() {
    }

    /** A single-stage image on {@code base}, which the policy needs approved and pinned (S1, R1). */
    public static ImageBuilder finalStage(BaseImage base) {
        return create().thenFrom(base);
    }

    /** An image whose first stage is a named builder stage; begin the final stage with {@link #thenFrom}. */
    public static ImageBuilder stage(String name, BaseImage base) {
        return create().thenStage(name, base);
    }

    /** An empty builder for the faithful path: add global arguments, then stages with {@code thenStage}. */
    public static ImageBuilder create() {
        return new ImageBuilder();
    }

    /** A builder holding an existing model, for checking it against the policy or continuing it. */
    public static ImageBuilder of(Dockerfile model) {
        ImageBuilder builder = new ImageBuilder();
        builder.globalArgs.addAll(model.globalArgs());
        model.stages().stream().map(Draft::of).forEach(builder.stages::add);
        return builder;
    }

    /** A build argument before the first {@code FROM}, so any {@code FROM} can use it. */
    public ImageBuilder globalArg(String name, String defaultValue) {
        globalArgs.add(new Arg(name, defaultValue));
        return this;
    }

    /** {@code name} may be null. */
    public ImageBuilder thenStage(String name, BaseImage base) {
        stages.add(new Draft(name, base, null));
        return this;
    }

    /** A stage built on an earlier stage; {@code name} may be null. */
    public ImageBuilder thenStageFrom(String name, String earlierStage) {
        stages.add(new Draft(name, null, earlierStage));
        return this;
    }

    /** Begins the final stage on {@code base}. */
    public ImageBuilder thenFrom(BaseImage base) {
        return thenStage(null, base);
    }

    /** Makes the instruction just added run in images built {@code FROM} this one rather than in this one. */
    public ImageBuilder asOnbuild() {
        stages.getLast().onbuildLast();
        return this;
    }

    /** Paths the process writes to at run time, made writable by group 0 for an arbitrary UID (S4). */
    public ImageBuilder writablePaths(String... paths) {
        writable.addAll(Arrays.asList(paths));
        return this;
    }

    /** The image with the policy's additions, or a {@link PolicyViolation} listing every breach. */
    public Built build(Policy policy) {
        List<PolicyViolation.Breach> breaches = check(policy);
        if (!breaches.isEmpty()) {
            throw new PolicyViolation(breaches);
        }
        return new Built(new Dockerfile(globalArgs, stages.stream().map(this::finished).toList()), raw());
    }

    /** The Dockerfile exactly as written, with no checks and no additions: what a faithful conversion needs. */
    public Dockerfile model() {
        return new Dockerfile(globalArgs, stages.stream().map(Draft::stage).toList());
    }

    /** Every breach {@link #build} would throw, without throwing. */
    public List<PolicyViolation.Breach> check(Policy policy) {
        return PolicyCheck.breaches(policy, stages);
    }

    Stage finished(Draft draft) {
        List<Instruction> instructions = draft == stages.getLast()
                ? FinalStage.finished(draft.instructions(), writable)
                : draft.instructions();
        return draft.stage(RunMerge.merged(instructions));
    }

    List<Raw> raw() {
        return stages.stream()
                .flatMap(stage -> stage.instructions().stream())
                .<Raw>mapMulti((i, raw) -> {
                    if (i instanceof Raw r) {
                        raw.accept(r);
                    }
                })
                .toList();
    }

    @Override
    ImageBuilder add(Instruction instruction) {
        stages.getLast().add(instruction);
        return this;
    }

    @Override
    BaseImage currentBase() {
        return stages.getLast().baseIn(stages);
    }
}
