package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.List;
import java.util.Optional;

interface GitHub {

    List<String> organizations();

    List<Repository> repositories(String organization);

    // A listing that holds each default branch's head (GraphQL's) has it already; REST's is asked for.
    default String headCommit(Repository repository) {
        return repository.headCommit();
    }

    Optional<Tree> tree(String repository, String sha, boolean recursive);

    List<BlobText> blobs(List<BlobRequest> requests);

    List<LastChange> lastChanges(List<ChangeRequest> requests);

    default int calls(Pending pending) {
        return pending.calls();
    }
}
