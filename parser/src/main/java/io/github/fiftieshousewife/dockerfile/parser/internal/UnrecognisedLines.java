/*
 * Copyright 2026 Dockerfile Inventory.
 * Added by Dockerfile Inventory, 2026: an unrecognised line is a syntax error, not part of an argument (issue 3).
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
package io.github.fiftieshousewife.dockerfile.parser.internal;

import io.github.fiftieshousewife.dockerfile.parser.internal.grammar.DockerLexer;
import io.github.fiftieshousewife.dockerfile.parser.internal.grammar.DockerParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

import java.util.ArrayList;
import java.util.List;

/**
 * Newlines are hidden from the grammar, so a line that starts with no instruction keyword is read as more arguments
 * of the instruction before. Docker ends an instruction at a newline unless a backslash continues it; inside a
 * continued instruction it drops whole-line comments and blank lines. Any other newline inside an instruction marks
 * a line glued on, reported here as a syntax error at that line's first token.
 */
public final class UnrecognisedLines {

    private UnrecognisedLines() {
    }

    public static void report(DockerParser parser, DockerParser.DockerfileContext dockerfile) {
        TokenStream tokens = parser.getTokenStream();
        for (ParserRuleContext instruction : instructions(dockerfile)) {
            for (int i = instruction.getStart().getTokenIndex() + 1; i < instruction.getStop().getTokenIndex(); i++) {
                if (isHiddenNewline(tokens.get(i)) && !continues(tokens, i)) {
                    Token next = nextVisible(tokens, i);
                    parser.notifyErrorListeners(next, "unrecognised line: " + line(tokens, next), null);
                    break;
                }
            }
        }
    }

    static List<ParserRuleContext> instructions(DockerParser.DockerfileContext dockerfile) {
        List<ParserRuleContext> instructions = new ArrayList<>(dockerfile.globalArgs().argInstruction());
        for (DockerParser.StageContext stage : dockerfile.stage()) {
            instructions.add(stage.fromInstruction());
            instructions.addAll(stage.stageInstruction());
        }
        return instructions;
    }

    // A newline continues the instruction after a line continuation, a blank line or a whole-line comment. A line
    // ending in \\ continues too: Docker takes its last backslash as the continuation, the lexer an escaped backslash.
    static boolean continues(TokenStream tokens, int newline) {
        Token before = previousNonSpace(tokens, newline);
        if (before == null) {
            return false;
        }
        return switch (before.getType()) {
            case DockerLexer.LINE_CONTINUATION -> true;
            case DockerLexer.NEWLINE -> before.getChannel() == Token.HIDDEN_CHANNEL && continues(tokens, before.getTokenIndex());
            case DockerLexer.COMMENT -> startsLine(tokens, before.getTokenIndex());
            default -> before.getText().endsWith("\\");
        };
    }

    static boolean startsLine(TokenStream tokens, int index) {
        Token before = previousNonSpace(tokens, index);
        return before != null && (before.getType() == DockerLexer.LINE_CONTINUATION
                || isHiddenNewline(before) && continues(tokens, before.getTokenIndex()));
    }

    static Token previousNonSpace(TokenStream tokens, int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (tokens.get(i).getType() != DockerLexer.WS) {
                return tokens.get(i);
            }
        }
        return null;
    }

    static boolean isHiddenNewline(Token token) {
        return token.getType() == DockerLexer.NEWLINE && token.getChannel() == Token.HIDDEN_CHANNEL;
    }

    static Token nextVisible(TokenStream tokens, int index) {
        int i = index + 1;
        while (tokens.get(i).getChannel() != Token.DEFAULT_CHANNEL) {
            i++;
        }
        return tokens.get(i);
    }

    // The rest of the glued line, as written, for the message
    static String line(TokenStream tokens, Token first) {
        StringBuilder line = new StringBuilder();
        for (int i = first.getTokenIndex(); i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (isHiddenNewline(token) || token.getType() == Token.EOF) {
                break;
            }
            line.append(token.getText());
        }
        return line.toString().strip();
    }
}
