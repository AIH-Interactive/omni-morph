package org.figuramc.figura.molang.lexer;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Lexical analyzer for the Molang language.
 *
 * Converts character streams to token streams. This is a stream-based lexer,
 * meaning it will not consume the entire reader if next() isn't called repeatedly.
 *
 * Reference: YSM MolangLexer
 */
public interface MolangLexer extends Closeable {

    /**
     * Returns the cursor for this lexer, which tracks current line and column.
     */
    Cursor cursor();

    /**
     * Returns the last emitted token from calling {@link #next()}.
     *
     * @throws IllegalStateException If next() has not been called yet
     */
    Token current();

    /**
     * Reads the next token from the internal reader.
     *
     * @return The next token (never null, may be EOF or ERROR)
     * @throws IOException If reading fails
     */
    Token next() throws IOException;

    /**
     * Reads all tokens until EOF.
     */
    default List<Token> tokenizeAll() throws IOException {
        List<Token> tokens = new ArrayList<>();
        Token token;
        while ((token = next()).kind() != TokenKind.EOF) {
            tokens.add(token);
        }
        return tokens;
    }

    @Override
    void close() throws IOException;

    static MolangLexer lexer(Reader reader) throws IOException {
        return new MolangLexerImpl(reader);
    }

    static MolangLexer lexer(String string) throws IOException {
        return lexer(new StringReader(string));
    }

    static List<Token> tokenizeAll(Reader reader) throws IOException {
        try (MolangLexer lexer = lexer(reader)) {
            return lexer.tokenizeAll();
        }
    }

    static List<Token> tokenizeAll(String string) throws IOException {
        try (MolangLexer lexer = lexer(string)) {
            return lexer.tokenizeAll();
        }
    }
}
