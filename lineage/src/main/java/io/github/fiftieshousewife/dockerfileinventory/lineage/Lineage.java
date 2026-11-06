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
            parents.put(node.configDigest(), parent(node, chains.get(node.configDigest()), byChain));
        }
        return parents;
    }

    /**
     * Longest layer prefix first. Equal layers count only for an image whose history is a strict prefix of this
     * one's: a child that adds only {@code USER}, {@code LABEL} or {@code ENV} makes no layer, and Buildah copies
     * the base's history into the child unchanged.
     */
    private static Parent parent(Resolved.Node node, List<String> chain, Map<String, List<Resolved.Node>> byChain) {
        List<Resolved.Node> sameLayers = byChain.get(chain.getLast()).stream()
                .filter(other -> isStrictPrefix(other.config().history(), node.config().history()))
                .toList();
        if (!sameLayers.isEmpty()) {
            return new Parent.Of(sameLayers.getFirst().configDigest());
        }
        for (int length = chain.size() - 1; length >= 1; length--) {
            List<Resolved.Node> candidates = byChain.get(chain.get(length - 1));
            if (candidates != null) {
                return new Parent.Of(candidates.getFirst().configDigest());
            }
        }
        return new Parent.Root();
    }

    static boolean isStrictPrefix(List<HistoryStep> prefix, List<HistoryStep> history) {
        return prefix.size() < history.size() && history.subList(0, prefix.size()).equals(prefix);
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
