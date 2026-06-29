package org.figuramc.figura.molang.lexer;

import java.util.Objects;

/**
 * Mutable class that tracks the position of characters
 * when performing lexical analysis.
 *
 * Can be used to show the position of lexical errors
 * in a human-readable way.
 *
 * Reference: YSM Cursor
 */
public final class Cursor implements Cloneable {

    private int index = 0;
    private int line = 0;
    private int column = 0;

    public Cursor(final int line, final int column) {
        this.line = line;
        this.column = column;
    }

    public Cursor() {
    }

    public int index() {
        return this.index;
    }

    public int line() {
        return this.line;
    }

    public int column() {
        return this.column;
    }

    public void push(final int character) {
        index++;
        if (character == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
    }

    @Override
    public Cursor clone() {
        return new Cursor(line, column);
    }

    @Override
    public String toString() {
        return "line " + line + ", column " + column;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cursor that = (Cursor) o;
        return line == that.line && column == that.column;
    }

    @Override
    public int hashCode() {
        return Objects.hash(line, column);
    }
}
