// $antlr-format alignTrailingComments true, columnLimit 150, maxEmptyLinesToKeep 1, reflowComments false, useTab false
// $antlr-format allowShortRulesOnASingleLine true, allowShortBlocksOnASingleLine true, minEmptyLines 0, alignSemicolons ownLine
// $antlr-format alignColons trailing, singleLineOverrulesHangingColon true, alignLexerCommands true, alignLabels true, alignTrailers true

// Copyright the original author or authors; Apache License 2.0. From openrewrite/rewrite@398a634, rewrite-docker/.
// Modified by Dockerfile Inventory, 2026: a backslash before a newline never joins a word (issue 7).
// Modified by Dockerfile Inventory, 2026: parser directives only before the first instruction (issue 2).
// Modified by Dockerfile Inventory, 2026: a comment line inside a string spanning lines is part of it (issue 1).
// Modified by Dockerfile Inventory, 2026: a heredoc's word may be quoted, as in COPY <<'EOF' (issue 14).
// Modified by Dockerfile Inventory, 2026: a $(...) inside double quotes keeps its own quoting (issue 16).
// Modified by Dockerfile Inventory, 2026: an exec form that is not a JSON array is shell form, as Docker reads it (issue 17).

lexer grammar DockerLexer;

@lexer::header
{import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.misc.Interval;}

@lexer::members
{
    // Use a queue (FIFO) for heredoc markers so they are matched in order of declaration
    private Queue<String> heredocIdentifiers = new LinkedList<String>();
    private boolean heredocIdentifierCaptured = false;
    // Track if we're at the start of a logical line (where instructions can appear)
    private boolean atLineStart = true;
    // Track if we're after FROM to recognize AS as a keyword (for stage aliasing)
    private boolean afterFrom = false;
    // Track if we're after HEALTHCHECK to recognize CMD/NONE as keywords
    private boolean afterHealthcheck = false;
    // Parser directives are read only before the first instruction; after it, # key=value is a comment
    private boolean directivesAllowed = true;
    // What may come before an exec form's '[' on its line
    private static final Pattern EXEC_FORM_START = Pattern.compile(
        "[ \\t]*(ONBUILD[ \\t]+)?(RUN([ \\t]+--\\S+)*|CMD|ENTRYPOINT|HEALTHCHECK([ \\t]+--\\S+)*[ \\t]+CMD)[ \\t]+",
        Pattern.CASE_INSENSITIVE);

    // Docker reads RUN, CMD and ENTRYPOINT as exec form only when the rest of the line is a JSON array of strings;
    // otherwise the whole line is shell form.
    private boolean startsInvalidExecForm() {
        int bracket = _tokenStartCharIndex;
        int lineStart = bracket;
        while (lineStart > 0 && _input.getText(Interval.of(lineStart - 1, lineStart - 1)).charAt(0) != '\n') {
            lineStart--;
        }
        return EXEC_FORM_START.matcher(_input.getText(Interval.of(lineStart, bracket - 1))).matches()
            && !jsonArrayAhead(bracket);
    }

    private boolean jsonArrayAhead(int bracket) {
        int i = skipBlanks(bracket + 1);
        if (charAt(i) == ']') {
            return lineEndsAfter(i + 1);
        }
        while (true) {
            if (charAt(i) != '"') {
                return false;
            }
            i++;
            while (charAt(i) != '"') {
                if (charAt(i) == '\\') {
                    i++;
                }
                if (charAt(i) == '\n' || charAt(i) == '\r' || charAt(i) == CharStream.EOF) {
                    return false;
                }
                i++;
            }
            i = skipBlanks(i + 1);
            if (charAt(i) == ']') {
                return lineEndsAfter(i + 1);
            }
            if (charAt(i) != ',') {
                return false;
            }
            i = skipBlanks(i + 1);
        }
    }

    private boolean lineEndsAfter(int i) {
        int end = skipBlanks(i);
        return charAt(end) == '\n' || charAt(end) == '\r' || charAt(end) == CharStream.EOF;
    }

    // Blanks, and a line continuation with any comment lines after it, as Docker joins them before reading JSON
    private int skipBlanks(int i) {
        while (true) {
            if (charAt(i) == ' ' || charAt(i) == '\t') {
                i++;
            } else if (charAt(i) == '\\' && continuationEnd(i + 1) > 0) {
                i = continuationEnd(i + 1);
            } else {
                return i;
            }
        }
    }

    // After a backslash: the index past its newline and any comment lines, or -1 when it does not end the line
    private int continuationEnd(int i) {
        while (charAt(i) == ' ' || charAt(i) == '\t') {
            i++;
        }
        if (charAt(i) != '\n' && charAt(i) != '\r') {
            return -1;
        }
        while (charAt(i) == '\n' || charAt(i) == '\r') {
            i++;
        }
        int line = i;
        while (charAt(line) == ' ' || charAt(line) == '\t') {
            line++;
        }
        if (charAt(line) == '#') {
            while (charAt(line) != '\n' && charAt(line) != CharStream.EOF) {
                line++;
            }
            return continuationEnd(line);
        }
        return i;
    }

    private int charAt(int i) {
        return i >= _input.size() ? CharStream.EOF : _input.getText(Interval.of(i, i)).charAt(0);
    }

    @Override
    public Token nextToken() {
        Token token = super.nextToken();
        if (token.getChannel() == Token.DEFAULT_CHANNEL && token.getType() != PARSER_DIRECTIVE) {
            directivesAllowed = false;
        }
        return token;
    }
}

options {
    caseInsensitive = true;
}

// Parser directives (must be at the beginning of file)
// After a parser directive, we're at line start (it consumes the newline)
PARSER_DIRECTIVE : '#' WS_CHAR* [A-Z_]+ WS_CHAR* '=' WS_CHAR* ~[\r\n]* NEWLINE_CHAR {directivesAllowed}? { atLineStart = true; };

// Comments (after parser directives) - HIDDEN in main mode
COMMENT : '#' ~[\r\n]* -> channel(HIDDEN);

// Instructions (case-insensitive)
// Instructions are only recognized at line start. Otherwise they become UNQUOTED_TEXT.
// This eliminates ambiguity between instruction keywords and shell command text.
FROM       : 'FROM'       { if (!atLineStart) setType(UNQUOTED_TEXT); else afterFrom = true; atLineStart = false; };
RUN        : 'RUN'        { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };
// CMD is a keyword at line start (CMD instruction) or after HEALTHCHECK
CMD        : 'CMD'        { if (!atLineStart && !afterHealthcheck) setType(UNQUOTED_TEXT); atLineStart = false; afterHealthcheck = false; };
// NONE is only a keyword after HEALTHCHECK
NONE       : 'NONE'       { if (!afterHealthcheck) setType(UNQUOTED_TEXT); atLineStart = false; afterHealthcheck = false; };
LABEL      : 'LABEL'      { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };
EXPOSE     : 'EXPOSE'     { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };
ENV        : 'ENV'        { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };
ADD        : 'ADD'        { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };
COPY       : 'COPY'       { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };
ENTRYPOINT : 'ENTRYPOINT' { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };
VOLUME     : 'VOLUME'     { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };
USER       : 'USER'       { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };
WORKDIR    : 'WORKDIR'    { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };
ARG        : 'ARG'        { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };
// ONBUILD is special: it keeps atLineStart true so the following instruction is recognized
ONBUILD    : 'ONBUILD'    { if (!atLineStart) setType(UNQUOTED_TEXT); /* atLineStart stays true */ };
STOPSIGNAL : 'STOPSIGNAL' { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };
// HEALTHCHECK is special: it keeps atLineStart true and sets afterHealthcheck so CMD/NONE are recognized after flags
HEALTHCHECK: 'HEALTHCHECK'{ if (!atLineStart) setType(UNQUOTED_TEXT); else afterHealthcheck = true; /* atLineStart stays true */ };
SHELL      : 'SHELL'      { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };
MAINTAINER : 'MAINTAINER' { if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; };

// AS is only a keyword after FROM (for stage aliasing)
AS         : 'AS' { if (!afterFrom) setType(UNQUOTED_TEXT); atLineStart = false; afterFrom = false; };

// Heredoc start - captures <<EOF or <<-EOF including the identifier and switches to HEREDOC_PREAMBLE mode
HEREDOC_START : '<<' '-'? HEREDOC_MARKER {
    // Extract and store the heredoc marker identifier in FIFO order
    String text = getText();
    int prefixLen = text.charAt(2) == '-' ? 3 : 2;
    String marker = text.substring(prefixLen).replaceAll("^(['\"])(.*)\\1$", "$2");
    heredocIdentifiers.add(marker);
    heredocIdentifierCaptured = true;
    atLineStart = false;
} -> pushMode(HEREDOC_PREAMBLE);

// A heredoc's word, bare or quoted ('EOF' or "EOF": the body is not expanded); the closing line is the bare word (issue 14)
fragment HEREDOC_MARKER : [A-Z_][A-Z0-9_]* | '\'' [A-Z_][A-Z0-9_]* '\'' | '"' [A-Z_][A-Z0-9_]* '"';

// Line continuation - HIDDEN in main mode
// Supports both backslash (Linux) and backtick (Windows with # escape=`)
LINE_CONTINUATION : ('\\' | '`') [ \t]* '\r'? '\n' -> channel(HIDDEN);

// An exec form that is not a JSON array: the rest of the line is shell-form text (issue 17)
INVALID_EXEC_FORM : {startsInvalidExecForm()}? '[' ( INLINE_CONTINUATION | ~[\r\n] )* { atLineStart = false; } -> type(UNQUOTED_TEXT);

// JSON array delimiters (for exec form) - no mode switching, handled in parser
LBRACKET : '[' { atLineStart = false; };
RBRACKET : ']' { atLineStart = false; };
COMMA    : ',' { atLineStart = false; };

// Assignment (used in ENV, ARG, LABEL, etc.)
EQUALS     : '=' { if (!afterHealthcheck) atLineStart = false; };

// Flag with optional value: --name or --name=value
// Captures the entire flag as a single token, stopping at whitespace
// This avoids the greedy flagValue+ parsing issue while keeping shell commands working
// Flag values can contain quoted strings (which may include spaces)
FLAG : '--' [a-z] [a-z0-9_-]* ('=' FLAG_VALUE_PART+)? { if (!afterHealthcheck) atLineStart = false; };
fragment FLAG_VALUE_PART
    : '"' ( '\\' ~[\r\n] | ~["\\\r\n] )* '"'   // Double-quoted string (with escapes)
    | '\'' ~['\r\n]* '\''                        // Single-quoted string (literal)
    | ~[ \t\r\n"'\\]                             // Unquoted character
    | '\\' ~[\r\n]                               // Escaped character
    ;

// Standalone -- (double dash without flag name) - used in shell commands
DASH_DASH  : '--' { if (!afterHealthcheck) atLineStart = false; };

// Unquoted text fragment (to be used in UNQUOTED_TEXT)
// This matches text that doesn't start with -- or <<
// Note: < is excluded to allow HEREDOC_START (<<) to match
fragment UNQUOTED_CHAR : ~[ \t\r\n\\"'$[\]=<];
// Not a newline: a backslash before one is a line continuation, which Docker removes, never part of a word (issue 7)
fragment ESCAPED_CHAR : '\\' ~[\r\n];

// String literals
// Double-quoted strings support escape sequences, line continuation, and bare newlines
// Backtick followed by whitespace+newline is continuation; standalone backtick is regular char
// Bare newlines are allowed (e.g., comment lines inside PowerShell strings don't need trailing backtick)
DOUBLE_QUOTED_STRING : '"' ( DQ_COMMAND_SUBST | ESCAPE_SEQUENCE | INLINE_CONTINUATION | '`' | [\r\n]+ STRING_COMMENT_LINE* | ~["\\\r\n`] )* '"' { if (!afterHealthcheck) atLineStart = false; };
// Single-quoted strings in shell are literal - no escape processing inside
// But they DO support line continuation (backslash or backtick followed by newline)
// Bare newlines are also allowed for multi-line strings
SINGLE_QUOTED_STRING : '\'' ( INLINE_CONTINUATION | [\r\n]+ STRING_COMMENT_LINE* | ~['\r\n] )* '\'' { if (!afterHealthcheck) atLineStart = false; };

// Inline line continuation (inside strings) - backtick or backslash followed by newline
fragment INLINE_CONTINUATION : ('\\' | '`') [ \t]* [\r\n]+ STRING_COMMENT_LINE*;

// A comment line inside a string that spans lines: Docker drops it before the shell reads the string, so a quote in it
// neither ends nor starts one (issue 1)
fragment STRING_COMMENT_LINE : [ \t]* '#' ~[\r\n]* [\r\n]+;

// A $(...) inside double quotes: its own quoted parts may hold a double quote (issue 16)
fragment DQ_COMMAND_SUBST
    : '$(' ( DQ_COMMAND_SUBST | '\'' ~['\r\n]* '\'' | '"' ( DQ_COMMAND_SUBST | ESCAPE_SEQUENCE | ~["\\\r\n] )* '"'
           | ESCAPE_SEQUENCE | INLINE_CONTINUATION | '(' ~[()'"\r\n]* ')' | ~[()'"\\\r\n] )* ')'
    ;

fragment ESCAPE_SEQUENCE
    : '\\' ~[\r\n]   // Backslash followed by any char except newline (includes \n, \t, \\, \", Windows paths like \P)
    ;

fragment HEX_DIGIT : [0-9A-F];

// Environment variable reference
ENV_VAR : ('$' '{' [A-Z_][A-Z0-9_]* ( ':-' | ':+' | ':' )? ~[}]* '}' | '$' [A-Z_][A-Z0-9_]*) { atLineStart = false; };

// Special shell variables ($!, $$, $?, $#, $@, $*, $0-$9)
SPECIAL_VAR : '$' [!$?#@*0-9] { atLineStart = false; };

// Command substitution $(command) or $((arithmetic))
// Handles nested parentheses by counting them
COMMAND_SUBST : '$(' ( COMMAND_SUBST | ~[()] | '(' COMMAND_SUBST_INNER* ')' )* ')' { atLineStart = false; };
fragment COMMAND_SUBST_INNER : COMMAND_SUBST | ~[()];

// Backtick command substitution `command`
// First char after backtick must NOT be whitespace/newline (which would be line continuation)
// Content cannot span newlines (backtick command substitution doesn't support that)
BACKTICK_SUBST : '`' ~[ \t\r\n`] ~[`\r\n]* '`' { atLineStart = false; };

// Lone dollar sign that doesn't match ENV_VAR, SPECIAL_VAR, or COMMAND_SUBST
// This handles cases like $'hello' (bash ANSI-C quoting) where $ precedes a quote
DOLLAR : '$' { atLineStart = false; };

// Unquoted text (arguments, file paths, etc.)
// This should be after more specific tokens
// Note: comma is NOT excluded here - it's only special in JSON arrays
// We structure this to not match text starting with -- (so DASH_DASH can match first)
// Also exclude < from starting char to allow HEREDOC_START (<<) to match
UNQUOTED_TEXT
    : ( ~[-< \t\r\n\\"'$[\]=] ( UNQUOTED_CHAR | ESCAPED_CHAR )*   // Start with non-hyphen, non-<, non-space
    | '-' ~[- \t\r\n\\"'$[\]=<] ( UNQUOTED_CHAR | ESCAPED_CHAR )*  // Single hyphen followed by non-hyphen, non-space
    | '-'  // Just a hyphen by itself
    | '<' ~[< \t\r\n\\"'$[\]=] ( UNQUOTED_CHAR | ESCAPED_CHAR )*  // Single < followed by non-<
    | '<'  // Just a < by itself
    | ESCAPED_CHAR ( UNQUOTED_CHAR | ESCAPED_CHAR )*  // Start with escaped char (e.g., \; in find -exec)
    ) { if (!afterHealthcheck) atLineStart = false; }
    ;

// Whitespace - HIDDEN in main mode
WS : WS_CHAR+ -> channel(HIDDEN);

fragment WS_CHAR : [ \t];

// Newlines - HIDDEN in main mode, reset state for next line
NEWLINE : NEWLINE_CHAR+ { atLineStart = true; afterFrom = false; afterHealthcheck = false; } -> channel(HIDDEN);

fragment NEWLINE_CHAR : [\r\n];

// ----------------------------------------------------------------------------------------------
// HEREDOC_PREAMBLE mode - for parsing shell command preamble after heredoc marker(s)
// The heredoc identifier (e.g., EOF) is already captured in HEREDOC_START
// This mode handles the shell command text including additional heredoc markers for multi-heredoc.
// ----------------------------------------------------------------------------------------------
mode HEREDOC_PREAMBLE;

// Line continuation in preamble - stay in HEREDOC_PREAMBLE mode
HP_LINE_CONTINUATION : ('\\' | '`') [ \t]* '\r'? '\n' -> channel(HIDDEN);

// Newline without continuation - transition to HEREDOC mode for body content
HP_NEWLINE : '\n' -> type(NEWLINE), mode(HEREDOC);

HP_WS      : [ \t\r\u000C]+ -> channel(HIDDEN);
HP_COMMENT : '/*' .*? '*/'  -> channel(HIDDEN);
HP_LINE_COMMENT : ('//' | '#') ~[\r\n]* '\r'? -> channel(HIDDEN);

// Additional heredoc marker in preamble (for multi-heredoc support)
HP_HEREDOC_START : '<<' '-'? HEREDOC_MARKER {
    // Extract and store the heredoc marker identifier in FIFO order
    String text = getText();
    int prefixLen = text.charAt(2) == '-' ? 3 : 2;
    String marker = text.substring(prefixLen).replaceAll("^(['\"])(.*)\\1$", "$2");
    heredocIdentifiers.add(marker);
} -> type(HEREDOC_START);

// Any text on the heredoc line after the marker (destination paths, interpreter names, shell commands, etc.)
// Exclude < to allow HP_HEREDOC_START to match <<
// Exclude \ and ` to allow HP_LINE_CONTINUATION to match
HP_UNQUOTED_TEXT : ( ~[<\\` \t\r\n]+
                   | '<' ~[< \t\r\n] ~[ \t\r\n]*  // single < followed by non-< char
                   | '<'  // standalone <
                   ) -> type(UNQUOTED_TEXT);

// ----------------------------------------------------------------------------------------------
// HEREDOC mode - for parsing heredoc content
// Supports multiple heredocs by only popping mode when all markers have been matched.
// ----------------------------------------------------------------------------------------------
mode HEREDOC;

H_NEWLINE : '\n' -> type(NEWLINE);

// Match heredoc content lines - emit as HEREDOC_CONTENT unless it's an ending identifier
// For multi-heredoc, we only popMode when the queue is empty (all markers matched in FIFO order)
HEREDOC_CONTENT : ~[\n]+
{
  if(!heredocIdentifiers.isEmpty() && getText().equals(heredocIdentifiers.peek())) {
      setType(UNQUOTED_TEXT);
      heredocIdentifiers.poll();  // Remove from front of queue (FIFO)
      // Only pop mode when all heredoc markers have been matched
      if(heredocIdentifiers.isEmpty()) {
          popMode();
          atLineStart = true;  // After heredoc ends, next line is at line start
      }
  }
};

