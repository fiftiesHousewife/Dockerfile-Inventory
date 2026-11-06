package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;

/**
 * What the effective rules need of one image, from lineage's {@code effective.jsonl}, {@code links.jsonl} and
 * {@code images.jsonl}: its effective user and the chain image that set it, its chain from itself to the root (each
 * with its linked Dockerfiles), and the root's image names.
 */
public record ImageView(String configDigest, String user, Group userSetBy, List<Group> chain, List<String> rootNames) {

    /** An image in the chain and the Dockerfiles (blobs) it links to; none when it has no link. */
    public record Group(String configDigest, List<String> blobShas) {

        public Group {
            blobShas = List.copyOf(blobShas);
        }
    }

    public ImageView {
        chain = List.copyOf(chain);
        rootNames = List.copyOf(rootNames);
    }
}
