package org.figuramc.figura.molang.lexer;

import java.util.Objects;

/**
 * Class representing a Molang token. Each token has information
 * set by the lexer: start/end position, token kind and optional value.
 *
 * Reference: YSM Token
 */
public final class Token {
    private final TokenKind kind;
    private final String value;
    private final int start;
    private final int end;

    public Token(
            TokenKind kind,
            String value,
            int start,
            int end
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.value = value;
        this.start = start;
        this.end = end;

        // Token kinds with HAS_VALUE must have a non-null value
        if (kind.hasTag(TokenKind.Tag.HAS_VALUE) && value == null) {
            throw new IllegalArgumentException("A token with kind "
                    + kind + " must have a non-null value");
        }
    }

    public TokenKind kind() {
        return kind;
    }

    public String value() {
        return value;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    @Override
    public String toString() {
        if (kind.hasTag(TokenKind.Tag.HAS_VALUE)) {
            return kind + "(" + value + ")";
        } else {
            return kind.toString();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        if (start != token.start) return false;
        if (end != token.end) return false;
        if (kind != token.kind) return false;
        return Objects.equals(value, token.value);
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + start;
        result = 31 * result + end;
        return result;
    }
}
