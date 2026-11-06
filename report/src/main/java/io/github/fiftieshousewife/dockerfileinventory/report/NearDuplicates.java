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

/**
 * Pass 4: merges signature groups whose package set plus base family are nearly the same (D2). MinHash
 * signatures are split into {@link #BANDS} bands of {@link #ROWS} rows; groups sharing a band are candidates, and
 * a candidate pair merges only at an exact Jaccard of at least {@link #THRESHOLD}. Merging is transitive. A group
 * with no packages has nothing to compare and never merges here (D3).
 */
public final class NearDuplicates {

    static final double THRESHOLD = 0.8;
    static final int BANDS = 32;
    static final int ROWS = MinHash.HASHES / BANDS;

    private NearDuplicates() {
    }

    public static List<Group> of(List<Group> groups) {
        List<Group> sorted = groups.stream().sorted(Comparator.comparing(Group::key)).toList();
        List<Set<String>> features = sorted.stream().map(NearDuplicates::features).toList();
        int[] parent = new int[sorted.size()];
        Arrays.setAll(parent, i -> i);
        Map<String, List<Integer>> buckets = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).packages().isEmpty()) {
                continue;
            }
            long[] signature = MinHash.signature(features.get(i));
            for (int band = 0; band < BANDS; band++) {
                long[] rows = Arrays.copyOfRange(signature, band * ROWS, (band + 1) * ROWS);
                buckets.computeIfAbsent(band + ":" + Arrays.toString(rows), k -> new ArrayList<>()).add(i);
            }
        }
        for (List<Integer> bucket : buckets.values()) {
            for (int x = 0; x < bucket.size(); x++) {
                for (int y = x + 1; y < bucket.size(); y++) {
                    int i = bucket.get(x);
                    int j = bucket.get(y);
                    if (MinHash.jaccard(features.get(i), features.get(j)) >= THRESHOLD) {
                        union(parent, i, j);
                    }
                }
            }
        }
        Map<Integer, List<Group>> merged = new TreeMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            merged.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(sorted.get(i));
        }
        return merged.values().stream().map(NearDuplicates::merge).sorted(Comparator.comparing(Group::key)).toList();
    }

    private static Set<String> features(Group group) {
        Set<String> features = new TreeSet<>(group.packages());
        features.add("base:" + group.family());
        return features;
    }

    /** The largest member names the merged group and gives its package set and family. */
    private static Group merge(List<Group> members) {
        Group largest = members.stream()
                .max(Comparator.comparingInt(Group::locations).thenComparing(Group::key, Comparator.reverseOrder()))
                .orElseThrow();
        return new Group(largest.key(), members.stream().flatMap(g -> g.blobShas().stream()).toList(),
                members.stream().mapToInt(Group::locations).sum(), largest.packages(), largest.family());
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int i, int j) {
        int a = find(parent, i);
        int b = find(parent, j);
        if (a != b) {
            parent[Math.max(a, b)] = Math.min(a, b);
        }
    }
}
