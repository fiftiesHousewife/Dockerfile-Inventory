package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * MinHash signatures of string sets, whose agreement estimates Jaccard similarity (D2). Each of the
 * {@link #HASHES} functions is {@code (a * x + b) mod p} over a 64-bit hash of the element, with {@code p} the
 * Mersenne prime 2^61 - 1; the coefficients come from a fixed seed, so signatures are the same on every run.
 */
final class MinHash {

    static final int HASHES = 128;

    private static final long PRIME = (1L << 61) - 1;
    /** 2^64 = 8 * 2^61, which is 8 mod 2^61 - 1; the high half of a product of two values under 2^61 is under 2^58. */
    private static final long TWO_TO_64_MOD_PRIME = 8;
    private static final long[] A = new long[HASHES];
    private static final long[] B = new long[HASHES];

    static {
        SplittableRandom random = new SplittableRandom(20261106L);
        for (int i = 0; i < HASHES; i++) {
            A[i] = 1 + random.nextLong(PRIME - 1);
            B[i] = random.nextLong(PRIME);
        }
    }

    private MinHash() {
    }

    static long[] signature(Set<String> set) {
        long[] signature = new long[HASHES];
        Arrays.fill(signature, Long.MAX_VALUE);
        for (String element : set) {
            long x = Long.remainderUnsigned(fnv1a(element), PRIME);
            for (int i = 0; i < HASHES; i++) {
                signature[i] = Math.min(signature[i], mulAddMod(A[i], x, B[i]));
            }
        }
        return signature;
    }

    static double estimate(long[] a, long[] b) {
        int agree = 0;
        for (int i = 0; i < HASHES; i++) {
            if (a[i] == b[i]) {
                agree++;
            }
        }
        return (double) agree / HASHES;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) {
            return 0;
        }
        long shared = a.stream().filter(b::contains).count();
        return (double) shared / union.size();
    }

    /** {@code (a * x + b) mod p} without overflow, by 128-bit multiplication. */
    private static long mulAddMod(long a, long x, long b) {
        long high = Math.multiplyHigh(a, x);
        long low = a * x;
        long product = Long.remainderUnsigned(low, PRIME) + high * TWO_TO_64_MOD_PRIME;
        return Long.remainderUnsigned(Long.remainderUnsigned(product, PRIME) + b, PRIME);
    }

    private static long fnv1a(String text) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
