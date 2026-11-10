package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class ImageComparisons {

    static final String PARALLEL = "IMAGE_ROUND_TRIP_PARALLEL";

    record Timed(ImageComparison.Result result, long seconds) {
    }

    private ImageComparisons() {
    }

    static int parallel(Map<String, String> environment) {
        String value = environment.getOrDefault(PARALLEL, "4");
        if (!value.matches("[1-9][0-9]{0,8}")) {
            throw new IllegalArgumentException(PARALLEL + " must be a positive number, not " + value);
        }
        return Integer.parseInt(value);
    }

    static List<Timed> run(List<String> shas, Function<String, String> checkout, int threads,
                           Function<String, ImageComparison.Result> compare, LongSupplier nanos) {
        // Each comparison cleans its checkout and writes stubs into it: one checkout's Dockerfiles take turns.
        Map<String, List<Integer>> byCheckout = IntStream.range(0, shas.size()).boxed().collect(Collectors.groupingBy(
                i -> checkout.apply(shas.get(i)), LinkedHashMap::new, Collectors.toList()));
        Map<Integer, Timed> done = new ConcurrentHashMap<>();
        List<Future<?>> tasks;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            tasks = byCheckout.values().stream().<Future<?>>map(indexes -> pool.submit(() -> indexes.forEach(i ->
                    done.put(i, timed(shas.get(i), compare, nanos))))).toList();
        }
        tasks.forEach(ImageComparisons::rethrow);
        return IntStream.range(0, shas.size()).mapToObj(done::get).toList();
    }

    static void rethrow(Future<?> task) {
        try {
            task.get();
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static Timed timed(String sha, Function<String, ImageComparison.Result> compare, LongSupplier nanos) {
        long start = nanos.getAsLong();
        ImageComparison.Result result = compare.apply(sha);
        return new Timed(result, Math.round((nanos.getAsLong() - start) / 1e9));
    }
}
