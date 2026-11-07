/*
 * Copyright 2026 the original author or authors.
 * Modified by Dockerfile Inventory, 2026: package renamed from org.openrewrite.docker.
 * Modified by Dockerfile Inventory, 2026: tests for comment lines inside continuations and strings, and after the first instruction (issues 1, 2).
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
package io.github.fiftieshousewife.dockerfile.parser.tree;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static io.github.fiftieshousewife.dockerfile.parser.Assertions.docker;
import static org.assertj.core.api.Assertions.assertThat;

class CommentTest implements RewriteTest {

    @Test
    void commentsAtTop() {
        rewriteRun(
          docker(
            """
              # This is a comment
              # Another comment line
              FROM ubuntu:20.04
              """
          )
        );
    }

    @Test
    void commentsInline() {
        rewriteRun(
          docker(
            """
              FROM ubuntu:20.04  # Base image
              RUN apt-get update  # Update packages
              """
          )
        );
    }

    @Test
    void commentsBetweenInstructions() {
        rewriteRun(
          docker(
            """
              FROM ubuntu:20.04
              # Update and install dependencies
              RUN apt-get update
              # Install curl
              RUN apt-get install -y curl
              """
          )
        );
    }

    @Test
    void emptyLinesAndComments() {
        rewriteRun(
          docker(
            """
              # Base image
              FROM ubuntu:20.04
              
              # System updates
              RUN apt-get update
              
              # Install packages
              RUN apt-get install -y curl wget
              """
          )
        );
    }

    @Test
    void trailingCommentAfterImage() {
        rewriteRun(
          docker(
            """
              FROM 'ubuntu:22.04' # Trailing comment
              RUN apt-get update
              """
          )
        );
    }

    @Test
    void commentLineWithAnApostropheInsideAContinuedRun() {
        rewriteRun(
          docker(
            """
              FROM ubi9
              RUN echo a && \\
              # because it's tested better \\
                  echo b
              RUN echo c && \\
                # it's here too
                  echo d
              """,
            spec -> spec.afterRecipe(doc -> {
                var instructions = doc.getStages().getFirst().getInstructions();
                assertThat(instructions).hasExactlyElementsOfTypes(Docker.Run.class, Docker.Run.class);
                var run = (Docker.ShellForm) ((Docker.Run) instructions.getFirst()).getCommand();
                assertThat(run.getArgument().getText()).startsWith("echo a").endsWith("echo b");
            })
          )
        );
    }

    @Test
    void keyValueCommentAfterTheFirstInstructionIsAComment() {
        rewriteRun(
          docker(
            """
              # syntax=docker/dockerfile:1
              FROM ubi9
              # a=b
              # UID=1001 && GID=0
              # syntax=docker/dockerfile:1
              RUN echo
              """,
            spec -> spec.afterRecipe(doc -> {
                var run = doc.getStages().getFirst().getInstructions().getFirst();
                assertThat(run).isInstanceOf(Docker.Run.class);
                assertThat(run.getPrefix().getComments()).extracting(Comment::getText)
                  .containsExactly("# a=b", "# UID=1001 && GID=0", "# syntax=docker/dockerfile:1");
            })
          )
        );
    }

    @Test
    void commentLineWithAQuoteInsideAQuotedStringSpanningLines() {
        rewriteRun(
          docker(
            """
              FROM ubi9
              RUN conf=' \\
                  A=y \\
              # we've removed B here \\
              # "C" doesn't build \\
                  D=y \\
              '; \\
                  echo "$conf \\
                # the "E" setting
                  F"; \\
                  echo 'done'
              RUN echo next
              """,
            spec -> spec.afterRecipe(doc -> {
                var instructions = doc.getStages().getFirst().getInstructions();
                assertThat(instructions).hasExactlyElementsOfTypes(Docker.Run.class, Docker.Run.class);
                var run = (Docker.ShellForm) ((Docker.Run) instructions.getFirst()).getCommand();
                assertThat(run.getArgument().getText()).startsWith("conf=' ").endsWith("echo 'done'");
            })
          )
        );
    }
}
