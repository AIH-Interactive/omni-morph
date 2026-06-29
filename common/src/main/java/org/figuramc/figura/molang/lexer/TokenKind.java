package org.figuramc.figura.molang.lexer;

import java.util.*;

/**
 * Enum of Molang token kinds. Each token kind represents a specific
 * sequence of one or more continuous characters.
 *
 * Reference: YSM TokenKind
 */
public enum TokenKind {

    /** End-of-file token */
    EOF,

    /** Error token, has a string value describing the error */
    ERROR(Tag.HAS_VALUE),

    /** Identifier token, has a string value of the identifier name */
    IDENTIFIER(Tag.HAS_VALUE),

    /** String literal token, has a string value of its content */
    STRING(Tag.HAS_VALUE),

    /** Float literal token, has a string value parsable to a floating-point number */
    FLOAT(Tag.HAS_VALUE),

    /** 'True' literal boolean token */
    TRUE,

    /** 'False' literal boolean token */
    FALSE,

    /** The "break" keyword */
    BREAK,

    /** The "continue" keyword */
    CONTINUE,

    /** The "return" keyword */
    RETURN,

    /** The dot symbol (.) */
    DOT,

    /** The bang or exclamation symbol (!) */
    BANG,

    /** Double ampersand token (&&) */
    AMPAMP,

    /** Double bar token (||) */
    BARBAR,

    /** Less-than token (<) */
    LT,

    /** Less-than-or-equal token (<=) */
    LTE,

    /** Greater-than token (>) */
    GT,

    /** Greater-than-or-equal token (>=) */
    GTE,

    /** Equal symbol (=) */
    EQ,

    /** Equal-equal token (==) */
    EQEQ,

    /** Bang-eq token (!=) */
    BANGEQ,

    /** Star symbol (*) */
    STAR,

    /** Slash symbol (/) */
    SLASH,

    /** Plus symbol (+) */
    PLUS,

    /** Hyphen/sub symbol (-) */
    SUB,

    /** Left-parenthesis symbol "(" */
    LPAREN,

    /** Right-parenthesis symbol ")" */
    RPAREN,

    /** Left-brace symbol "{" */
    LBRACE,

    /** Right-brace symbol "}" */
    RBRACE,

    /** Question-question token (??) */
    QUESQUES,

    /** Question symbol (?) */
    QUES,

    /** Colon symbol (:) */
    COLON,

    /** Arrow token (->) */
    ARROW,

    /** Left-bracket token "[" */
    LBRACKET,

    /** Right-bracket "]" */
    RBRACKET,

    /** Comma symbol (,) */
    COMMA,

    /** Semicolon symbol (;) */
    SEMICOLON;

    private final Set<Tag> tags;

    TokenKind(Tag... tags) {
        this.tags = tags.length > 0 ? EnumSet.copyOf(Arrays.asList(tags)) : Collections.emptySet();
    }

    /**
     * Determines if this token kind has a certain tag.
     */
    public boolean hasTag(Tag tag) {
        Objects.requireNonNull(tag, "tag");
        return this.tags.contains(tag);
    }

    /**
     * Enum of tags for token kinds. Tags specify certain features of token kinds.
     */
    public enum Tag {
        /**
         * Token kinds with HAS_VALUE tag will have a variable value,
         * for example float or string literal tokens have variable values.
         */
        HAS_VALUE
    }
}
