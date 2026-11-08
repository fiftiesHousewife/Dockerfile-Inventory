package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.reader.Conversion;
import io.github.fiftieshousewife.dockerfile.model.Dockerfile;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class JavaRoundTrip {

    private static final String PACKAGE = "roundtrip";

    record Candidate(String original, Conversion conversion) {
    }

    private JavaRoundTrip() {
    }

    static Map<String, RoundTrip> check(Map<String, Candidate> candidates) {
        Map<String, String> sources = candidates.entrySet().stream().collect(Collectors.toMap(
                entry -> className(entry.getKey()),
                entry -> JavaEmitter.emit(PACKAGE, "D" + entry.getKey(), entry.getValue().conversion(), List.of())));
        InMemoryCompiler.Compiled compiled = InMemoryCompiler.compile(sources);
        ClassLoader loader = new BytesLoader(compiled.classes(), JavaRoundTrip.class.getClassLoader());
        Map<String, RoundTrip> results = new TreeMap<>();
        candidates.forEach((sha, candidate) -> results.put(sha, outcome(sha, candidate, compiled, loader)));
        return results;
    }

    static RoundTrip outcome(String sha, Candidate candidate, InMemoryCompiler.Compiled compiled, ClassLoader loader) {
        String error = compiled.errors().get(className(sha));
        if (error != null) {
            return new RoundTrip(false, "the generated class does not compile: " + error);
        }
        try {
            return compare(candidate, (Dockerfile) loader.loadClass(className(sha)).getMethod("dockerfile")
                    .invoke(null));
        } catch (InvocationTargetException e) {
            return new RoundTrip(false, "the generated class throws: " + e.getCause().getMessage());
        } catch (ReflectiveOperationException e) {
            return new RoundTrip(false, "the generated class cannot be run: " + e.getMessage());
        }
    }

    static RoundTrip compare(Candidate candidate, Dockerfile built) {
        return built.equals(candidate.conversion().model())
                ? RoundTrip.check(candidate.original(), built)
                : new RoundTrip(false, "the generated class builds a different model");
    }

    private static String className(String sha) {
        return PACKAGE + ".D" + sha;
    }

    private static final class BytesLoader extends ClassLoader {

        private final Map<String, byte[]> classes;

        BytesLoader(Map<String, byte[]> classes, ClassLoader parent) {
            super(parent);
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
