package org.figuramc.figura.molang.parser;

import org.figuramc.figura.molang.lexer.Cursor;
import org.figuramc.figura.molang.lexer.MolangLexer;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.binding.ObjectBinding;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for the Molang language.
 *
 * Converts a token stream to an AST expression stream using
 * Pratt parsing (recursive descent with operator precedence).
 *
 * Reference: YSM MolangParser
 */
public interface MolangParser extends Closeable {

    /**
     * Returns the internal lexer.
     */
    MolangLexer lexer();

    /**
     * Returns the cursor for error reporting.
     */
    default Cursor cursor() {
        return lexer().cursor();
    }

    /**
     * Returns the last parsed expression.
     *
     * @throws IllegalStateException If next() has not been called yet
     */
    Expression current();

    /**
     * Parses the next expression.
     *
     * @return The parsed expression, or null if EOF
     * @throws IOException If reading or parsing fails
     */
    Expression next() throws IOException;

    /**
     * Parses all expressions until EOF.
     */
    default List<Expression> parseAll() throws IOException {
        List<Expression> tokens = new ArrayList<>();
        Expression expr;
        while ((expr = next()) != null) {
            tokens.add(expr);
        }
        return tokens;
    }

    @Override
    void close() throws IOException;

    static MolangParser parser(MolangLexer molangLexer, ObjectBinding objectBinding) throws IOException {
        return new MolangParserImpl(molangLexer, objectBinding);
    }

    static MolangParser parser(Reader reader, ObjectBinding objectBinding) throws IOException {
        return parser(MolangLexer.lexer(reader), objectBinding);
    }

    static MolangParser parser(String str, ObjectBinding objectBinding) throws IOException {
        return parser(MolangLexer.lexer(str), objectBinding);
    }

    static List<Expression> parseExpressions(Reader reader, ObjectBinding objectBinding) throws IOException {
        MolangParser molangParser = parser(reader, objectBinding);
        try {
            return molangParser.parseAll();
        } finally {
            molangParser.close();
        }
    }

    static List<Expression> parseExpressions(String str, ObjectBinding objectBinding) throws IOException {
        MolangParser molangParser = parser(str, objectBinding);
        try {
            return molangParser.parseAll();
        } finally {
            molangParser.close();
        }
    }

    /**
     * Strips comments from the input string before parsing.
     * Handles {@code //} single-line and {@code /* ... * /} multi-line comments.
     */
    static List<Expression> stripAndParse(String str, ObjectBinding objectBinding) throws IOException {
        return parseExpressions(CommentStripper.stripComments(str), objectBinding);
    }

    /**
     * Safely parses expressions, returning null on failure instead of throwing.
     */
    static List<Expression> parseSafe(String str, ObjectBinding objectBinding) {
        try {
            return parseExpressions(str, objectBinding);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Safely strips comments and parses, returning null on failure.
     */
    static List<Expression> stripAndParseSafe(String str, ObjectBinding objectBinding) {
        try {
            return stripAndParse(str, objectBinding);
        } catch (Exception e) {
            return null;
        }
    }
}
