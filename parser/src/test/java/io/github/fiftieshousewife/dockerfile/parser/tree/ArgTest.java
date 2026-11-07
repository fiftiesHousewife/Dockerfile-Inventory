/*
 * Copyright 2026 the original author or authors.
 * Modified by Dockerfile Inventory, 2026: package renamed from org.openrewrite.docker.
 * Modified by Dockerfile Inventory, 2026: tests for several names, empty defaults and several values (issues 10, 12, 13).
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

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.fiftieshousewife.dockerfile.parser.Assertions.docker;

class ArgTest implements RewriteTest {

    @Test
    void simpleArg() {
        rewriteRun(
          docker(
            """
              FROM ubuntu:20.04
              ARG VERSION=1.0.0
              """,
            spec -> spec.afterRecipe(doc -> {
                var arg = (Docker.Arg) doc.getStages().getFirst().getInstructions().getLast();
                assertThat(arg.getName().getText()).isEqualTo("VERSION");
                assertThat(arg.getValue()).isNotNull();
                assertThat(((Docker.Literal) arg.getValue().getContents().getFirst()).getText()).isEqualTo("1.0.0");
            })
          )
        );
    }

    @Test
    void argWithoutValue() {
        rewriteRun(
          docker(
            """
              FROM ubuntu:20.04
              ARG VERSION
              """,
            spec -> spec.afterRecipe(doc -> {
                var arg = (Docker.Arg) doc.getStages().getFirst().getInstructions().getLast();
                assertThat(arg.getName().getText()).isEqualTo("VERSION");
                assertThat(arg.getValue()).isNull();
            })
          )
        );
    }

    @Test
    void argInstructions() {
        rewriteRun(
          docker(
            """
              ARG BASE_IMAGE=ubuntu:20.04
              FROM ${BASE_IMAGE}
              ARG VERSION
              """
          )
        );
    }

    @Test
    void globalArg() {
        rewriteRun(
          docker(
            """
              ARG VERSION=25
              FROM ubuntu:${VERSION}
              """,
            spec -> spec.afterRecipe(doc -> {
                assertThat(doc.getGlobalArgs()).hasSize(1);
                Docker.Arg globalArg = doc.getGlobalArgs().getFirst();
                assertThat(globalArg.getName().getText()).isEqualTo("VERSION");
            })
          )
        );
    }

    @Test
    void severalNamesWithoutValues() {
        rewriteRun(
          docker(
            """
              FROM ubi9
              ARG TARGETOS TARGETARCH \\
                  OTHER
              """,
            spec -> spec.afterRecipe(doc -> {
                var arg = (Docker.Arg) doc.getStages().getFirst().getInstructions().getLast();
                assertThat(arg.getName().getText()).isEqualTo("TARGETOS");
                assertThat(arg.getValue()).isNull();
                assertThat(arg.getAdditionalPairs()).extracting(pair -> pair.getName().getText())
                  .containsExactly("TARGETARCH", "OTHER");
                assertThat(arg.getAdditionalPairs()).allSatisfy(pair -> assertThat(pair.getValue()).isNull());
            })
          )
        );
    }

    @Test
    void emptyDefault() {
        rewriteRun(
          docker(
            """
              ARG HTTP_PROXY=
              FROM ubi9
              ARG A= B=
              RUN make
              """,
            spec -> spec.afterRecipe(doc -> {
                var global = doc.getGlobalArgs().getFirst();
                assertThat(global.getName().getText()).isEqualTo("HTTP_PROXY");
                assertThat(global.getValue()).isNotNull();
                assertThat(global.getValue().getContents()).isEmpty();
                var arg = (Docker.Arg) doc.getStages().getFirst().getInstructions().getFirst();
                assertThat(arg.getValue().getContents()).isEmpty();
                assertThat(arg.getAdditionalPairs().getFirst().getValue().getContents()).isEmpty();
            })
          )
        );
    }

    @Test
    void severalNamesWithValues() {
        rewriteRun(
          docker(
            """
              FROM ubi9
              ARG A=1 B="two words" C D=${A:-x}/y
              """,
            spec -> spec.afterRecipe(doc -> {
                var arg = (Docker.Arg) doc.getStages().getFirst().getInstructions().getLast();
                assertThat(arg.getName().getText()).isEqualTo("A");
                assertThat(((Docker.Literal) arg.getValue().getContents().getFirst()).getText()).isEqualTo("1");
                assertThat(arg.getAdditionalPairs()).extracting(pair -> pair.getName().getText())
                  .containsExactly("B", "C", "D");
                assertThat(arg.getAdditionalPairs().get(1).getValue()).isNull();
            })
          )
        );
    }
}
