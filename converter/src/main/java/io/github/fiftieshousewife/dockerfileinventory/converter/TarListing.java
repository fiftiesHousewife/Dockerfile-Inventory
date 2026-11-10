package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

final class TarListing {

    private TarListing() {
    }

    static SortedMap<String, FileEntry> of(InputStream tar) {
        SortedMap<String, FileEntry> entries = new TreeMap<>();
        try (TarArchiveInputStream in = new TarArchiveInputStream(unclosed(tar))) {
            for (TarArchiveEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(path(entry.getName()), entry(entry, in, entries));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }

    static FileEntry entry(TarArchiveEntry entry, InputStream content,
            SortedMap<String, FileEntry> earlier) throws IOException {
        String mode = Integer.toOctalString(entry.getMode() & 07777);
        String uid = Long.toString(entry.getLongUserId());
        String gid = Long.toString(entry.getLongGroupId());
        if (entry.isLink()) {
            FileEntry target = earlier.get(path(entry.getLinkName()));
            return new FileEntry("f", mode, uid, gid, "", target == null ? null : target.sha256());
        }
        String type = type(entry);
        return new FileEntry(type, mode, uid, gid, entry.isSymbolicLink() ? entry.getLinkName() : "",
                type.equals("f") ? sha256(content) : null);
    }

    // find's %y letters.
    static String type(TarArchiveEntry entry) {
        if (entry.isDirectory()) {
            return "d";
        }
        if (entry.isSymbolicLink()) {
            return "l";
        }
        if (entry.isCharacterDevice()) {
            return "c";
        }
        if (entry.isBlockDevice()) {
            return "b";
        }
        return entry.isFIFO() ? "p" : "f";
    }

    private static InputStream unclosed(InputStream stream) {
        return new FilterInputStream(stream) {
            @Override
            public void close() {
            }
        };
    }

    static String path(String name) {
        String path = name.startsWith("./") ? name.substring(1) : name.startsWith("/") ? name : "/" + name;
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static String sha256(InputStream content) throws IOException {
        try {
            DigestInputStream digest = new DigestInputStream(content, MessageDigest.getInstance("SHA-256"));
            digest.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(digest.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
