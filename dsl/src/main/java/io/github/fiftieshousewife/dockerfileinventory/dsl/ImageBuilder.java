package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds an image that meets the policy, or fails listing every breach with its Phase 3 rule ID:
 * <ul>
 *   <li>S4: the final user is numeric and non-root; declared writable paths get {@code chgrp 0} and
 *       {@code chmod g=u} in one {@code RUN} before {@code USER}, and copies into the final stage are owned by
 *       {@code <uid>:0}.</li>
 *   <li>S1 and R1: the final base is approved and pinned by digest.</li>
 *   <li>H2: the entrypoint is exec form; a shell form can only be written as {@code raw}, and is a breach.</li>
 *   <li>H1: {@link #install} always cleans; a shell or raw install that does not is a breach.</li>
 * </ul>
 * Calls apply to the stage begun last. {@code raw} is allowed but every use is listed in the result.
 */
public final class ImageBuilder {

    private static final Pattern RPM_INSTALL = Pattern.compile("\\b(microdnf|dnf|yum)\\s+(-\\S+\\s+)*install\\b");
    private static final Pattern RPM_CLEAN =
            Pattern.compile("\\b(microdnf|dnf|yum)\\s+clean\\s+all\\b|rm\\s+-rf\\s+/var/cache/(dnf|yum)");
    private static final Pattern APT_INSTALL = Pattern.compile("\\bapt(-get)?\\s+(-\\S+\\s+)*install\\b");
    private static final Pattern APT_CLEAN = Pattern.compile("rm\\s+-rf\\s+/var/lib/apt/lists");
    private static final Pattern APK_ADD = Pattern.compile("\\bapk\\s+add\\b");
    private static final Pattern APK_CLEAN = Pattern.compile("--no-cache|rm\\s+-rf\\s+/var/cache/apk");

    private static final class Draft {
        private final String name;
        private final BaseImage base;
        private final String fromStage;
        private final List<Instruction> instructions = new ArrayList<>();

        Draft(String name, BaseImage base, String fromStage) {
            this.name = name;
            this.base = base;
            this.fromStage = fromStage;
        }
    }

    private final List<Draft> stages = new ArrayList<>();
    private final List<String> writable = new ArrayList<>();

    private ImageBuilder() {
    }

    /** An image with one stage. */
    public static ImageBuilder finalStage(BaseImage base) {
        ImageBuilder builder = new ImageBuilder();
        builder.stages.add(new Draft(null, base, null));
        return builder;
    }

    /** An image whose first stage is a named builder stage; begin the final stage with {@link #thenFrom}. */
    public static ImageBuilder stage(String name, BaseImage base) {
        ImageBuilder builder = new ImageBuilder();
        builder.stages.add(new Draft(name, base, null));
        return builder;
    }

    /** Begins the final stage on a base image. */
    public ImageBuilder thenFrom(BaseImage base) {
        stages.add(new Draft(null, base, null));
        return this;
    }

    /** Packages with the base's own manager, cleaned in the same {@code RUN}. */
    public ImageBuilder install(String... packages) {
        PackageManager manager = PackageInstallRenderer.forBase(baseOf(current()));
        return install(manager, packages);
    }

    public ImageBuilder install(PackageManager manager, String... packages) {
        return add(new Instruction.Run(new RunBody.Install(new PackageInstall(manager, Arrays.asList(packages)))));
    }

    public ImageBuilder run(String... commands) {
        return add(new Instruction.Run(new RunBody.Shell(Arrays.asList(commands))));
    }

    public ImageBuilder copy(String source, String destination) {
        return add(new Instruction.Copy(List.of(source), destination, null, null));
    }

    public ImageBuilder copyFrom(String stage, String source, String destination) {
        return add(new Instruction.Copy(List.of(source), destination, null, stage));
    }

    public ImageBuilder env(String name, String value) {
        return add(new Instruction.Env(Map.of(name, value)));
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

    /** Paths the process writes to at run time; made group-0 writable so any UID in group 0 can write (S4). */
    public ImageBuilder writablePaths(String... paths) {
        writable.addAll(Arrays.asList(paths));
        return this;
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

    public ImageBuilder raw(String text, String reason) {
        return add(new Instruction.Raw(text, reason));
    }

    public Built build(Policy policy) {
        Draft last = stages.getLast();
        String user = finalUser(last);
        List<PolicyViolation.Breach> breaches = new ArrayList<>();
        if (!numericNonRoot(user)) {
            breaches.add(new PolicyViolation.Breach("S4", user == null ? "the final stage sets no USER"
                    : "the final user " + user + " is not a numeric, non-root UID"));
        }
        BaseImage base = baseOf(last);
        if (!policy.approves(base)) {
            breaches.add(new PolicyViolation.Breach("S1", "base " + base.name() + ":" + base.tag()
                    + " is not on the approved list"));
        }
        if (base.digest() == null) {
            breaches.add(new PolicyViolation.Breach("R1", "base " + base.name() + " is not pinned by digest"));
        }
        List<Instruction.Raw> raw = new ArrayList<>();
        for (Draft stage : stages) {
            for (Instruction instruction : stage.instructions) {
                if (instruction instanceof Instruction.Raw r) {
                    raw.add(r);
                    if (r.text().startsWith("ENTRYPOINT") && !r.text().substring(10).strip().startsWith("[")) {
                        breaches.add(new PolicyViolation.Breach("H2", "shell-form entrypoint: " + r.text()));
                    }
                }
                uncleanedInstall(instruction).ifPresent(command -> breaches.add(new PolicyViolation.Breach("H1",
                        "an install that does not clean in the same RUN: " + command)));
            }
        }
        if (!breaches.isEmpty()) {
            throw new PolicyViolation(breaches);
        }
        return new Built(new Dockerfile(List.of(), stages.stream().map(d -> finish(d, d == last, user)).toList()),
                raw);
    }

    /** The final stage gets the group-0 step before its {@code USER}, and its copies owned by {@code <uid>:0}. */
    private Stage finish(Draft draft, boolean isFinal, String user) {
        List<Instruction> instructions = new ArrayList<>();
        for (Instruction instruction : draft.instructions) {
            if (isFinal && instruction instanceof Instruction.User && !writable.isEmpty()) {
                String paths = String.join(" ", writable);
                instructions.add(new Instruction.Run(new RunBody.Shell(List.of("chgrp -R 0 " + paths,
                        "chmod -R g=u " + paths))));
            }
            if (isFinal && instruction instanceof Instruction.Copy copy && copy.chown() == null) {
                instructions.add(new Instruction.Copy(copy.sources(), copy.destination(), user + ":0",
                        copy.fromStage()));
            } else {
                instructions.add(instruction);
            }
        }
        return new Stage(draft.name, draft.base, draft.fromStage, instructions);
    }

    private static java.util.Optional<String> uncleanedInstall(Instruction instruction) {
        String text = switch (instruction) {
            case Instruction.Run run when run.body() instanceof RunBody.Shell shell -> String.join(" && ",
                    shell.commands());
            case Instruction.Raw raw when raw.text().startsWith("RUN ") -> raw.text();
            default -> null;
        };
        if (text == null) {
            return java.util.Optional.empty();
        }
        boolean uncleaned = RPM_INSTALL.matcher(text).find() && !RPM_CLEAN.matcher(text).find()
                || APT_INSTALL.matcher(text).find() && !APT_CLEAN.matcher(text).find()
                || APK_ADD.matcher(text).find() && !APK_CLEAN.matcher(text).find();
        return uncleaned ? java.util.Optional.of(text) : java.util.Optional.empty();
    }

    private static String finalUser(Draft stage) {
        String user = null;
        for (Instruction instruction : stage.instructions) {
            if (instruction instanceof Instruction.User u) {
                user = u.user();
            }
        }
        return user;
    }

    private static boolean numericNonRoot(String user) {
        if (user == null) {
            return false;
        }
        String uid = user.split(":", 2)[0];
        return uid.chars().allMatch(Character::isDigit) && !uid.isEmpty() && Integer.parseInt(uid) != 0;
    }

    private BaseImage baseOf(Draft stage) {
        Map<String, Draft> byName = new LinkedHashMap<>();
        stages.stream().filter(s -> s.name != null).forEach(s -> byName.put(s.name, s));
        Draft current = stage;
        while (current.base == null) {
            current = byName.get(current.fromStage);
        }
        return current.base;
    }

    private Draft current() {
        return stages.getLast();
    }

    private ImageBuilder add(Instruction instruction) {
        current().instructions.add(instruction);
        return this;
    }
}
