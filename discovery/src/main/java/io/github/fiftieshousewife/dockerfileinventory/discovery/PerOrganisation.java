package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class PerOrganisation implements GitHub {

    private final Function<String, GitHub> clients;
    private final Supplier<List<String>> organizations;

    PerOrganisation(Function<String, GitHub> clients, Supplier<List<String>> organizations) {
        this.clients = clients;
        this.organizations = organizations;
    }

    @Override
    public List<String> organizations() {
        return organizations.get();
    }

    @Override
    public List<Repository> repositories(String organization) {
        String org = organization.toLowerCase(Locale.ROOT);
        return clients.apply(org).repositories(org);
    }

    @Override
    public Optional<Tree> tree(String repository, String sha, boolean recursive) {
        return clients.apply(owner(repository)).tree(repository, sha, recursive);
    }

    @Override
    public List<BlobText> blobs(List<BlobRequest> requests) {
        return split(requests, BlobRequest::repository, GitHub::blobs);
    }

    @Override
    public List<LastChange> lastChanges(List<ChangeRequest> requests) {
        return split(requests, ChangeRequest::repository, GitHub::lastChanges);
    }

    <R, T> List<T> split(List<R> requests, Function<R, String> repository, BiFunction<GitHub, List<R>, List<T>> call) {
        Map<String, List<R>> byOwner = requests.stream().collect(Collectors.groupingBy(
                request -> owner(repository.apply(request)), LinkedHashMap::new, Collectors.toList()));
        return byOwner.entrySet().stream().flatMap(e -> call.apply(clients.apply(e.getKey()), e.getValue()).stream())
                .toList();
    }

    static String owner(String repository) {
        return repository.substring(0, repository.indexOf('/')).toLowerCase(Locale.ROOT);
    }
}
