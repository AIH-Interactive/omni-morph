package org.figuramc.figura.molang.lexer;

import java.io.IOException;
import java.io.Reader;

/**
 * Stream-based Molang lexer implementation.
 *
 * Converts a character stream into a token stream, handling:
 * - Float literals (including those starting with '.')
 * - Identifiers and keywords (case-insensitive)
 * - String literals (single-quoted)
 * - All Molang operators and punctuation
 *
 * Reference: YSM MolangLexerImpl
 */
public final class MolangLexerImpl implements MolangLexer {

    private final Reader reader;
    private final Cursor cursor = new Cursor();
    private int next;
    private Token lastToken = null;
    private Token token = null;

    public MolangLexerImpl(Reader reader) throws IOException {
        this.reader = reader;
        this.next = reader.read();
    }

    @Override
    public Cursor cursor() {
        return cursor;
    }

    @Override
    public Token current() {
        if (token == null) {
            throw new IllegalStateException("No current token, please call next() at least once");
        }
        return token;
    }

    @Override
    public Token next() throws IOException {
        lastToken = token;
        return token = next0();
    }

    @Override
    public void close() throws IOException {
        this.reader.close();
    }

    private Token next0() throws IOException {
        int c = next;
        if (c == -1) {
            return new Token(TokenKind.EOF, null, cursor.index(), cursor.index() + 1);
        }

        // skip whitespace
        while (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
            c = read();
        }

        if (c == -1) {
            return new Token(TokenKind.EOF, null, cursor.index(), cursor.index() + 1);
        }

        int start = cursor.index();

        // Handle '.' after ')' as a struct access DOT (not as start of a float)
        if (c == '.' && lastToken != null && lastToken.kind() == TokenKind.RPAREN) {
            read();
            return new Token(TokenKind.DOT, null, start, cursor.index());
        }

        boolean isLastIdentifier = (lastToken != null && lastToken.kind() == TokenKind.IDENTIFIER);
        if (Characters.isDigit(c) || (!isLastIdentifier && c == '.')) {
            // Float literal
            StringBuilder builder = new StringBuilder(8);
            if (!isLastIdentifier) {
                builder.appendCodePoint(c);
                while (Characters.isDigit(c = read())) {
                    builder.appendCodePoint(c);
                }
            } else {
                builder.append('0');
            }

            if (c == '.') {
                builder.append('.');
                while (Characters.isDigit(c = read())) {
                    builder.appendCodePoint(c);
                }
            }

            return new Token(TokenKind.FLOAT, builder.toString(), start, cursor.index());
        } else if (Characters.isValidForWordStart(c)) {
            // Identifier or keyword
            StringBuilder builder = new StringBuilder();
            do {
                builder.appendCodePoint(c);
            } while (Characters.isValidForWordContinuation(c = read()));
            String word = builder.toString().toLowerCase();
            TokenKind kind;
            switch (word) {
                case "break":    kind = TokenKind.BREAK; break;
                case "continue": kind = TokenKind.CONTINUE; break;
                case "return":   kind = TokenKind.RETURN; break;
                case "true":     kind = TokenKind.TRUE; break;
                case "false":    kind = TokenKind.FALSE; break;
                default:         kind = TokenKind.IDENTIFIER; break;
            }

            return new Token(
                    kind,
                    kind == TokenKind.IDENTIFIER ? word : null,
                    start,
                    cursor.index()
            );
        } else if (c == '\'') {
            // String literal (single-quoted)
            StringBuilder value = new StringBuilder(16);
            while (true) {
                c = read();
                if (c == -1) {
                    return new Token(TokenKind.ERROR, "Found end-of-file before closing quote", start, cursor.index());
                } else if (c == '\'') {
                    break;
                } else {
                    value.appendCodePoint(c);
                }
            }
            read(); // consume closing quote
            return new Token(TokenKind.STRING, value.toString(), start, cursor.index());
        } else {
            // Operator or punctuation
            TokenKind tokenKind;
            String value = null;
            int c1 = -2;

            switch (c) {
                case '!': {
                    c1 = read();
                    if (c1 == '=') { read(); tokenKind = TokenKind.BANGEQ; }
                    else { tokenKind = TokenKind.BANG; }
                    break;
                }
                case '&': {
                    c1 = read();
                    if (c1 == '&') { read(); tokenKind = TokenKind.AMPAMP; }
                    else {
                        tokenKind = TokenKind.ERROR;
                        value = "Unexpected token '" + ((char) c1) + "', expected '&' (Molang doesn't support bitwise operators)";
                    }
                    break;
                }
                case '|': {
                    c1 = read();
                    if (c1 == '|') { read(); tokenKind = TokenKind.BARBAR; }
                    else {
                        tokenKind = TokenKind.ERROR;
                        value = "Unexpected token '" + ((char) c1) + "', expected '|' (Molang doesn't support bitwise operators)";
                    }
                    break;
                }
                case '<': {
                    c1 = read();
                    if (c1 == '=') { read(); tokenKind = TokenKind.LTE; }
                    else { tokenKind = TokenKind.LT; }
                    break;
                }
                case '>': {
                    c1 = read();
                    if (c1 == '=') { read(); tokenKind = TokenKind.GTE; }
                    else { tokenKind = TokenKind.GT; }
                    break;
                }
                case '=': {
                    c1 = read();
                    if (c1 == '=') { read(); tokenKind = TokenKind.EQEQ; }
                    else { tokenKind = TokenKind.EQ; }
                    break;
                }
                case '-': {
                    c1 = read();
                    if (c1 == '>') { read(); tokenKind = TokenKind.ARROW; }
                    else { tokenKind = TokenKind.SUB; }
                    break;
                }
                case '?': {
                    c1 = read();
                    if (c1 == '?') { read(); tokenKind = TokenKind.QUESQUES; }
                    else { tokenKind = TokenKind.QUES; }
                    break;
                }
                case '/':  tokenKind = TokenKind.SLASH; break;
                case '*':  tokenKind = TokenKind.STAR; break;
                case '+':  tokenKind = TokenKind.PLUS; break;
                case ',':  tokenKind = TokenKind.COMMA; break;
                case '.':  tokenKind = TokenKind.DOT; break;
                case '(':  tokenKind = TokenKind.LPAREN; break;
                case ')':  tokenKind = TokenKind.RPAREN; break;
                case '{':  tokenKind = TokenKind.LBRACE; break;
                case '}':  tokenKind = TokenKind.RBRACE; break;
                case ':':  tokenKind = TokenKind.COLON; break;
                case '[':  tokenKind = TokenKind.LBRACKET; break;
                case ']':  tokenKind = TokenKind.RBRACKET; break;
                case ';':  tokenKind = TokenKind.SEMICOLON; break;
                default: {
                    tokenKind = TokenKind.ERROR;
                    value = "Unexpected token '" + ((char) c) + "': invalid token";
                    break;
                }
            }

            if (c1 == -2) {
                read();
            }

            return new Token(tokenKind, value, start, cursor.index());
        }
    }

    private int read() throws IOException {
        int c = reader.read();
        cursor.push(c);
        next = c;
        return c;
    }
}
