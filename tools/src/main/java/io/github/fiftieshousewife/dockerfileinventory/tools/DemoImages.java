package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

// Builds for linux/amd64 because lineage reads amd64 only. JCR speaks plain HTTP here, so every pull and push skips
// TLS verification.
final class DemoImages {

    static final String BUILDAH_STORAGE = "inventory-demo-buildah";

    private final Commands docker;
    private final String registry;
    private final String password;
    private final String skopeoImage;
    private final String buildahImage;
    private final String nativePlatform;

    DemoImages(Commands docker, String registry, String password, String skopeoImage, String buildahImage,
               String nativePlatform) {
        this.docker = docker;
        this.registry = registry;
        this.password = password;
        this.skopeoImage = skopeoImage;
        this.buildahImage = buildahImage;
        this.nativePlatform = nativePlatform;
    }

    void mirror(List<String> lines, Path log) {
        lines.stream().map(line -> line.trim().split("\\s+")).filter(fields -> fields.length >= 2)
                .forEach(fields -> copy(fields[0], fields[1], log));
    }

    // --override-arch makes skopeo pick a manifest list's amd64 image.
    void copy(String reference, String name, Path log) {
        boolean copied = docker.run(List.of("docker", "run", "--rm", "--network", JcrContainers.NETWORK, skopeoImage,
                "copy", "-q", "--preserve-digests", "--override-os", "linux", "--override-arch", "amd64",
                "--dest-tls-verify=false", "--dest-creds", "admin:" + password, "docker://" + reference,
                "docker://" + registry + "/" + name), null).ok();
        log(log, copied ? "pushed " + registry + "/" + name : "FAILED " + reference);
    }

    void clone(List<BuildPlan.Build> plan, Path src) {
        plan.stream().map(build -> Map.entry(build.repository(), build.commit())).distinct()
                .forEach(checkout -> checkout(checkout.getKey(), checkout.getValue(), src));
    }

    // sclorg's build contexts link into a submodule.
    void checkout(String repository, String commit, Path src) {
        Path dir = src.resolve(repository).resolve(commit);
        if (!Files.isDirectory(dir.resolve(".git"))) {
            docker.run("git", "clone", "-q", "--filter=blob:none", "--no-checkout", "https://github.com/"
                    + repository + ".git", dir.toString());
            docker.run("git", "-C", dir.toString(), "checkout", "-q", commit);
        }
        docker.run("git", "-C", dir.toString(), "submodule", "update", "-q", "--init", "--recursive");
    }

    void build(List<BuildPlan.Build> plan, Path out, Path log) {
        plan.stream().map(BuildPlan.Build::base).filter(base -> base.startsWith("quay.io/centos/")).distinct()
                .sorted().forEach(root -> mirrorRoot(root, out, log));
        plan.forEach(build -> buildAndPush(build, out, log));
    }

    void mirrorRoot(String root, Path out, Path log) {
        boolean mirrored = buildah(out, List.of("pull", "-q", "--platform", "linux/amd64", root))
                && buildah(out, List.of("tag", root, local(root))) && push(out, local(root));
        if (!mirrored) {
            log(log, "FAILED mirror " + root);
        }
    }

    void buildAndPush(BuildPlan.Build build, Path out, Path log) {
        String context = "/src/" + build.repository() + "/" + build.commit();
        String image = registry + "/" + build.name();
        boolean built = buildah(out, concat(List.of(List.of("build", "-q", "--layers=false", "--platform",
                "linux/amd64"), login(), List.of("--from", local(build.base()), "-f", context + "/" + build.path(),
                "-t", image, context)))) && push(out, image);
        log(log, built ? "pushed " + image : "FAILED " + build.name());
    }

    private boolean push(Path out, String image) {
        return buildah(out, concat(List.of(List.of("push", "-q"), login(), List.of(image))));
    }

    private List<String> login() {
        return List.of("--tls-verify=false", "--creds", "admin:" + password);
    }

    // sclorg tags its images c9s, so a FROM without a tag means that one.
    String local(String base) {
        String reference = base.replaceFirst("^quay\\.io/", "");
        return registry + "/" + (reference.contains(":") ? reference : reference + ":c9s");
    }

    // Buildah runs natively (as an emulated binary it cannot re-exec itself for RUN); its store is a volume, so each
    // step's container sees what the last one left.
    private boolean buildah(Path out, List<String> arguments) {
        return docker.run(concat(List.of(List.of("docker", "run", "--rm", "--privileged", "--platform",
                nativePlatform, "--network", JcrContainers.NETWORK, "-e", "STORAGE_DRIVER=vfs", "-v", out.resolve("src")
                + ":/src:ro", "-v", out + ":/work", "-v", BUILDAH_STORAGE + ":/var/lib/containers", buildahImage,
                "buildah"), arguments)), null).ok();
    }

    static List<String> concat(List<List<String>> parts) {
        return parts.stream().flatMap(List::stream).toList();
    }

    private static void log(Path log, String line) {
        try {
            Files.writeString(log, line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.out.println(line);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
