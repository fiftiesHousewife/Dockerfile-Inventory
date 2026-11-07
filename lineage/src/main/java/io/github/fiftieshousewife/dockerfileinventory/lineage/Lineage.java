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
import java.util.Objects;
import java.util.stream.IntStream;

final class Lineage {

    private Lineage() {
    }

    static Map<String, Parent> of(Collection<Resolved.Node> nodes) {
        Map<String, List<String>> chains = new HashMap<>();
        nodes.forEach(node -> chains.put(node.configDigest(), chainIds(node.config().diffIds())));
        Map<String, List<Resolved.Node>> byChain = byChain(nodes, chains);
        Map<String, Parent> parents = new HashMap<>();
        nodes.forEach(node -> parents.put(node.configDigest(),
                parent(node, chains.get(node.configDigest()), byChain)));
        return parents;
    }

    static Map<String, List<Resolved.Node>> byChain(Collection<Resolved.Node> nodes, Map<String, List<String>> chains) {
        Map<String, List<Resolved.Node>> byChain = new HashMap<>();
        nodes.stream().filter(node -> !chains.get(node.configDigest()).isEmpty()).forEach(node -> byChain
                .computeIfAbsent(chains.get(node.configDigest()).getLast(), k -> new ArrayList<>()).add(node));
        return byChain;
    }

    // Equal layers count only for an image whose history is a strict prefix of this one's: a child that adds only
    // USER, LABEL or ENV makes no layer, and Buildah copies the base's history into the child unchanged.
    static Parent parent(Resolved.Node node, List<String> chain, Map<String, List<Resolved.Node>> byChain) {
        if (chain.isEmpty()) {
            return new Parent.Root();
        }
        List<Resolved.Node> sameLayers = precedingHistory(node, byChain.get(chain.getLast()));
        if (!sameLayers.isEmpty()) {
            return closest(sameLayers);
        }
        return IntStream.iterate(chain.size() - 1, length -> length >= 1, length -> length - 1)
                .mapToObj(length -> byChain.get(chain.get(length - 1)))
                .filter(Objects::nonNull)
                .findFirst()
                .map(candidates -> nearestOf(node, candidates))
                .orElseGet(Parent.Root::new);
    }

    static Parent nearestOf(Resolved.Node node, List<Resolved.Node> candidates) {
        List<Resolved.Node> preceding = precedingHistory(node, candidates);
        return closest(preceding.isEmpty() ? candidates : preceding);
    }

    static List<Resolved.Node> precedingHistory(Resolved.Node node, List<Resolved.Node> candidates) {
        return candidates.stream()
                .filter(other -> isStrictPrefix(other.config().history(), node.config().history()))
                .toList();
    }

    static Parent closest(List<Resolved.Node> candidates) {
        int longest = candidates.stream().mapToInt(c -> c.config().history().size()).max().orElseThrow();
        List<String> nearest = candidates.stream()
                .filter(c -> c.config().history().size() == longest)
                .map(Resolved.Node::configDigest)
                .sorted()
                .toList();
        return nearest.size() == 1 ? new Parent.Of(nearest.getFirst()) : new Parent.Ambiguous(nearest);
    }

    static boolean isStrictPrefix(List<HistoryStep> prefix, List<HistoryStep> history) {
        return prefix.size() < history.size() && history.subList(0, prefix.size()).equals(prefix);
    }

    static List<String> chainIds(List<String> diffIds) {
        List<String> chain = new ArrayList<>();
        diffIds.forEach(diffId -> chain.add(chain.isEmpty() ? diffId : sha256(chain.getLast() + " " + diffId)));
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
