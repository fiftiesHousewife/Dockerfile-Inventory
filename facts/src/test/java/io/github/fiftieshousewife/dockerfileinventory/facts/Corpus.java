package io.github.fiftieshousewife.dockerfileinventory.facts;

final class Corpus {

    private Corpus() {
    }

    static String outcome(ParseResult result) {
        return switch (result) {
            case ParseResult.Parsed parsed -> parsed.retried() ? "retried" : "parsed";
            case ParseResult.Failed failed -> failed.message().startsWith(FactsParser.TEMPLATE) ? "template" : "failed";
        };
    }

    static String ratchet(String name, String field) {
        return "corpus case " + name + ": " + field + " is not what expected.json says; if a change meant this (a fix"
                + " turning a failure into a pass), update expected.json";
    }
}
