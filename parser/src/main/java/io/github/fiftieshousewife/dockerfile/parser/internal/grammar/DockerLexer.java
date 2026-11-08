// Generated from src/main/antlr/DockerLexer.g4 by ANTLR 4.13.2
package io.github.fiftieshousewife.dockerfile.parser.internal.grammar;
import java.util.LinkedList;
import java.util.Queue;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class DockerLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.13.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		PARSER_DIRECTIVE=1, COMMENT=2, FROM=3, RUN=4, CMD=5, NONE=6, LABEL=7, 
		EXPOSE=8, ENV=9, ADD=10, COPY=11, ENTRYPOINT=12, VOLUME=13, USER=14, WORKDIR=15, 
		ARG=16, ONBUILD=17, STOPSIGNAL=18, HEALTHCHECK=19, SHELL=20, MAINTAINER=21, 
		AS=22, HEREDOC_START=23, LINE_CONTINUATION=24, LBRACKET=25, RBRACKET=26, 
		COMMA=27, EQUALS=28, FLAG=29, DASH_DASH=30, DOUBLE_QUOTED_STRING=31, SINGLE_QUOTED_STRING=32, 
		ENV_VAR=33, SPECIAL_VAR=34, COMMAND_SUBST=35, BACKTICK_SUBST=36, DOLLAR=37, 
		UNQUOTED_TEXT=38, WS=39, NEWLINE=40, HP_LINE_CONTINUATION=41, HP_WS=42, 
		HP_COMMENT=43, HP_LINE_COMMENT=44, HEREDOC_CONTENT=45, H_NEWLINE=46;
	public static final int
		HEREDOC_PREAMBLE=1, HEREDOC=2;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE", "HEREDOC_PREAMBLE", "HEREDOC"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"PARSER_DIRECTIVE", "COMMENT", "FROM", "RUN", "CMD", "NONE", "LABEL", 
			"EXPOSE", "ENV", "ADD", "COPY", "ENTRYPOINT", "VOLUME", "USER", "WORKDIR", 
			"ARG", "ONBUILD", "STOPSIGNAL", "HEALTHCHECK", "SHELL", "MAINTAINER", 
			"AS", "HEREDOC_START", "HEREDOC_MARKER", "LINE_CONTINUATION", "INVALID_EXEC_FORM", 
			"LBRACKET", "RBRACKET", "COMMA", "EQUALS", "FLAG", "FLAG_VALUE_PART", 
			"DASH_DASH", "UNQUOTED_CHAR", "ESCAPED_CHAR", "DOUBLE_QUOTED_STRING", 
			"SINGLE_QUOTED_STRING", "INLINE_CONTINUATION", "STRING_COMMENT_LINE", 
			"DQ_COMMAND_SUBST", "ESCAPE_SEQUENCE", "HEX_DIGIT", "ENV_VAR", "SPECIAL_VAR", 
			"COMMAND_SUBST", "COMMAND_SUBST_INNER", "BACKTICK_SUBST", "DOLLAR", "UNQUOTED_TEXT", 
			"WS", "WS_CHAR", "NEWLINE", "NEWLINE_CHAR", "HP_LINE_CONTINUATION", "HP_NEWLINE", 
			"HP_WS", "HP_COMMENT", "HP_LINE_COMMENT", "HP_HEREDOC_START", "HP_UNQUOTED_TEXT", 
			"H_NEWLINE", "HEREDOC_CONTENT"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, "'FROM'", "'RUN'", "'CMD'", "'NONE'", "'LABEL'", "'EXPOSE'", 
			"'ENV'", "'ADD'", "'COPY'", "'ENTRYPOINT'", "'VOLUME'", "'USER'", "'WORKDIR'", 
			"'ARG'", "'ONBUILD'", "'STOPSIGNAL'", "'HEALTHCHECK'", "'SHELL'", "'MAINTAINER'", 
			"'AS'", null, null, "'['", "']'", "','", "'='", null, "'--'", null, null, 
			null, null, null, null, "'$'", null, null, null, null, null, null, null, 
			null, "'\\n'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "PARSER_DIRECTIVE", "COMMENT", "FROM", "RUN", "CMD", "NONE", "LABEL", 
			"EXPOSE", "ENV", "ADD", "COPY", "ENTRYPOINT", "VOLUME", "USER", "WORKDIR", 
			"ARG", "ONBUILD", "STOPSIGNAL", "HEALTHCHECK", "SHELL", "MAINTAINER", 
			"AS", "HEREDOC_START", "LINE_CONTINUATION", "LBRACKET", "RBRACKET", "COMMA", 
			"EQUALS", "FLAG", "DASH_DASH", "DOUBLE_QUOTED_STRING", "SINGLE_QUOTED_STRING", 
			"ENV_VAR", "SPECIAL_VAR", "COMMAND_SUBST", "BACKTICK_SUBST", "DOLLAR", 
			"UNQUOTED_TEXT", "WS", "NEWLINE", "HP_LINE_CONTINUATION", "HP_WS", "HP_COMMENT", 
			"HP_LINE_COMMENT", "HEREDOC_CONTENT", "H_NEWLINE"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}


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


	public DockerLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "DockerLexer.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	@Override
	public void action(RuleContext _localctx, int ruleIndex, int actionIndex) {
		switch (ruleIndex) {
		case 0:
			PARSER_DIRECTIVE_action((RuleContext)_localctx, actionIndex);
			break;
		case 2:
			FROM_action((RuleContext)_localctx, actionIndex);
			break;
		case 3:
			RUN_action((RuleContext)_localctx, actionIndex);
			break;
		case 4:
			CMD_action((RuleContext)_localctx, actionIndex);
			break;
		case 5:
			NONE_action((RuleContext)_localctx, actionIndex);
			break;
		case 6:
			LABEL_action((RuleContext)_localctx, actionIndex);
			break;
		case 7:
			EXPOSE_action((RuleContext)_localctx, actionIndex);
			break;
		case 8:
			ENV_action((RuleContext)_localctx, actionIndex);
			break;
		case 9:
			ADD_action((RuleContext)_localctx, actionIndex);
			break;
		case 10:
			COPY_action((RuleContext)_localctx, actionIndex);
			break;
		case 11:
			ENTRYPOINT_action((RuleContext)_localctx, actionIndex);
			break;
		case 12:
			VOLUME_action((RuleContext)_localctx, actionIndex);
			break;
		case 13:
			USER_action((RuleContext)_localctx, actionIndex);
			break;
		case 14:
			WORKDIR_action((RuleContext)_localctx, actionIndex);
			break;
		case 15:
			ARG_action((RuleContext)_localctx, actionIndex);
			break;
		case 16:
			ONBUILD_action((RuleContext)_localctx, actionIndex);
			break;
		case 17:
			STOPSIGNAL_action((RuleContext)_localctx, actionIndex);
			break;
		case 18:
			HEALTHCHECK_action((RuleContext)_localctx, actionIndex);
			break;
		case 19:
			SHELL_action((RuleContext)_localctx, actionIndex);
			break;
		case 20:
			MAINTAINER_action((RuleContext)_localctx, actionIndex);
			break;
		case 21:
			AS_action((RuleContext)_localctx, actionIndex);
			break;
		case 22:
			HEREDOC_START_action((RuleContext)_localctx, actionIndex);
			break;
		case 25:
			INVALID_EXEC_FORM_action((RuleContext)_localctx, actionIndex);
			break;
		case 26:
			LBRACKET_action((RuleContext)_localctx, actionIndex);
			break;
		case 27:
			RBRACKET_action((RuleContext)_localctx, actionIndex);
			break;
		case 28:
			COMMA_action((RuleContext)_localctx, actionIndex);
			break;
		case 29:
			EQUALS_action((RuleContext)_localctx, actionIndex);
			break;
		case 30:
			FLAG_action((RuleContext)_localctx, actionIndex);
			break;
		case 32:
			DASH_DASH_action((RuleContext)_localctx, actionIndex);
			break;
		case 35:
			DOUBLE_QUOTED_STRING_action((RuleContext)_localctx, actionIndex);
			break;
		case 36:
			SINGLE_QUOTED_STRING_action((RuleContext)_localctx, actionIndex);
			break;
		case 42:
			ENV_VAR_action((RuleContext)_localctx, actionIndex);
			break;
		case 43:
			SPECIAL_VAR_action((RuleContext)_localctx, actionIndex);
			break;
		case 44:
			COMMAND_SUBST_action((RuleContext)_localctx, actionIndex);
			break;
		case 46:
			BACKTICK_SUBST_action((RuleContext)_localctx, actionIndex);
			break;
		case 47:
			DOLLAR_action((RuleContext)_localctx, actionIndex);
			break;
		case 48:
			UNQUOTED_TEXT_action((RuleContext)_localctx, actionIndex);
			break;
		case 51:
			NEWLINE_action((RuleContext)_localctx, actionIndex);
			break;
		case 58:
			HP_HEREDOC_START_action((RuleContext)_localctx, actionIndex);
			break;
		case 61:
			HEREDOC_CONTENT_action((RuleContext)_localctx, actionIndex);
			break;
		}
	}
	private void PARSER_DIRECTIVE_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 0:
			 atLineStart = true; 
			break;
		}
	}
	private void FROM_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 1:
			 if (!atLineStart) setType(UNQUOTED_TEXT); else afterFrom = true; atLineStart = false; 
			break;
		}
	}
	private void RUN_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 2:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void CMD_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 3:
			 if (!atLineStart && !afterHealthcheck) setType(UNQUOTED_TEXT); atLineStart = false; afterHealthcheck = false; 
			break;
		}
	}
	private void NONE_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 4:
			 if (!afterHealthcheck) setType(UNQUOTED_TEXT); atLineStart = false; afterHealthcheck = false; 
			break;
		}
	}
	private void LABEL_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 5:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void EXPOSE_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 6:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void ENV_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 7:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void ADD_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 8:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void COPY_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 9:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void ENTRYPOINT_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 10:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void VOLUME_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 11:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void USER_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 12:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void WORKDIR_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 13:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void ARG_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 14:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void ONBUILD_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 15:
			 if (!atLineStart) setType(UNQUOTED_TEXT); /* atLineStart stays true */ 
			break;
		}
	}
	private void STOPSIGNAL_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 16:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void HEALTHCHECK_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 17:
			 if (!atLineStart) setType(UNQUOTED_TEXT); else afterHealthcheck = true; /* atLineStart stays true */ 
			break;
		}
	}
	private void SHELL_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 18:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void MAINTAINER_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 19:
			 if (!atLineStart) setType(UNQUOTED_TEXT); atLineStart = false; 
			break;
		}
	}
	private void AS_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 20:
			 if (!afterFrom) setType(UNQUOTED_TEXT); atLineStart = false; afterFrom = false; 
			break;
		}
	}
	private void HEREDOC_START_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 21:

			    // Extract and store the heredoc marker identifier in FIFO order
			    String text = getText();
			    int prefixLen = text.charAt(2) == '-' ? 3 : 2;
			    String marker = text.substring(prefixLen).replaceAll("^(['\"])(.*)\\1$", "$2");
			    heredocIdentifiers.add(marker);
			    heredocIdentifierCaptured = true;
			    atLineStart = false;

			break;
		}
	}
	private void INVALID_EXEC_FORM_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 22:
			 atLineStart = false; 
			break;
		}
	}
	private void LBRACKET_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 23:
			 atLineStart = false; 
			break;
		}
	}
	private void RBRACKET_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 24:
			 atLineStart = false; 
			break;
		}
	}
	private void COMMA_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 25:
			 atLineStart = false; 
			break;
		}
	}
	private void EQUALS_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 26:
			 if (!afterHealthcheck) atLineStart = false; 
			break;
		}
	}
	private void FLAG_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 27:
			 if (!afterHealthcheck) atLineStart = false; 
			break;
		}
	}
	private void DASH_DASH_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 28:
			 if (!afterHealthcheck) atLineStart = false; 
			break;
		}
	}
	private void DOUBLE_QUOTED_STRING_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 29:
			 if (!afterHealthcheck) atLineStart = false; 
			break;
		}
	}
	private void SINGLE_QUOTED_STRING_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 30:
			 if (!afterHealthcheck) atLineStart = false; 
			break;
		}
	}
	private void ENV_VAR_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 31:
			 atLineStart = false; 
			break;
		}
	}
	private void SPECIAL_VAR_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 32:
			 atLineStart = false; 
			break;
		}
	}
	private void COMMAND_SUBST_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 33:
			 atLineStart = false; 
			break;
		}
	}
	private void BACKTICK_SUBST_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 34:
			 atLineStart = false; 
			break;
		}
	}
	private void DOLLAR_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 35:
			 atLineStart = false; 
			break;
		}
	}
	private void UNQUOTED_TEXT_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 36:
			 if (!afterHealthcheck) atLineStart = false; 
			break;
		}
	}
	private void NEWLINE_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 37:
			 atLineStart = true; afterFrom = false; afterHealthcheck = false; 
			break;
		}
	}
	private void HP_HEREDOC_START_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 38:

			    // Extract and store the heredoc marker identifier in FIFO order
			    String text = getText();
			    int prefixLen = text.charAt(2) == '-' ? 3 : 2;
			    String marker = text.substring(prefixLen).replaceAll("^(['\"])(.*)\\1$", "$2");
			    heredocIdentifiers.add(marker);

			break;
		}
	}
	private void HEREDOC_CONTENT_action(RuleContext _localctx, int actionIndex) {
		switch (actionIndex) {
		case 39:

			  if(!heredocIdentifiers.isEmpty() && getText().equals(heredocIdentifiers.peek())) {
			      setType(UNQUOTED_TEXT);
			      heredocIdentifiers.poll();  // Remove from front of queue (FIFO)
			      // Only pop mode when all heredoc markers have been matched
			      if(heredocIdentifiers.isEmpty()) {
			          popMode();
			          atLineStart = true;  // After heredoc ends, next line is at line start
			      }
			  }

			break;
		}
	}
	@Override
	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 0:
			return PARSER_DIRECTIVE_sempred((RuleContext)_localctx, predIndex);
		case 25:
			return INVALID_EXEC_FORM_sempred((RuleContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean PARSER_DIRECTIVE_sempred(RuleContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return directivesAllowed;
		}
		return true;
	}
	private boolean INVALID_EXEC_FORM_sempred(RuleContext _localctx, int predIndex) {
		switch (predIndex) {
		case 1:
			return startsInvalidExecForm();
		}
		return true;
	}

	public static final String _serializedATN =
		"\u0004\u0000.\u0341\u0006\uffff\uffff\u0006\uffff\uffff\u0006\uffff\uffff"+
		"\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0002\u0002\u0007\u0002"+
		"\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004\u0002\u0005\u0007\u0005"+
		"\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007\u0002\b\u0007\b\u0002"+
		"\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b\u0002\f\u0007\f\u0002"+
		"\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007\u000f\u0002\u0010"+
		"\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007\u0012\u0002\u0013"+
		"\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007\u0015\u0002\u0016"+
		"\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007\u0018\u0002\u0019"+
		"\u0007\u0019\u0002\u001a\u0007\u001a\u0002\u001b\u0007\u001b\u0002\u001c"+
		"\u0007\u001c\u0002\u001d\u0007\u001d\u0002\u001e\u0007\u001e\u0002\u001f"+
		"\u0007\u001f\u0002 \u0007 \u0002!\u0007!\u0002\"\u0007\"\u0002#\u0007"+
		"#\u0002$\u0007$\u0002%\u0007%\u0002&\u0007&\u0002\'\u0007\'\u0002(\u0007"+
		"(\u0002)\u0007)\u0002*\u0007*\u0002+\u0007+\u0002,\u0007,\u0002-\u0007"+
		"-\u0002.\u0007.\u0002/\u0007/\u00020\u00070\u00021\u00071\u00022\u0007"+
		"2\u00023\u00073\u00024\u00074\u00025\u00075\u00026\u00076\u00027\u0007"+
		"7\u00028\u00078\u00029\u00079\u0002:\u0007:\u0002;\u0007;\u0002<\u0007"+
		"<\u0002=\u0007=\u0001\u0000\u0001\u0000\u0005\u0000\u0082\b\u0000\n\u0000"+
		"\f\u0000\u0085\t\u0000\u0001\u0000\u0004\u0000\u0088\b\u0000\u000b\u0000"+
		"\f\u0000\u0089\u0001\u0000\u0005\u0000\u008d\b\u0000\n\u0000\f\u0000\u0090"+
		"\t\u0000\u0001\u0000\u0001\u0000\u0005\u0000\u0094\b\u0000\n\u0000\f\u0000"+
		"\u0097\t\u0000\u0001\u0000\u0005\u0000\u009a\b\u0000\n\u0000\f\u0000\u009d"+
		"\t\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0001"+
		"\u0001\u0005\u0001\u00a5\b\u0001\n\u0001\f\u0001\u00a8\t\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002"+
		"\u0001\u0002\u0001\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003"+
		"\u0001\u0003\u0001\u0003\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004"+
		"\u0001\u0004\u0001\u0004\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0005"+
		"\u0001\u0005\u0001\u0005\u0001\u0005\u0001\u0006\u0001\u0006\u0001\u0006"+
		"\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0006\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007"+
		"\u0001\u0007\u0001\u0007\u0001\b\u0001\b\u0001\b\u0001\b\u0001\b\u0001"+
		"\b\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\t\u0001\n\u0001\n\u0001"+
		"\n\u0001\n\u0001\n\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b"+
		"\u0001\u000b\u0001\u000b\u0001\u000b\u0001\u000b\u0001\f\u0001\f\u0001"+
		"\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\f\u0001\r\u0001\r\u0001"+
		"\r\u0001\r\u0001\r\u0001\r\u0001\r\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e\u0001\u000e"+
		"\u0001\u000e\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0001\u000f\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010"+
		"\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0010\u0001\u0011"+
		"\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011"+
		"\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011\u0001\u0011"+
		"\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012"+
		"\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012\u0001\u0012"+
		"\u0001\u0012\u0001\u0012\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013"+
		"\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0013\u0001\u0014\u0001\u0014"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014"+
		"\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0014\u0001\u0015"+
		"\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0015\u0001\u0016\u0001\u0016"+
		"\u0001\u0016\u0001\u0016\u0003\u0016\u015a\b\u0016\u0001\u0016\u0001\u0016"+
		"\u0001\u0016\u0001\u0016\u0001\u0016\u0001\u0017\u0001\u0017\u0005\u0017"+
		"\u0163\b\u0017\n\u0017\f\u0017\u0166\t\u0017\u0001\u0017\u0001\u0017\u0001"+
		"\u0017\u0005\u0017\u016b\b\u0017\n\u0017\f\u0017\u016e\t\u0017\u0001\u0017"+
		"\u0001\u0017\u0001\u0017\u0001\u0017\u0005\u0017\u0174\b\u0017\n\u0017"+
		"\f\u0017\u0177\t\u0017\u0001\u0017\u0003\u0017\u017a\b\u0017\u0001\u0018"+
		"\u0001\u0018\u0005\u0018\u017e\b\u0018\n\u0018\f\u0018\u0181\t\u0018\u0001"+
		"\u0018\u0003\u0018\u0184\b\u0018\u0001\u0018\u0001\u0018\u0001\u0018\u0001"+
		"\u0018\u0001\u0019\u0001\u0019\u0001\u0019\u0001\u0019\u0005\u0019\u018e"+
		"\b\u0019\n\u0019\f\u0019\u0191\t\u0019\u0001\u0019\u0001\u0019\u0001\u0019"+
		"\u0001\u0019\u0001\u001a\u0001\u001a\u0001\u001a\u0001\u001b\u0001\u001b"+
		"\u0001\u001b\u0001\u001c\u0001\u001c\u0001\u001c\u0001\u001d\u0001\u001d"+
		"\u0001\u001d\u0001\u001e\u0001\u001e\u0001\u001e\u0001\u001e\u0001\u001e"+
		"\u0005\u001e\u01a8\b\u001e\n\u001e\f\u001e\u01ab\t\u001e\u0001\u001e\u0001"+
		"\u001e\u0004\u001e\u01af\b\u001e\u000b\u001e\f\u001e\u01b0\u0003\u001e"+
		"\u01b3\b\u001e\u0001\u001e\u0001\u001e\u0001\u001f\u0001\u001f\u0001\u001f"+
		"\u0001\u001f\u0005\u001f\u01bb\b\u001f\n\u001f\f\u001f\u01be\t\u001f\u0001"+
		"\u001f\u0001\u001f\u0001\u001f\u0005\u001f\u01c3\b\u001f\n\u001f\f\u001f"+
		"\u01c6\t\u001f\u0001\u001f\u0001\u001f\u0001\u001f\u0001\u001f\u0003\u001f"+
		"\u01cc\b\u001f\u0001 \u0001 \u0001 \u0001 \u0001 \u0001!\u0001!\u0001"+
		"\"\u0001\"\u0001\"\u0001#\u0001#\u0001#\u0001#\u0001#\u0001#\u0004#\u01de"+
		"\b#\u000b#\f#\u01df\u0001#\u0005#\u01e3\b#\n#\f#\u01e6\t#\u0001#\u0005"+
		"#\u01e9\b#\n#\f#\u01ec\t#\u0001#\u0001#\u0001#\u0001$\u0001$\u0001$\u0004"+
		"$\u01f4\b$\u000b$\f$\u01f5\u0001$\u0005$\u01f9\b$\n$\f$\u01fc\t$\u0001"+
		"$\u0005$\u01ff\b$\n$\f$\u0202\t$\u0001$\u0001$\u0001$\u0001%\u0001%\u0005"+
		"%\u0209\b%\n%\f%\u020c\t%\u0001%\u0004%\u020f\b%\u000b%\f%\u0210\u0001"+
		"%\u0005%\u0214\b%\n%\f%\u0217\t%\u0001&\u0005&\u021a\b&\n&\f&\u021d\t"+
		"&\u0001&\u0001&\u0005&\u0221\b&\n&\f&\u0224\t&\u0001&\u0004&\u0227\b&"+
		"\u000b&\f&\u0228\u0001\'\u0001\'\u0001\'\u0001\'\u0001\'\u0001\'\u0005"+
		"\'\u0231\b\'\n\'\f\'\u0234\t\'\u0001\'\u0001\'\u0001\'\u0001\'\u0001\'"+
		"\u0005\'\u023b\b\'\n\'\f\'\u023e\t\'\u0001\'\u0001\'\u0001\'\u0001\'\u0001"+
		"\'\u0005\'\u0245\b\'\n\'\f\'\u0248\t\'\u0001\'\u0001\'\u0005\'\u024c\b"+
		"\'\n\'\f\'\u024f\t\'\u0001\'\u0001\'\u0001(\u0001(\u0001(\u0001)\u0001"+
		")\u0001*\u0001*\u0001*\u0001*\u0005*\u025c\b*\n*\f*\u025f\t*\u0001*\u0001"+
		"*\u0001*\u0001*\u0001*\u0003*\u0266\b*\u0001*\u0005*\u0269\b*\n*\f*\u026c"+
		"\t*\u0001*\u0001*\u0001*\u0001*\u0005*\u0272\b*\n*\f*\u0275\t*\u0003*"+
		"\u0277\b*\u0001*\u0001*\u0001+\u0001+\u0001+\u0001+\u0001,\u0001,\u0001"+
		",\u0001,\u0001,\u0001,\u0001,\u0005,\u0286\b,\n,\f,\u0289\t,\u0001,\u0005"+
		",\u028c\b,\n,\f,\u028f\t,\u0001,\u0001,\u0001,\u0001-\u0001-\u0003-\u0296"+
		"\b-\u0001.\u0001.\u0001.\u0005.\u029b\b.\n.\f.\u029e\t.\u0001.\u0001."+
		"\u0001.\u0001/\u0001/\u0001/\u00010\u00010\u00010\u00050\u02a9\b0\n0\f"+
		"0\u02ac\t0\u00010\u00010\u00010\u00010\u00050\u02b2\b0\n0\f0\u02b5\t0"+
		"\u00010\u00010\u00010\u00010\u00010\u00050\u02bc\b0\n0\f0\u02bf\t0\u0001"+
		"0\u00010\u00010\u00010\u00050\u02c5\b0\n0\f0\u02c8\t0\u00030\u02ca\b0"+
		"\u00010\u00010\u00011\u00041\u02cf\b1\u000b1\f1\u02d0\u00011\u00011\u0001"+
		"2\u00012\u00013\u00043\u02d8\b3\u000b3\f3\u02d9\u00013\u00013\u00013\u0001"+
		"3\u00014\u00014\u00015\u00015\u00055\u02e4\b5\n5\f5\u02e7\t5\u00015\u0003"+
		"5\u02ea\b5\u00015\u00015\u00015\u00015\u00016\u00016\u00016\u00016\u0001"+
		"6\u00017\u00047\u02f6\b7\u000b7\f7\u02f7\u00017\u00017\u00018\u00018\u0001"+
		"8\u00018\u00058\u0300\b8\n8\f8\u0303\t8\u00018\u00018\u00018\u00018\u0001"+
		"8\u00019\u00019\u00019\u00039\u030d\b9\u00019\u00059\u0310\b9\n9\f9\u0313"+
		"\t9\u00019\u00039\u0316\b9\u00019\u00019\u0001:\u0001:\u0001:\u0001:\u0003"+
		":\u031e\b:\u0001:\u0001:\u0001:\u0001:\u0001:\u0001;\u0004;\u0326\b;\u000b"+
		";\f;\u0327\u0001;\u0001;\u0001;\u0005;\u032d\b;\n;\f;\u0330\t;\u0001;"+
		"\u0003;\u0333\b;\u0001;\u0001;\u0001<\u0001<\u0001<\u0001<\u0001=\u0004"+
		"=\u033c\b=\u000b=\f=\u033d\u0001=\u0001=\u0001\u0301\u0000>\u0003\u0001"+
		"\u0005\u0002\u0007\u0003\t\u0004\u000b\u0005\r\u0006\u000f\u0007\u0011"+
		"\b\u0013\t\u0015\n\u0017\u000b\u0019\f\u001b\r\u001d\u000e\u001f\u000f"+
		"!\u0010#\u0011%\u0012\'\u0013)\u0014+\u0015-\u0016/\u00171\u00003\u0018"+
		"5\u00007\u00199\u001a;\u001b=\u001c?\u001dA\u0000C\u001eE\u0000G\u0000"+
		"I\u001fK M\u0000O\u0000Q\u0000S\u0000U\u0000W!Y\"[#]\u0000_$a%c&e\'g\u0000"+
		"i(k\u0000m)o\u0000q*s+u,w\u0000y\u0000{.}-\u0003\u0000\u0001\u00021\u0003"+
		"\u0000AZ__az\u0002\u0000\n\n\r\r\u0002\u0000FFff\u0002\u0000RRrr\u0002"+
		"\u0000OOoo\u0002\u0000MMmm\u0002\u0000UUuu\u0002\u0000NNnn\u0002\u0000"+
		"CCcc\u0002\u0000DDdd\u0002\u0000EEee\u0002\u0000LLll\u0002\u0000AAaa\u0002"+
		"\u0000BBbb\u0002\u0000XXxx\u0002\u0000PPpp\u0002\u0000SSss\u0002\u0000"+
		"VVvv\u0002\u0000YYyy\u0002\u0000TTtt\u0002\u0000IIii\u0002\u0000WWww\u0002"+
		"\u0000KKkk\u0002\u0000GGgg\u0002\u0000HHhh\u0004\u000009AZ__az\u0002\u0000"+
		"\\\\``\u0002\u0000\t\t  \u0002\u0000AZaz\u0005\u0000--09AZ__az\u0004\u0000"+
		"\n\n\r\r\"\"\\\\\u0003\u0000\n\n\r\r\'\'\u0006\u0000\t\n\r\r  \"\"\'\'"+
		"\\\\\b\u0000\t\n\r\r  \"\"$$\'\'<=[]\u0005\u0000\n\n\r\r\"\"\\\\``\u0004"+
		"\u0000\n\n\r\r\"\"\')\u0005\u0000\n\n\r\r\"\"\')\\\\\u0003\u000009AFa"+
		"f\u0001\u0000}}\u0005\u0000!!#$**09?@\u0001\u0000()\u0004\u0000\t\n\r"+
		"\r  ``\u0003\u0000\n\n\r\r``\t\u0000\t\n\r\r  \"\"$$\'\'--<=[]\u0003\u0000"+
		"\t\t\f\r  \u0006\u0000\t\n\r\r  <<\\\\``\u0004\u0000\t\n\r\r  <<\u0003"+
		"\u0000\t\n\r\r  \u0001\u0000\n\n\u0393\u0000\u0003\u0001\u0000\u0000\u0000"+
		"\u0000\u0005\u0001\u0000\u0000\u0000\u0000\u0007\u0001\u0000\u0000\u0000"+
		"\u0000\t\u0001\u0000\u0000\u0000\u0000\u000b\u0001\u0000\u0000\u0000\u0000"+
		"\r\u0001\u0000\u0000\u0000\u0000\u000f\u0001\u0000\u0000\u0000\u0000\u0011"+
		"\u0001\u0000\u0000\u0000\u0000\u0013\u0001\u0000\u0000\u0000\u0000\u0015"+
		"\u0001\u0000\u0000\u0000\u0000\u0017\u0001\u0000\u0000\u0000\u0000\u0019"+
		"\u0001\u0000\u0000\u0000\u0000\u001b\u0001\u0000\u0000\u0000\u0000\u001d"+
		"\u0001\u0000\u0000\u0000\u0000\u001f\u0001\u0000\u0000\u0000\u0000!\u0001"+
		"\u0000\u0000\u0000\u0000#\u0001\u0000\u0000\u0000\u0000%\u0001\u0000\u0000"+
		"\u0000\u0000\'\u0001\u0000\u0000\u0000\u0000)\u0001\u0000\u0000\u0000"+
		"\u0000+\u0001\u0000\u0000\u0000\u0000-\u0001\u0000\u0000\u0000\u0000/"+
		"\u0001\u0000\u0000\u0000\u00003\u0001\u0000\u0000\u0000\u00005\u0001\u0000"+
		"\u0000\u0000\u00007\u0001\u0000\u0000\u0000\u00009\u0001\u0000\u0000\u0000"+
		"\u0000;\u0001\u0000\u0000\u0000\u0000=\u0001\u0000\u0000\u0000\u0000?"+
		"\u0001\u0000\u0000\u0000\u0000C\u0001\u0000\u0000\u0000\u0000I\u0001\u0000"+
		"\u0000\u0000\u0000K\u0001\u0000\u0000\u0000\u0000W\u0001\u0000\u0000\u0000"+
		"\u0000Y\u0001\u0000\u0000\u0000\u0000[\u0001\u0000\u0000\u0000\u0000_"+
		"\u0001\u0000\u0000\u0000\u0000a\u0001\u0000\u0000\u0000\u0000c\u0001\u0000"+
		"\u0000\u0000\u0000e\u0001\u0000\u0000\u0000\u0000i\u0001\u0000\u0000\u0000"+
		"\u0001m\u0001\u0000\u0000\u0000\u0001o\u0001\u0000\u0000\u0000\u0001q"+
		"\u0001\u0000\u0000\u0000\u0001s\u0001\u0000\u0000\u0000\u0001u\u0001\u0000"+
		"\u0000\u0000\u0001w\u0001\u0000\u0000\u0000\u0001y\u0001\u0000\u0000\u0000"+
		"\u0002{\u0001\u0000\u0000\u0000\u0002}\u0001\u0000\u0000\u0000\u0003\u007f"+
		"\u0001\u0000\u0000\u0000\u0005\u00a2\u0001\u0000\u0000\u0000\u0007\u00ab"+
		"\u0001\u0000\u0000\u0000\t\u00b2\u0001\u0000\u0000\u0000\u000b\u00b8\u0001"+
		"\u0000\u0000\u0000\r\u00be\u0001\u0000\u0000\u0000\u000f\u00c5\u0001\u0000"+
		"\u0000\u0000\u0011\u00cd\u0001\u0000\u0000\u0000\u0013\u00d6\u0001\u0000"+
		"\u0000\u0000\u0015\u00dc\u0001\u0000\u0000\u0000\u0017\u00e2\u0001\u0000"+
		"\u0000\u0000\u0019\u00e9\u0001\u0000\u0000\u0000\u001b\u00f6\u0001\u0000"+
		"\u0000\u0000\u001d\u00ff\u0001\u0000\u0000\u0000\u001f\u0106\u0001\u0000"+
		"\u0000\u0000!\u0110\u0001\u0000\u0000\u0000#\u0116\u0001\u0000\u0000\u0000"+
		"%\u0120\u0001\u0000\u0000\u0000\'\u012d\u0001\u0000\u0000\u0000)\u013b"+
		"\u0001\u0000\u0000\u0000+\u0143\u0001\u0000\u0000\u0000-\u0150\u0001\u0000"+
		"\u0000\u0000/\u0155\u0001\u0000\u0000\u00001\u0179\u0001\u0000\u0000\u0000"+
		"3\u017b\u0001\u0000\u0000\u00005\u0189\u0001\u0000\u0000\u00007\u0196"+
		"\u0001\u0000\u0000\u00009\u0199\u0001\u0000\u0000\u0000;\u019c\u0001\u0000"+
		"\u0000\u0000=\u019f\u0001\u0000\u0000\u0000?\u01a2\u0001\u0000\u0000\u0000"+
		"A\u01cb\u0001\u0000\u0000\u0000C\u01cd\u0001\u0000\u0000\u0000E\u01d2"+
		"\u0001\u0000\u0000\u0000G\u01d4\u0001\u0000\u0000\u0000I\u01d7\u0001\u0000"+
		"\u0000\u0000K\u01f0\u0001\u0000\u0000\u0000M\u0206\u0001\u0000\u0000\u0000"+
		"O\u021b\u0001\u0000\u0000\u0000Q\u022a\u0001\u0000\u0000\u0000S\u0252"+
		"\u0001\u0000\u0000\u0000U\u0255\u0001\u0000\u0000\u0000W\u0276\u0001\u0000"+
		"\u0000\u0000Y\u027a\u0001\u0000\u0000\u0000[\u027e\u0001\u0000\u0000\u0000"+
		"]\u0295\u0001\u0000\u0000\u0000_\u0297\u0001\u0000\u0000\u0000a\u02a2"+
		"\u0001\u0000\u0000\u0000c\u02c9\u0001\u0000\u0000\u0000e\u02ce\u0001\u0000"+
		"\u0000\u0000g\u02d4\u0001\u0000\u0000\u0000i\u02d7\u0001\u0000\u0000\u0000"+
		"k\u02df\u0001\u0000\u0000\u0000m\u02e1\u0001\u0000\u0000\u0000o\u02ef"+
		"\u0001\u0000\u0000\u0000q\u02f5\u0001\u0000\u0000\u0000s\u02fb\u0001\u0000"+
		"\u0000\u0000u\u030c\u0001\u0000\u0000\u0000w\u0319\u0001\u0000\u0000\u0000"+
		"y\u0332\u0001\u0000\u0000\u0000{\u0336\u0001\u0000\u0000\u0000}\u033b"+
		"\u0001\u0000\u0000\u0000\u007f\u0083\u0005#\u0000\u0000\u0080\u0082\u0003"+
		"g2\u0000\u0081\u0080\u0001\u0000\u0000\u0000\u0082\u0085\u0001\u0000\u0000"+
		"\u0000\u0083\u0081\u0001\u0000\u0000\u0000\u0083\u0084\u0001\u0000\u0000"+
		"\u0000\u0084\u0087\u0001\u0000\u0000\u0000\u0085\u0083\u0001\u0000\u0000"+
		"\u0000\u0086\u0088\u0007\u0000\u0000\u0000\u0087\u0086\u0001\u0000\u0000"+
		"\u0000\u0088\u0089\u0001\u0000\u0000\u0000\u0089\u0087\u0001\u0000\u0000"+
		"\u0000\u0089\u008a\u0001\u0000\u0000\u0000\u008a\u008e\u0001\u0000\u0000"+
		"\u0000\u008b\u008d\u0003g2\u0000\u008c\u008b\u0001\u0000\u0000\u0000\u008d"+
		"\u0090\u0001\u0000\u0000\u0000\u008e\u008c\u0001\u0000\u0000\u0000\u008e"+
		"\u008f\u0001\u0000\u0000\u0000\u008f\u0091\u0001\u0000\u0000\u0000\u0090"+
		"\u008e\u0001\u0000\u0000\u0000\u0091\u0095\u0005=\u0000\u0000\u0092\u0094"+
		"\u0003g2\u0000\u0093\u0092\u0001\u0000\u0000\u0000\u0094\u0097\u0001\u0000"+
		"\u0000\u0000\u0095\u0093\u0001\u0000\u0000\u0000\u0095\u0096\u0001\u0000"+
		"\u0000\u0000\u0096\u009b\u0001\u0000\u0000\u0000\u0097\u0095\u0001\u0000"+
		"\u0000\u0000\u0098\u009a\b\u0001\u0000\u0000\u0099\u0098\u0001\u0000\u0000"+
		"\u0000\u009a\u009d\u0001\u0000\u0000\u0000\u009b\u0099\u0001\u0000\u0000"+
		"\u0000\u009b\u009c\u0001\u0000\u0000\u0000\u009c\u009e\u0001\u0000\u0000"+
		"\u0000\u009d\u009b\u0001\u0000\u0000\u0000\u009e\u009f\u0003k4\u0000\u009f"+
		"\u00a0\u0004\u0000\u0000\u0000\u00a0\u00a1\u0006\u0000\u0000\u0000\u00a1"+
		"\u0004\u0001\u0000\u0000\u0000\u00a2\u00a6\u0005#\u0000\u0000\u00a3\u00a5"+
		"\b\u0001\u0000\u0000\u00a4\u00a3\u0001\u0000\u0000\u0000\u00a5\u00a8\u0001"+
		"\u0000\u0000\u0000\u00a6\u00a4\u0001\u0000\u0000\u0000\u00a6\u00a7\u0001"+
		"\u0000\u0000\u0000\u00a7\u00a9\u0001\u0000\u0000\u0000\u00a8\u00a6\u0001"+
		"\u0000\u0000\u0000\u00a9\u00aa\u0006\u0001\u0001\u0000\u00aa\u0006\u0001"+
		"\u0000\u0000\u0000\u00ab\u00ac\u0007\u0002\u0000\u0000\u00ac\u00ad\u0007"+
		"\u0003\u0000\u0000\u00ad\u00ae\u0007\u0004\u0000\u0000\u00ae\u00af\u0007"+
		"\u0005\u0000\u0000\u00af\u00b0\u0001\u0000\u0000\u0000\u00b0\u00b1\u0006"+
		"\u0002\u0002\u0000\u00b1\b\u0001\u0000\u0000\u0000\u00b2\u00b3\u0007\u0003"+
		"\u0000\u0000\u00b3\u00b4\u0007\u0006\u0000\u0000\u00b4\u00b5\u0007\u0007"+
		"\u0000\u0000\u00b5\u00b6\u0001\u0000\u0000\u0000\u00b6\u00b7\u0006\u0003"+
		"\u0003\u0000\u00b7\n\u0001\u0000\u0000\u0000\u00b8\u00b9\u0007\b\u0000"+
		"\u0000\u00b9\u00ba\u0007\u0005\u0000\u0000\u00ba\u00bb\u0007\t\u0000\u0000"+
		"\u00bb\u00bc\u0001\u0000\u0000\u0000\u00bc\u00bd\u0006\u0004\u0004\u0000"+
		"\u00bd\f\u0001\u0000\u0000\u0000\u00be\u00bf\u0007\u0007\u0000\u0000\u00bf"+
		"\u00c0\u0007\u0004\u0000\u0000\u00c0\u00c1\u0007\u0007\u0000\u0000\u00c1"+
		"\u00c2\u0007\n\u0000\u0000\u00c2\u00c3\u0001\u0000\u0000\u0000\u00c3\u00c4"+
		"\u0006\u0005\u0005\u0000\u00c4\u000e\u0001\u0000\u0000\u0000\u00c5\u00c6"+
		"\u0007\u000b\u0000\u0000\u00c6\u00c7\u0007\f\u0000\u0000\u00c7\u00c8\u0007"+
		"\r\u0000\u0000\u00c8\u00c9\u0007\n\u0000\u0000\u00c9\u00ca\u0007\u000b"+
		"\u0000\u0000\u00ca\u00cb\u0001\u0000\u0000\u0000\u00cb\u00cc\u0006\u0006"+
		"\u0006\u0000\u00cc\u0010\u0001\u0000\u0000\u0000\u00cd\u00ce\u0007\n\u0000"+
		"\u0000\u00ce\u00cf\u0007\u000e\u0000\u0000\u00cf\u00d0\u0007\u000f\u0000"+
		"\u0000\u00d0\u00d1\u0007\u0004\u0000\u0000\u00d1\u00d2\u0007\u0010\u0000"+
		"\u0000\u00d2\u00d3\u0007\n\u0000\u0000\u00d3\u00d4\u0001\u0000\u0000\u0000"+
		"\u00d4\u00d5\u0006\u0007\u0007\u0000\u00d5\u0012\u0001\u0000\u0000\u0000"+
		"\u00d6\u00d7\u0007\n\u0000\u0000\u00d7\u00d8\u0007\u0007\u0000\u0000\u00d8"+
		"\u00d9\u0007\u0011\u0000\u0000\u00d9\u00da\u0001\u0000\u0000\u0000\u00da"+
		"\u00db\u0006\b\b\u0000\u00db\u0014\u0001\u0000\u0000\u0000\u00dc\u00dd"+
		"\u0007\f\u0000\u0000\u00dd\u00de\u0007\t\u0000\u0000\u00de\u00df\u0007"+
		"\t\u0000\u0000\u00df\u00e0\u0001\u0000\u0000\u0000\u00e0\u00e1\u0006\t"+
		"\t\u0000\u00e1\u0016\u0001\u0000\u0000\u0000\u00e2\u00e3\u0007\b\u0000"+
		"\u0000\u00e3\u00e4\u0007\u0004\u0000\u0000\u00e4\u00e5\u0007\u000f\u0000"+
		"\u0000\u00e5\u00e6\u0007\u0012\u0000\u0000\u00e6\u00e7\u0001\u0000\u0000"+
		"\u0000\u00e7\u00e8\u0006\n\n\u0000\u00e8\u0018\u0001\u0000\u0000\u0000"+
		"\u00e9\u00ea\u0007\n\u0000\u0000\u00ea\u00eb\u0007\u0007\u0000\u0000\u00eb"+
		"\u00ec\u0007\u0013\u0000\u0000\u00ec\u00ed\u0007\u0003\u0000\u0000\u00ed"+
		"\u00ee\u0007\u0012\u0000\u0000\u00ee\u00ef\u0007\u000f\u0000\u0000\u00ef"+
		"\u00f0\u0007\u0004\u0000\u0000\u00f0\u00f1\u0007\u0014\u0000\u0000\u00f1"+
		"\u00f2\u0007\u0007\u0000\u0000\u00f2\u00f3\u0007\u0013\u0000\u0000\u00f3"+
		"\u00f4\u0001\u0000\u0000\u0000\u00f4\u00f5\u0006\u000b\u000b\u0000\u00f5"+
		"\u001a\u0001\u0000\u0000\u0000\u00f6\u00f7\u0007\u0011\u0000\u0000\u00f7"+
		"\u00f8\u0007\u0004\u0000\u0000\u00f8\u00f9\u0007\u000b\u0000\u0000\u00f9"+
		"\u00fa\u0007\u0006\u0000\u0000\u00fa\u00fb\u0007\u0005\u0000\u0000\u00fb"+
		"\u00fc\u0007\n\u0000\u0000\u00fc\u00fd\u0001\u0000\u0000\u0000\u00fd\u00fe"+
		"\u0006\f\f\u0000\u00fe\u001c\u0001\u0000\u0000\u0000\u00ff\u0100\u0007"+
		"\u0006\u0000\u0000\u0100\u0101\u0007\u0010\u0000\u0000\u0101\u0102\u0007"+
		"\n\u0000\u0000\u0102\u0103\u0007\u0003\u0000\u0000\u0103\u0104\u0001\u0000"+
		"\u0000\u0000\u0104\u0105\u0006\r\r\u0000\u0105\u001e\u0001\u0000\u0000"+
		"\u0000\u0106\u0107\u0007\u0015\u0000\u0000\u0107\u0108\u0007\u0004\u0000"+
		"\u0000\u0108\u0109\u0007\u0003\u0000\u0000\u0109\u010a\u0007\u0016\u0000"+
		"\u0000\u010a\u010b\u0007\t\u0000\u0000\u010b\u010c\u0007\u0014\u0000\u0000"+
		"\u010c\u010d\u0007\u0003\u0000\u0000\u010d\u010e\u0001\u0000\u0000\u0000"+
		"\u010e\u010f\u0006\u000e\u000e\u0000\u010f \u0001\u0000\u0000\u0000\u0110"+
		"\u0111\u0007\f\u0000\u0000\u0111\u0112\u0007\u0003\u0000\u0000\u0112\u0113"+
		"\u0007\u0017\u0000\u0000\u0113\u0114\u0001\u0000\u0000\u0000\u0114\u0115"+
		"\u0006\u000f\u000f\u0000\u0115\"\u0001\u0000\u0000\u0000\u0116\u0117\u0007"+
		"\u0004\u0000\u0000\u0117\u0118\u0007\u0007\u0000\u0000\u0118\u0119\u0007"+
		"\r\u0000\u0000\u0119\u011a\u0007\u0006\u0000\u0000\u011a\u011b\u0007\u0014"+
		"\u0000\u0000\u011b\u011c\u0007\u000b\u0000\u0000\u011c\u011d\u0007\t\u0000"+
		"\u0000\u011d\u011e\u0001\u0000\u0000\u0000\u011e\u011f\u0006\u0010\u0010"+
		"\u0000\u011f$\u0001\u0000\u0000\u0000\u0120\u0121\u0007\u0010\u0000\u0000"+
		"\u0121\u0122\u0007\u0013\u0000\u0000\u0122\u0123\u0007\u0004\u0000\u0000"+
		"\u0123\u0124\u0007\u000f\u0000\u0000\u0124\u0125\u0007\u0010\u0000\u0000"+
		"\u0125\u0126\u0007\u0014\u0000\u0000\u0126\u0127\u0007\u0017\u0000\u0000"+
		"\u0127\u0128\u0007\u0007\u0000\u0000\u0128\u0129\u0007\f\u0000\u0000\u0129"+
		"\u012a\u0007\u000b\u0000\u0000\u012a\u012b\u0001\u0000\u0000\u0000\u012b"+
		"\u012c\u0006\u0011\u0011\u0000\u012c&\u0001\u0000\u0000\u0000\u012d\u012e"+
		"\u0007\u0018\u0000\u0000\u012e\u012f\u0007\n\u0000\u0000\u012f\u0130\u0007"+
		"\f\u0000\u0000\u0130\u0131\u0007\u000b\u0000\u0000\u0131\u0132\u0007\u0013"+
		"\u0000\u0000\u0132\u0133\u0007\u0018\u0000\u0000\u0133\u0134\u0007\b\u0000"+
		"\u0000\u0134\u0135\u0007\u0018\u0000\u0000\u0135\u0136\u0007\n\u0000\u0000"+
		"\u0136\u0137\u0007\b\u0000\u0000\u0137\u0138\u0007\u0016\u0000\u0000\u0138"+
		"\u0139\u0001\u0000\u0000\u0000\u0139\u013a\u0006\u0012\u0012\u0000\u013a"+
		"(\u0001\u0000\u0000\u0000\u013b\u013c\u0007\u0010\u0000\u0000\u013c\u013d"+
		"\u0007\u0018\u0000\u0000\u013d\u013e\u0007\n\u0000\u0000\u013e\u013f\u0007"+
		"\u000b\u0000\u0000\u013f\u0140\u0007\u000b\u0000\u0000\u0140\u0141\u0001"+
		"\u0000\u0000\u0000\u0141\u0142\u0006\u0013\u0013\u0000\u0142*\u0001\u0000"+
		"\u0000\u0000\u0143\u0144\u0007\u0005\u0000\u0000\u0144\u0145\u0007\f\u0000"+
		"\u0000\u0145\u0146\u0007\u0014\u0000\u0000\u0146\u0147\u0007\u0007\u0000"+
		"\u0000\u0147\u0148\u0007\u0013\u0000\u0000\u0148\u0149\u0007\f\u0000\u0000"+
		"\u0149\u014a\u0007\u0014\u0000\u0000\u014a\u014b\u0007\u0007\u0000\u0000"+
		"\u014b\u014c\u0007\n\u0000\u0000\u014c\u014d\u0007\u0003\u0000\u0000\u014d"+
		"\u014e\u0001\u0000\u0000\u0000\u014e\u014f\u0006\u0014\u0014\u0000\u014f"+
		",\u0001\u0000\u0000\u0000\u0150\u0151\u0007\f\u0000\u0000\u0151\u0152"+
		"\u0007\u0010\u0000\u0000\u0152\u0153\u0001\u0000\u0000\u0000\u0153\u0154"+
		"\u0006\u0015\u0015\u0000\u0154.\u0001\u0000\u0000\u0000\u0155\u0156\u0005"+
		"<\u0000\u0000\u0156\u0157\u0005<\u0000\u0000\u0157\u0159\u0001\u0000\u0000"+
		"\u0000\u0158\u015a\u0005-\u0000\u0000\u0159\u0158\u0001\u0000\u0000\u0000"+
		"\u0159\u015a\u0001\u0000\u0000\u0000\u015a\u015b\u0001\u0000\u0000\u0000"+
		"\u015b\u015c\u00031\u0017\u0000\u015c\u015d\u0006\u0016\u0016\u0000\u015d"+
		"\u015e\u0001\u0000\u0000\u0000\u015e\u015f\u0006\u0016\u0017\u0000\u015f"+
		"0\u0001\u0000\u0000\u0000\u0160\u0164\u0007\u0000\u0000\u0000\u0161\u0163"+
		"\u0007\u0019\u0000\u0000\u0162\u0161\u0001\u0000\u0000\u0000\u0163\u0166"+
		"\u0001\u0000\u0000\u0000\u0164\u0162\u0001\u0000\u0000\u0000\u0164\u0165"+
		"\u0001\u0000\u0000\u0000\u0165\u017a\u0001\u0000\u0000\u0000\u0166\u0164"+
		"\u0001\u0000\u0000\u0000\u0167\u0168\u0005\'\u0000\u0000\u0168\u016c\u0007"+
		"\u0000\u0000\u0000\u0169\u016b\u0007\u0019\u0000\u0000\u016a\u0169\u0001"+
		"\u0000\u0000\u0000\u016b\u016e\u0001\u0000\u0000\u0000\u016c\u016a\u0001"+
		"\u0000\u0000\u0000\u016c\u016d\u0001\u0000\u0000\u0000\u016d\u016f\u0001"+
		"\u0000\u0000\u0000\u016e\u016c\u0001\u0000\u0000\u0000\u016f\u017a\u0005"+
		"\'\u0000\u0000\u0170\u0171\u0005\"\u0000\u0000\u0171\u0175\u0007\u0000"+
		"\u0000\u0000\u0172\u0174\u0007\u0019\u0000\u0000\u0173\u0172\u0001\u0000"+
		"\u0000\u0000\u0174\u0177\u0001\u0000\u0000\u0000\u0175\u0173\u0001\u0000"+
		"\u0000\u0000\u0175\u0176\u0001\u0000\u0000\u0000\u0176\u0178\u0001\u0000"+
		"\u0000\u0000\u0177\u0175\u0001\u0000\u0000\u0000\u0178\u017a\u0005\"\u0000"+
		"\u0000\u0179\u0160\u0001\u0000\u0000\u0000\u0179\u0167\u0001\u0000\u0000"+
		"\u0000\u0179\u0170\u0001\u0000\u0000\u0000\u017a2\u0001\u0000\u0000\u0000"+
		"\u017b\u017f\u0007\u001a\u0000\u0000\u017c\u017e\u0007\u001b\u0000\u0000"+
		"\u017d\u017c\u0001\u0000\u0000\u0000\u017e\u0181\u0001\u0000\u0000\u0000"+
		"\u017f\u017d\u0001\u0000\u0000\u0000\u017f\u0180\u0001\u0000\u0000\u0000"+
		"\u0180\u0183\u0001\u0000\u0000\u0000\u0181\u017f\u0001\u0000\u0000\u0000"+
		"\u0182\u0184\u0005\r\u0000\u0000\u0183\u0182\u0001\u0000\u0000\u0000\u0183"+
		"\u0184\u0001\u0000\u0000\u0000\u0184\u0185\u0001\u0000\u0000\u0000\u0185"+
		"\u0186\u0005\n\u0000\u0000\u0186\u0187\u0001\u0000\u0000\u0000\u0187\u0188"+
		"\u0006\u0018\u0001\u0000\u01884\u0001\u0000\u0000\u0000\u0189\u018a\u0004"+
		"\u0019\u0001\u0000\u018a\u018f\u0005[\u0000\u0000\u018b\u018e\u0003M%"+
		"\u0000\u018c\u018e\b\u0001\u0000\u0000\u018d\u018b\u0001\u0000\u0000\u0000"+
		"\u018d\u018c\u0001\u0000\u0000\u0000\u018e\u0191\u0001\u0000\u0000\u0000"+
		"\u018f\u018d\u0001\u0000\u0000\u0000\u018f\u0190\u0001\u0000\u0000\u0000"+
		"\u0190\u0192\u0001\u0000\u0000\u0000\u0191\u018f\u0001\u0000\u0000\u0000"+
		"\u0192\u0193\u0006\u0019\u0018\u0000\u0193\u0194\u0001\u0000\u0000\u0000"+
		"\u0194\u0195\u0006\u0019\u0019\u0000\u01956\u0001\u0000\u0000\u0000\u0196"+
		"\u0197\u0005[\u0000\u0000\u0197\u0198\u0006\u001a\u001a\u0000\u01988\u0001"+
		"\u0000\u0000\u0000\u0199\u019a\u0005]\u0000\u0000\u019a\u019b\u0006\u001b"+
		"\u001b\u0000\u019b:\u0001\u0000\u0000\u0000\u019c\u019d\u0005,\u0000\u0000"+
		"\u019d\u019e\u0006\u001c\u001c\u0000\u019e<\u0001\u0000\u0000\u0000\u019f"+
		"\u01a0\u0005=\u0000\u0000\u01a0\u01a1\u0006\u001d\u001d\u0000\u01a1>\u0001"+
		"\u0000\u0000\u0000\u01a2\u01a3\u0005-\u0000\u0000\u01a3\u01a4\u0005-\u0000"+
		"\u0000\u01a4\u01a5\u0001\u0000\u0000\u0000\u01a5\u01a9\u0007\u001c\u0000"+
		"\u0000\u01a6\u01a8\u0007\u001d\u0000\u0000\u01a7\u01a6\u0001\u0000\u0000"+
		"\u0000\u01a8\u01ab\u0001\u0000\u0000\u0000\u01a9\u01a7\u0001\u0000\u0000"+
		"\u0000\u01a9\u01aa\u0001\u0000\u0000\u0000\u01aa\u01b2\u0001\u0000\u0000"+
		"\u0000\u01ab\u01a9\u0001\u0000\u0000\u0000\u01ac\u01ae\u0005=\u0000\u0000"+
		"\u01ad\u01af\u0003A\u001f\u0000\u01ae\u01ad\u0001\u0000\u0000\u0000\u01af"+
		"\u01b0\u0001\u0000\u0000\u0000\u01b0\u01ae\u0001\u0000\u0000\u0000\u01b0"+
		"\u01b1\u0001\u0000\u0000\u0000\u01b1\u01b3\u0001\u0000\u0000\u0000\u01b2"+
		"\u01ac\u0001\u0000\u0000\u0000\u01b2\u01b3\u0001\u0000\u0000\u0000\u01b3"+
		"\u01b4\u0001\u0000\u0000\u0000\u01b4\u01b5\u0006\u001e\u001e\u0000\u01b5"+
		"@\u0001\u0000\u0000\u0000\u01b6\u01bc\u0005\"\u0000\u0000\u01b7\u01b8"+
		"\u0005\\\u0000\u0000\u01b8\u01bb\b\u0001\u0000\u0000\u01b9\u01bb\b\u001e"+
		"\u0000\u0000\u01ba\u01b7\u0001\u0000\u0000\u0000\u01ba\u01b9\u0001\u0000"+
		"\u0000\u0000\u01bb\u01be\u0001\u0000\u0000\u0000\u01bc\u01ba\u0001\u0000"+
		"\u0000\u0000\u01bc\u01bd\u0001\u0000\u0000\u0000\u01bd\u01bf\u0001\u0000"+
		"\u0000\u0000\u01be\u01bc\u0001\u0000\u0000\u0000\u01bf\u01cc\u0005\"\u0000"+
		"\u0000\u01c0\u01c4\u0005\'\u0000\u0000\u01c1\u01c3\b\u001f\u0000\u0000"+
		"\u01c2\u01c1\u0001\u0000\u0000\u0000\u01c3\u01c6\u0001\u0000\u0000\u0000"+
		"\u01c4\u01c2\u0001\u0000\u0000\u0000\u01c4\u01c5\u0001\u0000\u0000\u0000"+
		"\u01c5\u01c7\u0001\u0000\u0000\u0000\u01c6\u01c4\u0001\u0000\u0000\u0000"+
		"\u01c7\u01cc\u0005\'\u0000\u0000\u01c8\u01cc\b \u0000\u0000\u01c9\u01ca"+
		"\u0005\\\u0000\u0000\u01ca\u01cc\b\u0001\u0000\u0000\u01cb\u01b6\u0001"+
		"\u0000\u0000\u0000\u01cb\u01c0\u0001\u0000\u0000\u0000\u01cb\u01c8\u0001"+
		"\u0000\u0000\u0000\u01cb\u01c9\u0001\u0000\u0000\u0000\u01ccB\u0001\u0000"+
		"\u0000\u0000\u01cd\u01ce\u0005-\u0000\u0000\u01ce\u01cf\u0005-\u0000\u0000"+
		"\u01cf\u01d0\u0001\u0000\u0000\u0000\u01d0\u01d1\u0006 \u001f\u0000\u01d1"+
		"D\u0001\u0000\u0000\u0000\u01d2\u01d3\b!\u0000\u0000\u01d3F\u0001\u0000"+
		"\u0000\u0000\u01d4\u01d5\u0005\\\u0000\u0000\u01d5\u01d6\b\u0001\u0000"+
		"\u0000\u01d6H\u0001\u0000\u0000\u0000\u01d7\u01ea\u0005\"\u0000\u0000"+
		"\u01d8\u01e9\u0003Q\'\u0000\u01d9\u01e9\u0003S(\u0000\u01da\u01e9\u0003"+
		"M%\u0000\u01db\u01e9\u0005`\u0000\u0000\u01dc\u01de\u0007\u0001\u0000"+
		"\u0000\u01dd\u01dc\u0001\u0000\u0000\u0000\u01de\u01df\u0001\u0000\u0000"+
		"\u0000\u01df\u01dd\u0001\u0000\u0000\u0000\u01df\u01e0\u0001\u0000\u0000"+
		"\u0000\u01e0\u01e4\u0001\u0000\u0000\u0000\u01e1\u01e3\u0003O&\u0000\u01e2"+
		"\u01e1\u0001\u0000\u0000\u0000\u01e3\u01e6\u0001\u0000\u0000\u0000\u01e4"+
		"\u01e2\u0001\u0000\u0000\u0000\u01e4\u01e5\u0001\u0000\u0000\u0000\u01e5"+
		"\u01e9\u0001\u0000\u0000\u0000\u01e6\u01e4\u0001\u0000\u0000\u0000\u01e7"+
		"\u01e9\b\"\u0000\u0000\u01e8\u01d8\u0001\u0000\u0000\u0000\u01e8\u01d9"+
		"\u0001\u0000\u0000\u0000\u01e8\u01da\u0001\u0000\u0000\u0000\u01e8\u01db"+
		"\u0001\u0000\u0000\u0000\u01e8\u01dd\u0001\u0000\u0000\u0000\u01e8\u01e7"+
		"\u0001\u0000\u0000\u0000\u01e9\u01ec\u0001\u0000\u0000\u0000\u01ea\u01e8"+
		"\u0001\u0000\u0000\u0000\u01ea\u01eb\u0001\u0000\u0000\u0000\u01eb\u01ed"+
		"\u0001\u0000\u0000\u0000\u01ec\u01ea\u0001\u0000\u0000\u0000\u01ed\u01ee"+
		"\u0005\"\u0000\u0000\u01ee\u01ef\u0006# \u0000\u01efJ\u0001\u0000\u0000"+
		"\u0000\u01f0\u0200\u0005\'\u0000\u0000\u01f1\u01ff\u0003M%\u0000\u01f2"+
		"\u01f4\u0007\u0001\u0000\u0000\u01f3\u01f2\u0001\u0000\u0000\u0000\u01f4"+
		"\u01f5\u0001\u0000\u0000\u0000\u01f5\u01f3\u0001\u0000\u0000\u0000\u01f5"+
		"\u01f6\u0001\u0000\u0000\u0000\u01f6\u01fa\u0001\u0000\u0000\u0000\u01f7"+
		"\u01f9\u0003O&\u0000\u01f8\u01f7\u0001\u0000\u0000\u0000\u01f9\u01fc\u0001"+
		"\u0000\u0000\u0000\u01fa\u01f8\u0001\u0000\u0000\u0000\u01fa\u01fb\u0001"+
		"\u0000\u0000\u0000\u01fb\u01ff\u0001\u0000\u0000\u0000\u01fc\u01fa\u0001"+
		"\u0000\u0000\u0000\u01fd\u01ff\b\u001f\u0000\u0000\u01fe\u01f1\u0001\u0000"+
		"\u0000\u0000\u01fe\u01f3\u0001\u0000\u0000\u0000\u01fe\u01fd\u0001\u0000"+
		"\u0000\u0000\u01ff\u0202\u0001\u0000\u0000\u0000\u0200\u01fe\u0001\u0000"+
		"\u0000\u0000\u0200\u0201\u0001\u0000\u0000\u0000\u0201\u0203\u0001\u0000"+
		"\u0000\u0000\u0202\u0200\u0001\u0000\u0000\u0000\u0203\u0204\u0005\'\u0000"+
		"\u0000\u0204\u0205\u0006$!\u0000\u0205L\u0001\u0000\u0000\u0000\u0206"+
		"\u020a\u0007\u001a\u0000\u0000\u0207\u0209\u0007\u001b\u0000\u0000\u0208"+
		"\u0207\u0001\u0000\u0000\u0000\u0209\u020c\u0001\u0000\u0000\u0000\u020a"+
		"\u0208\u0001\u0000\u0000\u0000\u020a\u020b\u0001\u0000\u0000\u0000\u020b"+
		"\u020e\u0001\u0000\u0000\u0000\u020c\u020a\u0001\u0000\u0000\u0000\u020d"+
		"\u020f\u0007\u0001\u0000\u0000\u020e\u020d\u0001\u0000\u0000\u0000\u020f"+
		"\u0210\u0001\u0000\u0000\u0000\u0210\u020e\u0001\u0000\u0000\u0000\u0210"+
		"\u0211\u0001\u0000\u0000\u0000\u0211\u0215\u0001\u0000\u0000\u0000\u0212"+
		"\u0214\u0003O&\u0000\u0213\u0212\u0001\u0000\u0000\u0000\u0214\u0217\u0001"+
		"\u0000\u0000\u0000\u0215\u0213\u0001\u0000\u0000\u0000\u0215\u0216\u0001"+
		"\u0000\u0000\u0000\u0216N\u0001\u0000\u0000\u0000\u0217\u0215\u0001\u0000"+
		"\u0000\u0000\u0218\u021a\u0007\u001b\u0000\u0000\u0219\u0218\u0001\u0000"+
		"\u0000\u0000\u021a\u021d\u0001\u0000\u0000\u0000\u021b\u0219\u0001\u0000"+
		"\u0000\u0000\u021b\u021c\u0001\u0000\u0000\u0000\u021c\u021e\u0001\u0000"+
		"\u0000\u0000\u021d\u021b\u0001\u0000\u0000\u0000\u021e\u0222\u0005#\u0000"+
		"\u0000\u021f\u0221\b\u0001\u0000\u0000\u0220\u021f\u0001\u0000\u0000\u0000"+
		"\u0221\u0224\u0001\u0000\u0000\u0000\u0222\u0220\u0001\u0000\u0000\u0000"+
		"\u0222\u0223\u0001\u0000\u0000\u0000\u0223\u0226\u0001\u0000\u0000\u0000"+
		"\u0224\u0222\u0001\u0000\u0000\u0000\u0225\u0227\u0007\u0001\u0000\u0000"+
		"\u0226\u0225\u0001\u0000\u0000\u0000\u0227\u0228\u0001\u0000\u0000\u0000"+
		"\u0228\u0226\u0001\u0000\u0000\u0000\u0228\u0229\u0001\u0000\u0000\u0000"+
		"\u0229P\u0001\u0000\u0000\u0000\u022a\u022b\u0005$\u0000\u0000\u022b\u022c"+
		"\u0005(\u0000\u0000\u022c\u024d\u0001\u0000\u0000\u0000\u022d\u024c\u0003"+
		"Q\'\u0000\u022e\u0232\u0005\'\u0000\u0000\u022f\u0231\b\u001f\u0000\u0000"+
		"\u0230\u022f\u0001\u0000\u0000\u0000\u0231\u0234\u0001\u0000\u0000\u0000"+
		"\u0232\u0230\u0001\u0000\u0000\u0000\u0232\u0233\u0001\u0000\u0000\u0000"+
		"\u0233\u0235\u0001\u0000\u0000\u0000\u0234\u0232\u0001\u0000\u0000\u0000"+
		"\u0235\u024c\u0005\'\u0000\u0000\u0236\u023c\u0005\"\u0000\u0000\u0237"+
		"\u023b\u0003Q\'\u0000\u0238\u023b\u0003S(\u0000\u0239\u023b\b\u001e\u0000"+
		"\u0000\u023a\u0237\u0001\u0000\u0000\u0000\u023a\u0238\u0001\u0000\u0000"+
		"\u0000\u023a\u0239\u0001\u0000\u0000\u0000\u023b\u023e\u0001\u0000\u0000"+
		"\u0000\u023c\u023a\u0001\u0000\u0000\u0000\u023c\u023d\u0001\u0000\u0000"+
		"\u0000\u023d\u023f\u0001\u0000\u0000\u0000\u023e\u023c\u0001\u0000\u0000"+
		"\u0000\u023f\u024c\u0005\"\u0000\u0000\u0240\u024c\u0003S(\u0000\u0241"+
		"\u024c\u0003M%\u0000\u0242\u0246\u0005(\u0000\u0000\u0243\u0245\b#\u0000"+
		"\u0000\u0244\u0243\u0001\u0000\u0000\u0000\u0245\u0248\u0001\u0000\u0000"+
		"\u0000\u0246\u0244\u0001\u0000\u0000\u0000\u0246\u0247\u0001\u0000\u0000"+
		"\u0000\u0247\u0249\u0001\u0000\u0000\u0000\u0248\u0246\u0001\u0000\u0000"+
		"\u0000\u0249\u024c\u0005)\u0000\u0000\u024a\u024c\b$\u0000\u0000\u024b"+
		"\u022d\u0001\u0000\u0000\u0000\u024b\u022e\u0001\u0000\u0000\u0000\u024b"+
		"\u0236\u0001\u0000\u0000\u0000\u024b\u0240\u0001\u0000\u0000\u0000\u024b"+
		"\u0241\u0001\u0000\u0000\u0000\u024b\u0242\u0001\u0000\u0000\u0000\u024b"+
		"\u024a\u0001\u0000\u0000\u0000\u024c\u024f\u0001\u0000\u0000\u0000\u024d"+
		"\u024b\u0001\u0000\u0000\u0000\u024d\u024e\u0001\u0000\u0000\u0000\u024e"+
		"\u0250\u0001\u0000\u0000\u0000\u024f\u024d\u0001\u0000\u0000\u0000\u0250"+
		"\u0251\u0005)\u0000\u0000\u0251R\u0001\u0000\u0000\u0000\u0252\u0253\u0005"+
		"\\\u0000\u0000\u0253\u0254\b\u0001\u0000\u0000\u0254T\u0001\u0000\u0000"+
		"\u0000\u0255\u0256\u0007%\u0000\u0000\u0256V\u0001\u0000\u0000\u0000\u0257"+
		"\u0258\u0005$\u0000\u0000\u0258\u0259\u0005{\u0000\u0000\u0259\u025d\u0007"+
		"\u0000\u0000\u0000\u025a\u025c\u0007\u0019\u0000\u0000\u025b\u025a\u0001"+
		"\u0000\u0000\u0000\u025c\u025f\u0001\u0000\u0000\u0000\u025d\u025b\u0001"+
		"\u0000\u0000\u0000\u025d\u025e\u0001\u0000\u0000\u0000\u025e\u0265\u0001"+
		"\u0000\u0000\u0000\u025f\u025d\u0001\u0000\u0000\u0000\u0260\u0261\u0005"+
		":\u0000\u0000\u0261\u0266\u0005-\u0000\u0000\u0262\u0263\u0005:\u0000"+
		"\u0000\u0263\u0266\u0005+\u0000\u0000\u0264\u0266\u0005:\u0000\u0000\u0265"+
		"\u0260\u0001\u0000\u0000\u0000\u0265\u0262\u0001\u0000\u0000\u0000\u0265"+
		"\u0264\u0001\u0000\u0000\u0000\u0265\u0266\u0001\u0000\u0000\u0000\u0266"+
		"\u026a\u0001\u0000\u0000\u0000\u0267\u0269\b&\u0000\u0000\u0268\u0267"+
		"\u0001\u0000\u0000\u0000\u0269\u026c\u0001\u0000\u0000\u0000\u026a\u0268"+
		"\u0001\u0000\u0000\u0000\u026a\u026b\u0001\u0000\u0000\u0000\u026b\u026d"+
		"\u0001\u0000\u0000\u0000\u026c\u026a\u0001\u0000\u0000\u0000\u026d\u0277"+
		"\u0005}\u0000\u0000\u026e\u026f\u0005$\u0000\u0000\u026f\u0273\u0007\u0000"+
		"\u0000\u0000\u0270\u0272\u0007\u0019\u0000\u0000\u0271\u0270\u0001\u0000"+
		"\u0000\u0000\u0272\u0275\u0001\u0000\u0000\u0000\u0273\u0271\u0001\u0000"+
		"\u0000\u0000\u0273\u0274\u0001\u0000\u0000\u0000\u0274\u0277\u0001\u0000"+
		"\u0000\u0000\u0275\u0273\u0001\u0000\u0000\u0000\u0276\u0257\u0001\u0000"+
		"\u0000\u0000\u0276\u026e\u0001\u0000\u0000\u0000\u0277\u0278\u0001\u0000"+
		"\u0000\u0000\u0278\u0279\u0006*\"\u0000\u0279X\u0001\u0000\u0000\u0000"+
		"\u027a\u027b\u0005$\u0000\u0000\u027b\u027c\u0007\'\u0000\u0000\u027c"+
		"\u027d\u0006+#\u0000\u027dZ\u0001\u0000\u0000\u0000\u027e\u027f\u0005"+
		"$\u0000\u0000\u027f\u0280\u0005(\u0000\u0000\u0280\u028d\u0001\u0000\u0000"+
		"\u0000\u0281\u028c\u0003[,\u0000\u0282\u028c\b(\u0000\u0000\u0283\u0287"+
		"\u0005(\u0000\u0000\u0284\u0286\u0003]-\u0000\u0285\u0284\u0001\u0000"+
		"\u0000\u0000\u0286\u0289\u0001\u0000\u0000\u0000\u0287\u0285\u0001\u0000"+
		"\u0000\u0000\u0287\u0288\u0001\u0000\u0000\u0000\u0288\u028a\u0001\u0000"+
		"\u0000\u0000\u0289\u0287\u0001\u0000\u0000\u0000\u028a\u028c\u0005)\u0000"+
		"\u0000\u028b\u0281\u0001\u0000\u0000\u0000\u028b\u0282\u0001\u0000\u0000"+
		"\u0000\u028b\u0283\u0001\u0000\u0000\u0000\u028c\u028f\u0001\u0000\u0000"+
		"\u0000\u028d\u028b\u0001\u0000\u0000\u0000\u028d\u028e\u0001\u0000\u0000"+
		"\u0000\u028e\u0290\u0001\u0000\u0000\u0000\u028f\u028d\u0001\u0000\u0000"+
		"\u0000\u0290\u0291\u0005)\u0000\u0000\u0291\u0292\u0006,$\u0000\u0292"+
		"\\\u0001\u0000\u0000\u0000\u0293\u0296\u0003[,\u0000\u0294\u0296\b(\u0000"+
		"\u0000\u0295\u0293\u0001\u0000\u0000\u0000\u0295\u0294\u0001\u0000\u0000"+
		"\u0000\u0296^\u0001\u0000\u0000\u0000\u0297\u0298\u0005`\u0000\u0000\u0298"+
		"\u029c\b)\u0000\u0000\u0299\u029b\b*\u0000\u0000\u029a\u0299\u0001\u0000"+
		"\u0000\u0000\u029b\u029e\u0001\u0000\u0000\u0000\u029c\u029a\u0001\u0000"+
		"\u0000\u0000\u029c\u029d\u0001\u0000\u0000\u0000\u029d\u029f\u0001\u0000"+
		"\u0000\u0000\u029e\u029c\u0001\u0000\u0000\u0000\u029f\u02a0\u0005`\u0000"+
		"\u0000\u02a0\u02a1\u0006.%\u0000\u02a1`\u0001\u0000\u0000\u0000\u02a2"+
		"\u02a3\u0005$\u0000\u0000\u02a3\u02a4\u0006/&\u0000\u02a4b\u0001\u0000"+
		"\u0000\u0000\u02a5\u02aa\b+\u0000\u0000\u02a6\u02a9\u0003E!\u0000\u02a7"+
		"\u02a9\u0003G\"\u0000\u02a8\u02a6\u0001\u0000\u0000\u0000\u02a8\u02a7"+
		"\u0001\u0000\u0000\u0000\u02a9\u02ac\u0001\u0000\u0000\u0000\u02aa\u02a8"+
		"\u0001\u0000\u0000\u0000\u02aa\u02ab\u0001\u0000\u0000\u0000\u02ab\u02ca"+
		"\u0001\u0000\u0000\u0000\u02ac\u02aa\u0001\u0000\u0000\u0000\u02ad\u02ae"+
		"\u0005-\u0000\u0000\u02ae\u02b3\b+\u0000\u0000\u02af\u02b2\u0003E!\u0000"+
		"\u02b0\u02b2\u0003G\"\u0000\u02b1\u02af\u0001\u0000\u0000\u0000\u02b1"+
		"\u02b0\u0001\u0000\u0000\u0000\u02b2\u02b5\u0001\u0000\u0000\u0000\u02b3"+
		"\u02b1\u0001\u0000\u0000\u0000\u02b3\u02b4\u0001\u0000\u0000\u0000\u02b4"+
		"\u02ca\u0001\u0000\u0000\u0000\u02b5\u02b3\u0001\u0000\u0000\u0000\u02b6"+
		"\u02ca\u0005-\u0000\u0000\u02b7\u02b8\u0005<\u0000\u0000\u02b8\u02bd\b"+
		"!\u0000\u0000\u02b9\u02bc\u0003E!\u0000\u02ba\u02bc\u0003G\"\u0000\u02bb"+
		"\u02b9\u0001\u0000\u0000\u0000\u02bb\u02ba\u0001\u0000\u0000\u0000\u02bc"+
		"\u02bf\u0001\u0000\u0000\u0000\u02bd\u02bb\u0001\u0000\u0000\u0000\u02bd"+
		"\u02be\u0001\u0000\u0000\u0000\u02be\u02ca\u0001\u0000\u0000\u0000\u02bf"+
		"\u02bd\u0001\u0000\u0000\u0000\u02c0\u02ca\u0005<\u0000\u0000\u02c1\u02c6"+
		"\u0003G\"\u0000\u02c2\u02c5\u0003E!\u0000\u02c3\u02c5\u0003G\"\u0000\u02c4"+
		"\u02c2\u0001\u0000\u0000\u0000\u02c4\u02c3\u0001\u0000\u0000\u0000\u02c5"+
		"\u02c8\u0001\u0000\u0000\u0000\u02c6\u02c4\u0001\u0000\u0000\u0000\u02c6"+
		"\u02c7\u0001\u0000\u0000\u0000\u02c7\u02ca\u0001\u0000\u0000\u0000\u02c8"+
		"\u02c6\u0001\u0000\u0000\u0000\u02c9\u02a5\u0001\u0000\u0000\u0000\u02c9"+
		"\u02ad\u0001\u0000\u0000\u0000\u02c9\u02b6\u0001\u0000\u0000\u0000\u02c9"+
		"\u02b7\u0001\u0000\u0000\u0000\u02c9\u02c0\u0001\u0000\u0000\u0000\u02c9"+
		"\u02c1\u0001\u0000\u0000\u0000\u02ca\u02cb\u0001\u0000\u0000\u0000\u02cb"+
		"\u02cc\u00060\'\u0000\u02ccd\u0001\u0000\u0000\u0000\u02cd\u02cf\u0003"+
		"g2\u0000\u02ce\u02cd\u0001\u0000\u0000\u0000\u02cf\u02d0\u0001\u0000\u0000"+
		"\u0000\u02d0\u02ce\u0001\u0000\u0000\u0000\u02d0\u02d1\u0001\u0000\u0000"+
		"\u0000\u02d1\u02d2\u0001\u0000\u0000\u0000\u02d2\u02d3\u00061\u0001\u0000"+
		"\u02d3f\u0001\u0000\u0000\u0000\u02d4\u02d5\u0007\u001b\u0000\u0000\u02d5"+
		"h\u0001\u0000\u0000\u0000\u02d6\u02d8\u0003k4\u0000\u02d7\u02d6\u0001"+
		"\u0000\u0000\u0000\u02d8\u02d9\u0001\u0000\u0000\u0000\u02d9\u02d7\u0001"+
		"\u0000\u0000\u0000\u02d9\u02da\u0001\u0000\u0000\u0000\u02da\u02db\u0001"+
		"\u0000\u0000\u0000\u02db\u02dc\u00063(\u0000\u02dc\u02dd\u0001\u0000\u0000"+
		"\u0000\u02dd\u02de\u00063\u0001\u0000\u02dej\u0001\u0000\u0000\u0000\u02df"+
		"\u02e0\u0007\u0001\u0000\u0000\u02e0l\u0001\u0000\u0000\u0000\u02e1\u02e5"+
		"\u0007\u001a\u0000\u0000\u02e2\u02e4\u0007\u001b\u0000\u0000\u02e3\u02e2"+
		"\u0001\u0000\u0000\u0000\u02e4\u02e7\u0001\u0000\u0000\u0000\u02e5\u02e3"+
		"\u0001\u0000\u0000\u0000\u02e5\u02e6\u0001\u0000\u0000\u0000\u02e6\u02e9"+
		"\u0001\u0000\u0000\u0000\u02e7\u02e5\u0001\u0000\u0000\u0000\u02e8\u02ea"+
		"\u0005\r\u0000\u0000\u02e9\u02e8\u0001\u0000\u0000\u0000\u02e9\u02ea\u0001"+
		"\u0000\u0000\u0000\u02ea\u02eb\u0001\u0000\u0000\u0000\u02eb\u02ec\u0005"+
		"\n\u0000\u0000\u02ec\u02ed\u0001\u0000\u0000\u0000\u02ed\u02ee\u00065"+
		"\u0001\u0000\u02een\u0001\u0000\u0000\u0000\u02ef\u02f0\u0005\n\u0000"+
		"\u0000\u02f0\u02f1\u0001\u0000\u0000\u0000\u02f1\u02f2\u00066)\u0000\u02f2"+
		"\u02f3\u00066*\u0000\u02f3p\u0001\u0000\u0000\u0000\u02f4\u02f6\u0007"+
		",\u0000\u0000\u02f5\u02f4\u0001\u0000\u0000\u0000\u02f6\u02f7\u0001\u0000"+
		"\u0000\u0000\u02f7\u02f5\u0001\u0000\u0000\u0000\u02f7\u02f8\u0001\u0000"+
		"\u0000\u0000\u02f8\u02f9\u0001\u0000\u0000\u0000\u02f9\u02fa\u00067\u0001"+
		"\u0000\u02far\u0001\u0000\u0000\u0000\u02fb\u02fc\u0005/\u0000\u0000\u02fc"+
		"\u02fd\u0005*\u0000\u0000\u02fd\u0301\u0001\u0000\u0000\u0000\u02fe\u0300"+
		"\t\u0000\u0000\u0000\u02ff\u02fe\u0001\u0000\u0000\u0000\u0300\u0303\u0001"+
		"\u0000\u0000\u0000\u0301\u0302\u0001\u0000\u0000\u0000\u0301\u02ff\u0001"+
		"\u0000\u0000\u0000\u0302\u0304\u0001\u0000\u0000\u0000\u0303\u0301\u0001"+
		"\u0000\u0000\u0000\u0304\u0305\u0005*\u0000\u0000\u0305\u0306\u0005/\u0000"+
		"\u0000\u0306\u0307\u0001\u0000\u0000\u0000\u0307\u0308\u00068\u0001\u0000"+
		"\u0308t\u0001\u0000\u0000\u0000\u0309\u030a\u0005/\u0000\u0000\u030a\u030d"+
		"\u0005/\u0000\u0000\u030b\u030d\u0005#\u0000\u0000\u030c\u0309\u0001\u0000"+
		"\u0000\u0000\u030c\u030b\u0001\u0000\u0000\u0000\u030d\u0311\u0001\u0000"+
		"\u0000\u0000\u030e\u0310\b\u0001\u0000\u0000\u030f\u030e\u0001\u0000\u0000"+
		"\u0000\u0310\u0313\u0001\u0000\u0000\u0000\u0311\u030f\u0001\u0000\u0000"+
		"\u0000\u0311\u0312\u0001\u0000\u0000\u0000\u0312\u0315\u0001\u0000\u0000"+
		"\u0000\u0313\u0311\u0001\u0000\u0000\u0000\u0314\u0316\u0005\r\u0000\u0000"+
		"\u0315\u0314\u0001\u0000\u0000\u0000\u0315\u0316\u0001\u0000\u0000\u0000"+
		"\u0316\u0317\u0001\u0000\u0000\u0000\u0317\u0318\u00069\u0001\u0000\u0318"+
		"v\u0001\u0000\u0000\u0000\u0319\u031a\u0005<\u0000\u0000\u031a\u031b\u0005"+
		"<\u0000\u0000\u031b\u031d\u0001\u0000\u0000\u0000\u031c\u031e\u0005-\u0000"+
		"\u0000\u031d\u031c\u0001\u0000\u0000\u0000\u031d\u031e\u0001\u0000\u0000"+
		"\u0000\u031e\u031f\u0001\u0000\u0000\u0000\u031f\u0320\u00031\u0017\u0000"+
		"\u0320\u0321\u0006:+\u0000\u0321\u0322\u0001\u0000\u0000\u0000\u0322\u0323"+
		"\u0006:,\u0000\u0323x\u0001\u0000\u0000\u0000\u0324\u0326\b-\u0000\u0000"+
		"\u0325\u0324\u0001\u0000\u0000\u0000\u0326\u0327\u0001\u0000\u0000\u0000"+
		"\u0327\u0325\u0001\u0000\u0000\u0000\u0327\u0328\u0001\u0000\u0000\u0000"+
		"\u0328\u0333\u0001\u0000\u0000\u0000\u0329\u032a\u0005<\u0000\u0000\u032a"+
		"\u032e\b.\u0000\u0000\u032b\u032d\b/\u0000\u0000\u032c\u032b\u0001\u0000"+
		"\u0000\u0000\u032d\u0330\u0001\u0000\u0000\u0000\u032e\u032c\u0001\u0000"+
		"\u0000\u0000\u032e\u032f\u0001\u0000\u0000\u0000\u032f\u0333\u0001\u0000"+
		"\u0000\u0000\u0330\u032e\u0001\u0000\u0000\u0000\u0331\u0333\u0005<\u0000"+
		"\u0000\u0332\u0325\u0001\u0000\u0000\u0000\u0332\u0329\u0001\u0000\u0000"+
		"\u0000\u0332\u0331\u0001\u0000\u0000\u0000\u0333\u0334\u0001\u0000\u0000"+
		"\u0000\u0334\u0335\u0006;\u0019\u0000\u0335z\u0001\u0000\u0000\u0000\u0336"+
		"\u0337\u0005\n\u0000\u0000\u0337\u0338\u0001\u0000\u0000\u0000\u0338\u0339"+
		"\u0006<)\u0000\u0339|\u0001\u0000\u0000\u0000\u033a\u033c\b0\u0000\u0000"+
		"\u033b\u033a\u0001\u0000\u0000\u0000\u033c\u033d\u0001\u0000\u0000\u0000"+
		"\u033d\u033b\u0001\u0000\u0000\u0000\u033d\u033e\u0001\u0000\u0000\u0000"+
		"\u033e\u033f\u0001\u0000\u0000\u0000\u033f\u0340\u0006=-\u0000\u0340~"+
		"\u0001\u0000\u0000\u0000N\u0000\u0001\u0002\u0083\u0089\u008e\u0095\u009b"+
		"\u00a6\u0159\u0164\u016c\u0175\u0179\u017f\u0183\u018d\u018f\u01a9\u01b0"+
		"\u01b2\u01ba\u01bc\u01c4\u01cb\u01df\u01e4\u01e8\u01ea\u01f5\u01fa\u01fe"+
		"\u0200\u020a\u0210\u0215\u021b\u0222\u0228\u0232\u023a\u023c\u0246\u024b"+
		"\u024d\u025d\u0265\u026a\u0273\u0276\u0287\u028b\u028d\u0295\u029c\u02a8"+
		"\u02aa\u02b1\u02b3\u02bb\u02bd\u02c4\u02c6\u02c9\u02d0\u02d9\u02e5\u02e9"+
		"\u02f7\u0301\u030c\u0311\u0315\u031d\u0327\u032e\u0332\u033d.\u0001\u0000"+
		"\u0000\u0000\u0001\u0000\u0001\u0002\u0001\u0001\u0003\u0002\u0001\u0004"+
		"\u0003\u0001\u0005\u0004\u0001\u0006\u0005\u0001\u0007\u0006\u0001\b\u0007"+
		"\u0001\t\b\u0001\n\t\u0001\u000b\n\u0001\f\u000b\u0001\r\f\u0001\u000e"+
		"\r\u0001\u000f\u000e\u0001\u0010\u000f\u0001\u0011\u0010\u0001\u0012\u0011"+
		"\u0001\u0013\u0012\u0001\u0014\u0013\u0001\u0015\u0014\u0001\u0016\u0015"+
		"\u0005\u0001\u0000\u0001\u0019\u0016\u0007&\u0000\u0001\u001a\u0017\u0001"+
		"\u001b\u0018\u0001\u001c\u0019\u0001\u001d\u001a\u0001\u001e\u001b\u0001"+
		" \u001c\u0001#\u001d\u0001$\u001e\u0001*\u001f\u0001+ \u0001,!\u0001."+
		"\"\u0001/#\u00010$\u00013%\u0007(\u0000\u0002\u0002\u0000\u0001:&\u0007"+
		"\u0017\u0000\u0001=\'";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}