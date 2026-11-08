package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

// An image's root filesystem as the comparison script lists it: find's "type mode uid gid path target" lines and
// sha256sum's "sum  path" lines, paths relative to the root ("./opt/app.jar").
final class FileListing {

    record Entry(String type, String mode, String uid, String gid, String target, String sha256) {
    }

    private FileListing() {
    }

    static SortedMap<String, Entry> of(String files, String sums) {
        Map<String, String> sumByPath = new TreeMap<>();
        sums.lines().filter(line -> !line.isBlank()).forEach(line -> {
            String[] parts = line.split(" {2}", 2);
            sumByPath.put(path(parts[1]), parts[0]);
        });
        SortedMap<String, Entry> entries = new TreeMap<>();
        files.lines().filter(line -> !line.isBlank()).forEach(line -> {
            String[] f = line.split("\t", -1);
            String path = path(f[4]);
            entries.put(path, new Entry(f[0], f[1], f[2], f[3], f[5], sumByPath.get(path)));
        });
        return entries;
    }

    static String path(String listed) {
        return listed.equals(".") ? "/" : listed.substring(1);
    }
}
