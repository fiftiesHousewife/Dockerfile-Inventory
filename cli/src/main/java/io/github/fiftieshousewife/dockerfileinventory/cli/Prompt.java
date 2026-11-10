package io.github.fiftieshousewife.dockerfileinventory.cli;

import io.github.fiftieshousewife.dockerfileinventory.discovery.Confirmation;
import io.github.fiftieshousewife.dockerfileinventory.discovery.FirstPass;
import java.io.Console;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

// Asks on the terminal whether to fetch, once discovery's first pass has found what there is.
final class Prompt implements Confirmation {

    // The stages after discovery on the combined public estate (10 Nov): 73 s for 5,250 distinct Dockerfiles, and
    // about 10 s of starting up between them.
    static final double SECONDS_PER_DOCKERFILE = 0.014;
    static final long STARTING_UP = 10;

    private final PrintStream out;
    private final Supplier<String> answer;
    private final Map<String, String> env;

    Prompt(PrintStream out, Supplier<String> answer, Map<String, String> env) {
        this.out = out;
        this.answer = answer;
        this.env = Map.copyOf(env);
    }

    static Confirmation confirmation(Map<String, String> env, boolean terminal, PrintStream out) {
        if ("true".equalsIgnoreCase(env.get("ASSUME_YES"))) {
            return Confirmation.ALWAYS;
        }
        if (!terminal) {
            throw new UsageError("no terminal to ask on: give --yes to go on without asking");
        }
        Console console = System.console();
        return new Prompt(out, console::readLine, env);
    }

    @Override
    public boolean proceed(FirstPass pass) {
        double rate = Double.parseDouble(env.getOrDefault("GHE_MAX_REQUESTS_PER_SECOND", "1"));
        Path output = Path.of(pass.output());
        out.println(pass.repositories() + " repositories selected, " + pass.withDockerfiles() + " with Dockerfiles: "
                + pass.locations() + " Dockerfiles, " + pass.distinct() + " distinct");
        out.println(pass.callsLeft() + " more calls to GitHub at " + number(rate) + " a second, then the other "
                + "stages: " + inWords(likely(pass, rate)));
        out.println("output in " + (output.endsWith("discovery") && output.getParent() != null
                ? output.getParent() : output));
        out.print("Continue? [y/N] ");
        out.flush();
        String given = answer.get();
        return given != null && java.util.Set.of("y", "yes").contains(given.strip().toLowerCase(Locale.ROOT));
    }

    static Duration likely(FirstPass pass, double rate) {
        return Duration.ofSeconds(Math.round(pass.callsLeft() / rate) + STARTING_UP
                + Math.round(pass.distinct() * SECONDS_PER_DOCKERFILE));
    }

    static String inWords(Duration time) {
        long minutes = (time.toSeconds() + 59) / 60;
        if (time.toSeconds() < 60) {
            return "under a minute";
        }
        return minutes < 90 ? "about " + minutes + " minutes" : "about " + number(time.toMinutes() / 60.0) + " hours";
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(Math.round(value * 10) / 10.0);
    }
}
