package com.docarchitect.parser;

import org.antlr.v4.runtime.*;

/**
 * Base class for Go lexer.
 */
public abstract class GoLexerBase extends Lexer {
    
    protected GoLexerBase(CharStream input) {
        super(input);
    }
}
