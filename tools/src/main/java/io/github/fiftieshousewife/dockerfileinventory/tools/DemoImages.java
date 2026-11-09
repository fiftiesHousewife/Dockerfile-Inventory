package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// The lineage demo's images in JCR: published ones copied unchanged with skopeo, or the plan's Dockerfiles built with
// Buildah for linux/amd64 (lineage reads amd64 only), each FROM the image built before it. JCR speaks plain HTTP
// here, so every pull and push skips TLS verification.
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

    // One line per image: its reference and its name in JCR; skopeo picks a manifest list's amd64 image.
    void mirror(List<String> lines, Path log) {
        for (String line : lines) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 2) {
                continue;
            }
            boolean copied = docker.run(List.of("docker", "run", "--rm", "--network", JcrContainers.NETWORK,
                    skopeoImage, "copy", "-q", "--preserve-digests", "--override-os", "linux", "--override-arch",
                    "amd64", "--dest-tls-verify=false", "--dest-creds", "admin:" + password, "docker://" + fields[0],
                    "docker://" + registry + "/" + fields[1]), null).ok();
            log(log, copied ? "pushed " + registry + "/" + fields[1] : "FAILED " + fields[0]);
        }
    }

    // Each repository once, at the commit discovery scanned; sclorg's build contexts link into a submodule.
    void clone(List<BuildPlan.Build> plan, Path src) {
        Set<String> seen = new LinkedHashSet<>();
        for (BuildPlan.Build build : plan) {
            if (!seen.add(build.repository() + "@" + build.commit())) {
                continue;
            }
            Path dir = src.resolve(build.repository()).resolve(build.commit());
            if (!Files.isDirectory(dir.resolve(".git"))) {
                docker.run("git", "clone", "-q", "--filter=blob:none", "--no-checkout", "https://github.com/"
                        + build.repository() + ".git", dir.toString());
                docker.run("git", "-C", dir.toString(), "checkout", "-q", build.commit());
            }
            docker.run("git", "-C", dir.toString(), "submodule", "update", "-q", "--init", "--recursive");
        }
    }

    void build(List<BuildPlan.Build> plan, Path out, Path log) {
        List<String> login = List.of("--tls-verify=false", "--creds", "admin:" + password);
        plan.stream().map(BuildPlan.Build::base).filter(base -> base.startsWith("quay.io/centos/")).distinct()
                .sorted().forEach(root -> {
                    boolean mirrored = buildah(out, List.of("pull", "-q", "--platform", "linux/amd64", root))
                            && buildah(out, List.of("tag", root, local(root)))
                            && buildah(out, concat(List.of("push", "-q"), login, List.of(local(root))));
                    if (!mirrored) {
                        log(log, "FAILED mirror " + root);
                    }
                });
        for (BuildPlan.Build build : plan) {
            String context = "/src/" + build.repository() + "/" + build.commit();
            String image = registry + "/" + build.name();
            boolean built = buildah(out, concat(List.of("build", "-q", "--layers=false", "--platform", "linux/amd64"),
                    login, List.of("--from", local(build.base()), "-f", context + "/" + build.path(), "-t", image,
                            context))) && buildah(out, concat(List.of("push", "-q"), login, List.of(image)));
            log(log, built ? "pushed " + image : "FAILED " + build.name());
        }
    }

    // The image a FROM resolves to inside JCR: the public name without quay.io/, sclorg's tag c9s when it has none.
    String local(String base) {
        String reference = base.replaceFirst("^quay\\.io/", "");
        return registry + "/" + (reference.contains(":") ? reference : reference + ":c9s");
    }

    // Buildah runs natively (as an emulated binary it cannot re-exec itself for RUN); its store is a volume, so each
    // step's container sees what the last one left.
    private boolean buildah(Path out, List<String> arguments) {
        return docker.run(concat(List.of("docker", "run", "--rm", "--privileged", "--platform", nativePlatform,
                "--network", JcrContainers.NETWORK, "-e", "STORAGE_DRIVER=vfs", "-v", out.resolve("src") + ":/src:ro",
                "-v", out + ":/work", "-v", BUILDAH_STORAGE + ":/var/lib/containers", buildahImage, "buildah"),
                arguments), null).ok();
    }

    @SafeVarargs
    private static List<String> concat(List<String>... parts) {
        List<String> all = new ArrayList<>();
        for (List<String> part : parts) {
            all.addAll(part);
        }
        return all;
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
