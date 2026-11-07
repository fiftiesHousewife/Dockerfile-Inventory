/*
 * Copyright 2026 the original author or authors.
 * Modified by Dockerfile Inventory, 2026: package renamed from org.openrewrite.docker.
 * Modified by Dockerfile Inventory, 2026: a test for an empty ENV value (issue 5).
 * Modified by Dockerfile Inventory, 2026: a test for the legacy ENV KEY = value (issue 15).
 * Modified by Dockerfile Inventory, 2026: a test for variables in an ENV value (issue 6).
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static io.github.fiftieshousewife.dockerfile.parser.Assertions.docker;

class EnvTest implements RewriteTest {

    @Test
    void envSingleLine() {
        rewriteRun(
          docker(
            """
              FROM ubuntu:20.04
              ENV NODE_VERSION=18.0.0
              ENV PATH=/usr/local/bin:$PATH
              """,
            spec -> spec.afterRecipe(doc -> {
                List<Docker.Instruction> instructions = doc.getStages().getFirst().getInstructions();
                var env1 = (Docker.Env) instructions.getFirst();
                assertThat(env1.getPairs()).hasSize(1);
                assertThat(env1.getPairs().getFirst().getKey().getText()).isEqualTo("NODE_VERSION");
                assertThat(((Docker.Literal) env1.getPairs().getFirst().getValue().getContents().getFirst()).getText()).isEqualTo("18.0.0");
            })
          )
        );
    }

    @Test
    void envMultiplePairs() {
        rewriteRun(
          docker(
            """
              FROM ubuntu:20.04
              ENV NODE_VERSION=18.0.0 NPM_VERSION=9.0.0
              """,
            spec -> spec.afterRecipe(doc -> {
                var env = (Docker.Env) doc.getStages().getFirst().getInstructions().getLast();
                assertThat(env.getPairs()).hasSize(2);
                assertThat(env.getPairs().get(0).getKey().getText()).isEqualTo("NODE_VERSION");
                assertThat(((Docker.Literal) env.getPairs().get(0).getValue().getContents().getFirst()).getText()).isEqualTo("18.0.0");
                assertThat(env.getPairs().get(1).getKey().getText()).isEqualTo("NPM_VERSION");
                assertThat(((Docker.Literal) env.getPairs().get(1).getValue().getContents().getFirst()).getText()).isEqualTo("9.0.0");
            })
          )
        );
    }

    @Test
    void envOldStyleSpaceSeparated() {
        rewriteRun(
          docker(
            """
              FROM ubuntu:20.04
              ENV NODE_VERSION 18.0.0
              """,
            spec -> spec.afterRecipe(doc -> {
                List<Docker.Instruction> instructions = doc.getStages().getFirst().getInstructions();
                var env1 = (Docker.Env) instructions.getFirst();
                assertThat(env1.getPairs()).hasSize(1);
                assertThat(env1.getPairs().getFirst().isHasEquals()).isFalse();
                assertThat(env1.getPairs().getFirst().getKey().getText()).isEqualTo("NODE_VERSION");
                assertThat(((Docker.Literal) env1.getPairs().getFirst().getValue().getContents().getFirst()).getText()).isEqualTo("18.0.0");
            })
          )
        );
    }

    @Test
    void envWithKeywordName() {
        // Test ENV where key is a Docker keyword (like SHELL)
        rewriteRun(
          docker(
            """
              FROM alpine:latest
              ENV SHELL /usr/bin/zsh
              """
          )
        );
    }

    @Test
    void emptyValue() {
        rewriteRun(
          docker(
            """
              FROM ubi9
              ENV SECRET_KEY= OTHER=1
              ENV LAST=
              """,
            spec -> spec.afterRecipe(doc -> {
                var env = (Docker.Env) doc.getStages().getFirst().getInstructions().getFirst();
                assertThat(env.getPairs()).extracting(pair -> pair.getKey().getText()).containsExactly("SECRET_KEY", "OTHER");
                assertThat(env.getPairs().getFirst().isHasEquals()).isTrue();
                assertThat(env.getPairs().getFirst().getValue().getContents()).isEmpty();
                var last = (Docker.Env) doc.getStages().getFirst().getInstructions().getLast();
                assertThat(last.getPairs().getFirst().getValue().getContents()).isEmpty();
            })
          )
        );
    }

    @Test
    void legacyFormWithSpacesAroundEquals() {
        rewriteRun(
          docker(
            """
              FROM ubi9
              ENV PERL_LWP_SSL_VERIFY_HOSTNAME = 0
              WORKDIR /home
              """,
            spec -> spec.afterRecipe(doc -> {
                var env = (Docker.Env) doc.getStages().getFirst().getInstructions().getFirst();
                assertThat(env.getPairs()).singleElement().satisfies(pair -> {
                    assertThat(pair.getKey().getText()).isEqualTo("PERL_LWP_SSL_VERIFY_HOSTNAME");
                    assertThat(pair.isHasEquals()).isFalse();
                    assertThat(((Docker.Literal) pair.getValue().getContents().getFirst()).getText()).isEqualTo("= 0");
                });
            })
          )
        );
    }

    @Test
    void variablesInAValueAreSplitOut() {
        rewriteRun(
          docker(
            """
              FROM ubi9
              ENV PASSWORD_FILE=$SECRETS/db HOME="/home/$USER" PATH=${HOME}/bin:$PATH
              """,
            spec -> spec.afterRecipe(doc -> {
                var env = (Docker.Env) doc.getStages().getFirst().getInstructions().getFirst();
                var passwordFile = env.getPairs().getFirst().getValue().getContents();
                assertThat(passwordFile).hasSize(2);
                assertThat(passwordFile.getFirst()).isInstanceOfSatisfying(Docker.EnvironmentVariable.class,
                  variable -> assertThat(variable.getName()).isEqualTo("SECRETS"));
                assertThat(((Docker.Literal) passwordFile.getLast()).getText()).isEqualTo("/db");
                assertThat(env.getPairs().get(1).getValue().getContents()).singleElement()
                  .isInstanceOfSatisfying(Docker.Literal.class, literal -> assertThat(literal.getText()).isEqualTo("\"/home/$USER\""));
                assertThat(env.getPairs().get(2).getValue().getContents()).extracting(c -> c.getClass().getSimpleName())
                  .containsExactly("EnvironmentVariable", "Literal", "EnvironmentVariable");
            })
          )
        );
    }
}
