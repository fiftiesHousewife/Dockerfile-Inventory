package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.List;
import java.util.Optional;

/** The GitHub Enterprise calls discovery needs. Tests use a fake; production uses {@link GitHubHttp}. */
public interface GitHub {

    List<String> organizations();

    List<Repository> repositories(String organization);

    /** The recursive tree at a commit, or empty when the commit does not exist or the repository is empty. */
    Optional<Tree> tree(String repository, String commitSha);

    /** Fetches blobs in one round trip. A blob GitHub cannot find is absent from the result. */
    List<BlobText> blobs(List<BlobRequest> requests);
}
