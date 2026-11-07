/*
 * Copyright 2026 the original author or authors.
 * Modified by Dockerfile Inventory, 2026: package renamed from org.openrewrite.docker.
 * Modified by Dockerfile Inventory, 2026: a test for a line holding only a backslash (issue 7).
 * Modified by Dockerfile Inventory, 2026: tests for unrecognised lines (issue 3).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.fiftieshousewife.dockerfile.parser;

import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import org.openrewrite.test.RewriteTest;

import java.util.ArrayList;
import java.util.List;

import static io.github.fiftieshousewife.dockerfile.parser.Assertions.docker;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * General integration tests for the Docker parser.
 * Instruction-specific tests are located in the tree package.
 */
class DockerParserTest implements RewriteTest {

    @Test
    void multipleInstructions() {
        rewriteRun(
          docker(
            """
              FROM ubuntu:20.04
              RUN apt-get update
              RUN apt-get install -y curl
              """
          )
        );
    }

    @Test
    void multiLineRunWithContinuation() {
        rewriteRun(
          docker(
            """
              FROM alpine:latest
              RUN apk --no-cache add \\
              \topenvpn

              RUN mkdir /usr/share/openvpn
              RUN cp /etc/openvpn/* /usr/share/openvpn/

              WORKDIR /etc/openvpn
              ENTRYPOINT ["openvpn"]
              """
          )
        );
    }

    @Test
    void comprehensiveDockerfile() {
        rewriteRun(
          docker(
            """
              # syntax=docker/dockerfile:1

              # Build stage
              FROM golang:1.20 AS builder
              WORKDIR /build
              COPY go.mod go.sum ./
              RUN go mod download
              COPY . .
              RUN CGO_ENABLED=0 go build -o app

              # Runtime stage
              FROM alpine:latest
              RUN apk add --no-cache ca-certificates
              WORKDIR /app
              COPY --from=builder /build/app .
              EXPOSE 8080
              USER nobody
              ENTRYPOINT ["./app"]
              """
          )
        );
    }

    @Test
    void caseInsensitiveInstructions() {
        rewriteRun(
          docker(
            """
              from ubuntu:20.04
              run apt-get update
              copy app.jar /app/
              Env MY_VAR=value
              expose 8080
              Cmd ["./app"]
              """
          )
        );
    }

    @Test
    void multipleJsonArrayWhitespaceStyles() {
        // Test different whitespace styles in JSON arrays are preserved
        rewriteRun(
          docker(
            """
              FROM ubuntu:20.04
              CMD ["no-spaces"]
              ENTRYPOINT [ "with-spaces" ]
              """
          )
        );
    }

    @Test
    void lineHoldingOnlyABackslashBeforeAnInstruction() {
        rewriteRun(
          docker(
            """
              FROM ubi9
              COPY a /b
               \\
              RUN A="x"
              EXPOSE 8080
              \\
              RUN make
              """,
            spec -> spec.afterRecipe(doc -> {
                var instructions = doc.getStages().getFirst().getInstructions();
                assertThat(instructions).hasExactlyElementsOfTypes(Docker.Copy.class, Docker.Run.class,
                  Docker.Expose.class, Docker.Run.class);
                var copy = (Docker.CopyShellForm) ((Docker.Copy) instructions.getFirst()).getForm();
                assertThat(((Docker.Literal) copy.getDestination().getContents().getFirst()).getText()).isEqualTo("/b");
            })
          )
        );
    }

    @Test
    void unrecognisedLineIsASyntaxError() {
        List<Throwable> errors = new ArrayList<>();

        DockerParser.builder().build()
          .parse(new InMemoryExecutionContext(errors::add), "FROM ubi9\nRUNN echo typo\n")
          .toList();

        assertThat(errors).singleElement().satisfies(error ->
          assertThat(error.getMessage()).contains("line 2:0").contains("RUNN echo typo"));
    }

    @Test
    void continuedLinesBlankLinesAndWholeLineCommentsInsideAnInstructionAreNotUnrecognised() {
        List<Throwable> errors = new ArrayList<>();

        DockerParser.builder().build()
          .parse(new InMemoryExecutionContext(errors::add), """
            FROM ubi9
            RUN echo a \\

                # a comment
                && echo b # trailing
            RUN <<EOF
            echo c
            EOF
            """)
          .toList();

        assertThat(errors).isEmpty();
    }

    @Test
    void lineAfterATrailingCommentIsUnrecognised() {
        List<Throwable> errors = new ArrayList<>();

        DockerParser.builder().build()
          .parse(new InMemoryExecutionContext(errors::add), "FROM ubi9\nRUN make # build\nCROSS_BUILD_COPY qemu /usr/bin/\n")
          .toList();

        assertThat(errors).singleElement().satisfies(error ->
          assertThat(error.getMessage()).contains("CROSS_BUILD_COPY qemu /usr/bin/"));
    }

    @Test
    void lineEndingInTwoBackslashesContinuesAsDockerReadsIt() {
        List<Throwable> errors = new ArrayList<>();

        DockerParser.builder().build()
          .parse(new InMemoryExecutionContext(errors::add), "FROM ubi9\nRUN dnf -y module enable nodejs && \\\\\ndnf -y install nodejs\n")
          .toList();

        assertThat(errors).isEmpty();
    }
}
