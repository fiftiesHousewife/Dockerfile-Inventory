package io.github.fiftieshousewife.dockerfileinventory.discovery;

/**
 * What discovery's first pass, the listing and the tree walks, found before anything is fetched.
 *
 * @param repositories the repositories selected
 * @param withDockerfiles those holding a Dockerfile
 * @param locations the Dockerfiles' locations
 * @param distinct the distinct Dockerfiles among them
 * @param callsLeft the calls to GitHub still to make: blobs, YAML and last changes, 50 a call
 * @param output the folder discovery writes to
 */
public record FirstPass(int repositories, int withDockerfiles, int locations, int distinct, int callsLeft,
                        String output) {
}
