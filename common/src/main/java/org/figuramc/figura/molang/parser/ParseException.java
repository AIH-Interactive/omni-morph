package org.figuramc.figura.molang.parser;

import org.figuramc.figura.molang.lexer.Cursor;

import java.io.IOException;

/**
 * Exception thrown during the Molang parsing phase.
 *
 * Reference: YSM ParseException
 */
public class ParseException extends IOException {

    private final Cursor cursor;

    public ParseException(Cursor cursor) {
        this.cursor = cursor;
    }

    public ParseException(String str, Cursor cursor) {
        super(appendCursor(str, cursor));
        this.cursor = cursor;
    }

    public ParseException(Throwable th, Cursor cursor) {
        super(th);
        this.cursor = cursor;
    }

    public ParseException(String str, Throwable th, Cursor cursor) {
        super(appendCursor(str, cursor), th);
        this.cursor = cursor;
    }

    public Cursor cursor() {
        return this.cursor;
    }

    private static String appendCursor(String message, Cursor cursor) {
        if (cursor == null) return message;
        return message + "\n  at " + cursor;
    }
}
