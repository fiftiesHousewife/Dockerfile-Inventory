package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

final class InMemoryCompiler {

    record Compiled(Map<String, byte[]> classes, Map<String, String> errors) {
    }

    private static final String UNATTRIBUTED = "";

    private InMemoryCompiler() {
    }

    // javac writes no class files when any unit has an error, so the failing ones are set aside and the rest
    // compile again.
    static Compiled compile(Map<String, String> sources) {
        Map<String, String> remaining = new TreeMap<>(sources);
        Map<String, String> errors = new TreeMap<>();
        while (!remaining.isEmpty()) {
            Compiled pass = once(remaining);
            if (pass.errors().isEmpty()) {
                return new Compiled(pass.classes(), errors);
            }
            String general = pass.errors().get(UNATTRIBUTED);
            if (general != null) {
                remaining.keySet().forEach(name -> errors.put(name, general));
                break;
            }
            errors.putAll(pass.errors());
            remaining.keySet().removeAll(pass.errors().keySet());
        }
        return new Compiled(Map.of(), errors);
    }

    static Compiled once(Map<String, String> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<String, byte[]> classes = new TreeMap<>();
        try (Outputs outputs = new Outputs(compiler.getStandardFileManager(diagnostics, null, null), classes)) {
            List<JavaFileObject> units = sources.entrySet().stream()
                    .<JavaFileObject>map(source -> new Source(source.getKey(), source.getValue()))
                    .toList();
            compiler.getTask(null, outputs, diagnostics, List.of("-proc:none", "-nowarn", "--class-path",
                    System.getProperty("java.class.path")), null, units).call();
        }
        return new Compiled(classes, errors(diagnostics.getDiagnostics()));
    }

    static Map<String, String> errors(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        Map<String, String> errors = new TreeMap<>();
        diagnostics.stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).forEach(d -> errors.putIfAbsent(
                d.getSource() instanceof Source source ? source.className : UNATTRIBUTED,
                d.getMessage(Locale.ROOT)));
        return errors;
    }

    private static final class Source extends SimpleJavaFileObject {

        private final String className;
        private final String code;

        Source(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.className = className;
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    private static final class Outputs extends ForwardingJavaFileManager<StandardJavaFileManager> {

        private final Map<String, byte[]> classes;

        Outputs(StandardJavaFileManager standard, Map<String, byte[]> classes) {
            super(standard);
            this.classes = classes;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className,
                JavaFileObject.Kind kind, FileObject sibling) {
            return new SimpleJavaFileObject(URI.create("bytes:///" + className), kind) {
                @Override
                public OutputStream openOutputStream() {
                    return new ByteArrayOutputStream() {
                        @Override
                        public void close() {
                            classes.put(className, toByteArray());
                        }
                    };
                }
            };
        }

        @Override
        public void close() {
        }
    }
}
