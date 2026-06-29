package org.figuramc.figura.molang;

import org.figuramc.figura.molang.lexer.Cursor;
import org.figuramc.figura.molang.parser.ParseException;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.binding.ObjectBinding;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

/**
 * Entry point for the Figura Molang engine.
 *
 * Provides methods to parse and evaluate Molang expressions.
 * Uses the YSM-inspired Lexer → Pratt Parser → Visitor Evaluator pipeline.
 *
 * Usage:
 * <pre>{@code
 *   MolangBindings bindings = new MolangBindings();
 *   bindings.registerQuery("anim_time", ...);
 *
 *   MolangEngine engine = MolangEngine.fromCustomBinding(bindings);
 *   List<Expression> ast = engine.parse("Math.sin(query.anim_time * 20)");
 *
 *   ExpressionEvaluator<Avatar> evaluator = ExpressionEvaluator.evaluator(avatar);
 *   float result = evaluator.evalAsFloat(ast.get(0));
 * }</pre>
 */
public interface MolangEngine {

    /**
     * Parses Molang code from a reader into a list of AST expressions.
     *
     * @param reader The source reader (will NOT be closed by this method)
     * @return The parsed expressions
     * @throws IOException If reading fails or there are syntax errors
     */
    List<Expression> parse(Reader reader) throws IOException;

    /**
     * Parses a Molang string into a list of AST expressions.
     *
     * @param str The Molang expression string
     * @return The parsed expressions
     * @throws ParseException If parsing fails
     */
    default List<Expression> parse(String str) throws ParseException {
        try {
            StringReader stringReader = new StringReader(str);
            List<Expression> expressions = parse(stringReader);
            stringReader.close();
            return expressions;
        } catch (ParseException e) {
            throw e;
        } catch (IOException e) {
            throw new ParseException("Failed to close string reader", e, new Cursor(0, 0));
        }
    }

    /**
     * Creates a MolangEngine with custom bindings.
     *
     * @param objectBinding The root ObjectBinding for name resolution
     * @return A new MolangEngine instance
     */
    static MolangEngine fromCustomBinding(ObjectBinding objectBinding) {
        return new MolangEngineImpl(objectBinding);
    }

    /**
     * Creates a MolangEngine with empty bindings.
     * Only literal expressions will parse successfully.
     *
     * @return A new MolangEngine instance
     */
    static MolangEngine createEmpty() {
        return new MolangEngineImpl(ObjectBinding.EMPTY);
    }
}
