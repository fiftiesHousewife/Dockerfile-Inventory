package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;

class InMemoryCompilerTest {

    @Test
    void aClassCompilesToItsBytesWithNoErrors() {
        InMemoryCompiler.Compiled compiled = InMemoryCompiler.compile(Map.of("roundtrip.A",
                "package roundtrip; public final class A { public static int one() { return 1; } }"));

        assertAll(
                () -> assertThat(compiled.errors()).isEmpty(),
                () -> assertThat(compiled.classes()).containsOnlyKeys("roundtrip.A"));
    }

    @Test
    void aClassThatDoesNotCompileIsNamedWithItsErrorAndTheOthersStillCompile() {
        InMemoryCompiler.Compiled compiled = InMemoryCompiler.compile(Map.of(
                "roundtrip.Good", "package roundtrip; public final class Good { }",
                "roundtrip.Bad", "package roundtrip; public final class Bad { int x = \"text\"; }"));

        assertAll(
                () -> assertThat(compiled.classes()).containsOnlyKeys("roundtrip.Good"),
                () -> assertThat(compiled.errors()).containsOnlyKeys("roundtrip.Bad"),
                () -> assertThat(compiled.errors().get("roundtrip.Bad")).contains("incompatible types"));
    }

    @Test
    void anErrorWithoutASourceIsKeptApartAndAWarningIsNotAnError() {
        assertThat(InMemoryCompiler.errors(List.of(diagnostic(Diagnostic.Kind.ERROR, "no compiler"),
                diagnostic(Diagnostic.Kind.WARNING, "deprecated")))).containsExactly(entry("", "no compiler"));
    }

    private static Diagnostic<JavaFileObject> diagnostic(Diagnostic.Kind kind, String message) {
        return new Diagnostic<>() {
            public Kind getKind() {
                return kind;
            }

            public JavaFileObject getSource() {
                return null;
            }

            public long getPosition() {
                return NOPOS;
            }

            public long getStartPosition() {
                return NOPOS;
            }

            public long getEndPosition() {
                return NOPOS;
            }

            public long getLineNumber() {
                return NOPOS;
            }

            public long getColumnNumber() {
                return NOPOS;
            }

            public String getCode() {
                return null;
            }

            public String getMessage(Locale locale) {
                return message;
            }
        };
    }
}
