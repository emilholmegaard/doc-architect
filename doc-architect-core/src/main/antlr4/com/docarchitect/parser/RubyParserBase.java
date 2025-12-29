package com.docarchitect.parser;

import org.antlr.v4.runtime.*;

/**
 * Base class for Ruby parser with helper methods for semantic analysis.
 *
 * <p>This base class can be extended with custom methods to support
 * Ruby-specific parsing logic and validation.
 */
public abstract class RubyParserBase extends Parser {

    protected RubyParserBase(TokenStream input) {
        super(input);
    }

    /**
     * Helper method to check if the previous token matches the given text.
     * Useful for context-sensitive parsing.
     */
    protected boolean prevTokenIs(String text) {
        if (_input.index() <= 0) {
            return false;
        }
        Token prevToken = _input.LT(-1);
        return prevToken != null && prevToken.getText().equals(text);
    }

    /**
     * Helper method to check if the next token matches the given text.
     * Useful for lookahead parsing decisions.
     */
    protected boolean nextTokenIs(String text) {
        Token nextToken = _input.LT(1);
        return nextToken != null && nextToken.getText().equals(text);
    }
}
