package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;

final class NearDuplicates {

    static final double THRESHOLD = 0.8;
    static final int BANDS = 32;
    static final int ROWS = MinHash.HASHES / BANDS;

    private NearDuplicates() {
    }

    static List<Group> of(List<Group> groups) {
        List<Group> sorted = groups.stream().sorted(Comparator.comparing(Group::key)).toList();
        List<Set<String>> features = sorted.stream().map(NearDuplicates::features).toList();
        int[] parent = IntStream.range(0, sorted.size()).toArray();
        buckets(sorted, features).values().forEach(bucket -> mergeSimilar(bucket, features, parent));
        Map<Integer, List<Group>> merged = new TreeMap<>();
        IntStream.range(0, sorted.size())
                .forEach(i -> merged.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(sorted.get(i)));
        return merged.values().stream().map(NearDuplicates::merge).sorted(Comparator.comparing(Group::key)).toList();
    }

    static Map<String, List<Integer>> buckets(List<Group> sorted, List<Set<String>> features) {
        Map<String, List<Integer>> buckets = new HashMap<>();
        IntStream.range(0, sorted.size())
                .filter(i -> !sorted.get(i).packages().isEmpty())
                .forEach(i -> bandKeys(MinHash.signature(features.get(i)))
                        .forEach(key -> buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(i)));
        return buckets;
    }

    static List<String> bandKeys(long[] signature) {
        return IntStream.range(0, BANDS)
                .mapToObj(band -> band + ":" + Arrays.toString(
                        Arrays.copyOfRange(signature, band * ROWS, (band + 1) * ROWS)))
                .toList();
    }

    static void mergeSimilar(List<Integer> bucket, List<Set<String>> features, int[] parent) {
        IntStream.range(0, bucket.size()).forEach(x -> IntStream.range(x + 1, bucket.size()).forEach(y -> {
            int i = bucket.get(x);
            int j = bucket.get(y);
            if (MinHash.jaccard(features.get(i), features.get(j)) >= THRESHOLD) {
                union(parent, i, j);
            }
        }));
    }

    static Set<String> features(Group group) {
        Set<String> features = new TreeSet<>(group.packages());
        features.add("base:" + group.family());
        return features;
    }

    static Group merge(List<Group> members) {
        Group largest = members.stream()
                .max(Comparator.comparingInt(Group::locations).thenComparing(Group::key, Comparator.reverseOrder()))
                .orElseThrow();
        return new Group(largest.key(), members.stream().flatMap(g -> g.blobShas().stream()).toList(),
                members.stream().mapToInt(Group::locations).sum(), largest.packages(), largest.family());
    }

    static int find(int[] parent, int i) {
        int root = i;
        while (parent[root] != root) {
            parent[root] = parent[parent[root]];
            root = parent[root];
        }
        return root;
    }

    static void union(int[] parent, int i, int j) {
        int a = find(parent, i);
        int b = find(parent, j);
        if (a != b) {
            parent[Math.max(a, b)] = Math.min(a, b);
        }
    }
}
