package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;

record ImageView(String configDigest, String user, Group userSetBy, List<Group> chain, List<String> rootNames) {

    record Group(String configDigest, List<String> blobShas) {

        Group {
            blobShas = List.copyOf(blobShas);
        }
    }

    ImageView {
        chain = List.copyOf(chain);
        rootNames = List.copyOf(rootNames);
    }

    List<String> ownBlobs() {
        return chain.isEmpty() ? List.of() : chain.getFirst().blobShas();
    }

    String ownBlob() {
        List<String> own = ownBlobs();
        return own.isEmpty() ? null : own.getFirst();
    }

    List<String> userSetByBlobs() {
        return userSetBy == null ? List.of() : userSetBy.blobShas();
    }
}
