package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.List;
import java.util.Optional;

interface GitHub {

    List<String> organizations();

    List<Repository> repositories(String organization);

    Optional<Tree> tree(String repository, String sha, boolean recursive);

    List<BlobText> blobs(List<BlobRequest> requests);

    List<LastChange> lastChanges(List<ChangeRequest> requests);
}
