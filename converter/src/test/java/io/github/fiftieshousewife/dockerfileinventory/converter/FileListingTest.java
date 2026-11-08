package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FileListingTest {

    @Test
    void eachPathHasItsTypeModeOwnerTargetAndTheSumOfARegularFile() {
        String files = "d\t755\t0\t0\t.\t\nf\t644\t1001\t0\t./opt/app.jar\t\nl\t777\t0\t0\t./bin\tusr/bin\n";
        String sums = "ab12  ./opt/app.jar\n";

        assertThat(FileListing.of(files, sums)).containsExactly(
                Map.entry("/", new FileListing.Entry("d", "755", "0", "0", "", null)),
                Map.entry("/bin", new FileListing.Entry("l", "777", "0", "0", "usr/bin", null)),
                Map.entry("/opt/app.jar", new FileListing.Entry("f", "644", "1001", "0", "", "ab12")));
    }

    @Test
    void aListedPathIsMadeAbsoluteAndTheRootIsSlash() {
        assertAll(
                () -> assertThat(FileListing.path(".")).isEqualTo("/"),
                () -> assertThat(FileListing.path("./etc/passwd")).isEqualTo("/etc/passwd"));
    }
}
