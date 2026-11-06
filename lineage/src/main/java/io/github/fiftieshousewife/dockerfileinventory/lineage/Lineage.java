package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * The image family tree, rebuilt from layers alone: an image's parent is the image whose layers are the longest
 * prefix of its own. Each image is indexed by the OCI chain ID of its full layer list, so finding a parent is one
 * lookup per layer rather than a comparison with every other image.
 */
public final class Lineage {

    private Lineage() {
    }

    public static Map<String, Parent> of(Collection<Resolved.Node> nodes) {
        Map<String, List<Resolved.Node>> byChain = new HashMap<>();
        Map<String, List<String>> chains = new HashMap<>();
        for (Resolved.Node node : nodes) {
            List<String> chain = chainIds(node.config().diffIds());
            chains.put(node.configDigest(), chain);
            byChain.computeIfAbsent(chain.getLast(), k -> new ArrayList<>()).add(node);
        }
        Map<String, Parent> parents = new HashMap<>();
        for (Resolved.Node node : nodes) {
            parents.put(node.configDigest(), parent(chains.get(node.configDigest()), byChain));
        }
        return parents;
    }

    private static Parent parent(List<String> chain, Map<String, List<Resolved.Node>> byChain) {
        for (int length = chain.size() - 1; length >= 1; length--) {
            List<Resolved.Node> candidates = byChain.get(chain.get(length - 1));
            if (candidates != null) {
                return new Parent.Of(candidates.getFirst().configDigest());
            }
        }
        return new Parent.Root();
    }

    /** OCI chain IDs: the first is the first diff ID; each next is {@code sha256(previous + " " + diffId)}. */
    static List<String> chainIds(List<String> diffIds) {
        List<String> chain = new ArrayList<>();
        for (String diffId : diffIds) {
            chain.add(chain.isEmpty() ? diffId : sha256(chain.getLast() + " " + diffId));
        }
        return chain;
    }

    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JVM has SHA-256", e);
        }
    }
}
