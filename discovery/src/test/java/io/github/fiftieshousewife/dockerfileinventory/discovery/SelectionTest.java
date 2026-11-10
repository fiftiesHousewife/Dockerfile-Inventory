package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SelectionTest {

    private static Repository repo(String name) {
        return new Repository(name, "main", "abc", false, false, "2026-11-01T00:00:00Z");
    }

    private static final List<Repository> LISTED = List.of(repo("payments/api"), repo("payments/billing-ui"),
            repo("payments/Billing-Engine"), repo("payments/web"));

    private static List<String> kept(Selection selection) {
        return selection.of(LISTED).stream().map(Repository::nameWithOwner).toList();
    }

    @Test
    void withoutASelectionEveryRepositoryIsKept() {
        assertThat(kept(Selection.ALL)).hasSize(4);
    }

    @Test
    void aNamedRepositoryIsKeptWhateverTheCaseItIsNamedIn() {
        assertThat(kept(new Selection(List.of("Payments/API"), List.of()))).containsExactly("payments/api");
    }

    @Test
    void aStringKeepsEveryRepositoryWhoseNameHoldsItInAnyCase() {
        assertThat(kept(new Selection(List.of(), List.of("billing"))))
                .containsExactly("payments/billing-ui", "payments/Billing-Engine");
    }

    @Test
    void namesAndStringsTogetherKeepEitherOnce() {
        assertThat(kept(new Selection(List.of("payments/web", "payments/billing-ui"), List.of("billing"))))
                .containsExactly("payments/billing-ui", "payments/Billing-Engine", "payments/web");
    }

    @Test
    void theStringMatchesTheRepositorysNameNotItsOwner() {
        assertThatThrownBy(() -> kept(new Selection(List.of(), List.of("payments"))))
                .isInstanceOf(SelectionNotFound.class);
    }

    @Test
    void aNamedRepositoryTheListingDoesNotHoldStopsTheRunNamingIt() {
        assertThatThrownBy(() -> new Selection(List.of("payments/api", "payments/gone"), List.of()).of(LISTED))
                .isInstanceOf(SelectionNotFound.class)
                .hasMessage("no repository payments/gone in the organisations' listing (or the credential cannot "
                        + "read it)");
    }

    @Test
    void aSelectionThatKeepsNothingStopsTheRun() {
        assertThatThrownBy(() -> new Selection(List.of(), List.of("nothing-like-it")).of(LISTED))
                .isInstanceOf(SelectionNotFound.class)
                .hasMessage("no repository's name holds nothing-like-it");
    }

    @Test
    void theSelectionIsReadFromItsSettingsCommaSeparated() {
        assertAll(
                () -> assertThat(Selection.from(Map.of("REPOSITORIES", "a/b, c/d", "REPOSITORY_NAME_CONTAINS", "x")))
                        .isEqualTo(new Selection(List.of("a/b", "c/d"), List.of("x"))),
                () -> assertThat(Selection.from(Map.of())).isEqualTo(Selection.ALL),
                () -> assertThat(Selection.from(Map.of("REPOSITORIES", " "))).isEqualTo(Selection.ALL));
    }
}
