package org.figuramc.figura.molang.parser;

import org.figuramc.figura.molang.lexer.MolangLexer;
import org.figuramc.figura.molang.lexer.Token;
import org.figuramc.figura.molang.lexer.TokenKind;
import org.figuramc.figura.molang.parser.ast.*;
import org.figuramc.figura.molang.runtime.Function;
import org.figuramc.figura.molang.runtime.binding.ObjectBinding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pratt parser implementation for Molang.
 *
 * Uses recursive descent with operator precedence parsing (Pratt parsing)
 * to handle all Molang operators with correct associativity.
 *
 * Reference: YSM MolangParserImpl
 */
public final class MolangParserImpl implements MolangParser {

    private static final int PRECEDENCE_QUES = 1400;
    private static final Object UNSET_FLAG = new Object();

    private final MolangLexer lexer;
    private final ObjectBinding binding;

    private Object current = UNSET_FLAG;

    MolangParserImpl(MolangLexer lexer, ObjectBinding binding) {
        this.lexer = lexer;
        this.binding = binding;
    }

    /**
     * Parses a single (atomic) expression - no operators.
     */
    Expression parseSingle(MolangLexer lexer) throws IOException {
        Token token = lexer.current();
        switch (token.kind()) {
            case FLOAT:
                lexer.next();
                return new FloatExpression(Float.parseFloat(token.value()));
            case STRING:
                lexer.next();
                return new StringExpression(token.value());
            case TRUE:
                lexer.next();
                return FloatExpression.ONE;
            case FALSE:
                lexer.next();
                return FloatExpression.ZERO;
            case LPAREN:
                lexer.next();
                Expression expression = parseCompoundExpression(lexer, 0);
                token = lexer.current();
                if (token.kind() != TokenKind.RPAREN) {
                    throw new ParseException("Non closed expression", lexer.cursor());
                }
                lexer.next();
                return expression;
            case LBRACE: {
                lexer.next();
                token = lexer.current();
                List<Expression> expressions = new ArrayList<>();
                while (token.kind() != TokenKind.RBRACE) {
                    expressions.add(parseCompoundExpression(lexer, 0));
                    Token current = lexer.current();
                    if (current.kind() == TokenKind.RBRACE) {
                        lexer.next();
                        return new ExecutionScopeExpression(expressions);
                    }
                    if (current.kind() == TokenKind.EOF) {
                        throw new ParseException("Found the end before the execution scope closing token", lexer.cursor());
                    }
                    if (current.kind() == TokenKind.ERROR) {
                        throw new ParseException("Found an invalid token (error): " + current.value(), lexer.cursor());
                    }
                    if (current.kind() != TokenKind.SEMICOLON) {
                        throw new ParseException("Missing semicolon", lexer.cursor());
                    }
                    token = lexer.next();
                }
                lexer.next();
                return new ExecutionScopeExpression(expressions);
            }
            case BREAK:
                lexer.next();
                return new StatementExpression(StatementExpression.Op.BREAK);
            case CONTINUE:
                lexer.next();
                return new StatementExpression(StatementExpression.Op.CONTINUE);
            case IDENTIFIER: {
                Object lastTarget = binding.getProperty(token.value());
                if (lastTarget == null) {
                    throw new ParseException("Failed to get property: " + token.value(), lexer.cursor());
                }
                Expression expr = IdentifierExpression.get(token.value(), lastTarget);
                token = lexer.next();
                if (token.kind() == TokenKind.DOT) {
                    token = lexer.next();
                    if (token.kind() != TokenKind.IDENTIFIER) {
                        throw new ParseException("Unexpected token, expected a valid field token", lexer.cursor());
                    }
                    if (lastTarget instanceof ObjectBinding) {
                        lastTarget = ((ObjectBinding) lastTarget).getProperty(token.value());
                    } else {
                        throw new ParseException("Illegal access to: " + token.value(), lexer.cursor());
                    }
                    if (lastTarget == null) {
                        throw new ParseException("Failed to get property: " + token.value(), lexer.cursor());
                    }
                    expr = IdentifierExpression.get(token.value(), lastTarget);
                    lexer.next();
                }
                return expr;
            }
            case PLUS:
                lexer.next();
                return parseCompoundExpression(lexer, UnaryExpression.Op.PLUS.precedence());
            case SUB:
                lexer.next();
                return new UnaryExpression(UnaryExpression.Op.ARITHMETICAL_NEGATION,
                        parseCompoundExpression(lexer, UnaryExpression.Op.ARITHMETICAL_NEGATION.precedence()));
            case BANG:
                lexer.next();
                return new UnaryExpression(UnaryExpression.Op.LOGICAL_NEGATION,
                        parseCompoundExpression(lexer, UnaryExpression.Op.LOGICAL_NEGATION.precedence()));
            case RETURN:
                lexer.next();
                return new UnaryExpression(UnaryExpression.Op.RETURN,
                        parseCompoundExpression(lexer, UnaryExpression.Op.RETURN.precedence()));
            default:
                throw new ParseException("Expected an expression.", lexer.cursor());
        }
    }

    /**
     * Parses a compound expression with Pratt operator precedence.
     */
    Expression parseCompoundExpression(MolangLexer lexer, int lastPrecedence) throws IOException {
        Expression expr = parseSingle(lexer);
        while (true) {
            final Expression compoundExpr = parseCompound(lexer, expr, lastPrecedence);
            final Token current = lexer.current();
            if (current.kind() == TokenKind.EOF || current.kind() == TokenKind.SEMICOLON) {
                return compoundExpr;
            } else if (compoundExpr == expr) {
                return expr;
            }
            expr = compoundExpr;
        }
    }

    Expression parseCompound(MolangLexer lexer, Expression left, int lastPrecedence) throws IOException {
        Token current = lexer.current();

        // Handle function call arguments: if left is a CallExpression with no args, parse (args)
        if (left instanceof CallExpression callExpression) {
            if (current.kind() == TokenKind.LPAREN) {
                if (callExpression.arguments() != CallExpression.EMPTY) {
                    throw new ParseException("Multiple '()' after function name", lexer.cursor());
                }
                lexer.next();
                final List<Expression> arguments = new ArrayList<>();
                current = lexer.current();
                if (current.kind() != TokenKind.RPAREN) {
                    while (true) {
                        arguments.add(parseCompoundExpression(lexer, 0));
                        current = lexer.current();
                        if (current.kind() == TokenKind.EOF) {
                            throw new ParseException("Found EOF before closing RPAREN", null);
                        } else if (current.kind() == TokenKind.RPAREN) {
                            lexer.next();
                            break;
                        } else {
                            if (current.kind() != TokenKind.COMMA) {
                                throw new ParseException("Expected a comma", lexer.cursor());
                            }
                            lexer.next();
                        }
                    }
                } else {
                    lexer.next();
                }
                if (!callExpression.function().validateArgumentSize(arguments.size())) {
                    throw new ParseException("Illegal function arguments size", lexer.cursor());
                }
                return new CallExpression(callExpression.function(), new Function.ArgumentCollection(arguments));
            }
            if (!callExpression.function().validateArgumentSize(callExpression.arguments().size())) {
                throw new ParseException("Illegal function arguments size", lexer.cursor());
            }
        }

        switch (current.kind()) {
            case RPAREN:
            case EOF:
                return left;

            case LPAREN: {
                if (lastPrecedence >= BinaryExpression.Op.MUL.precedence()) {
                    return left;
                }
                Expression right = parseCompoundExpression(lexer, BinaryExpression.Op.MUL.precedence());
                return new BinaryExpression(BinaryExpression.Op.MUL, left, right);
            }

            case QUES: {
                if (lastPrecedence > PRECEDENCE_QUES) {
                    return left;
                }
                lexer.next();
                final Expression trueValue = parseCompoundExpression(lexer, PRECEDENCE_QUES);
                if (lexer.current().kind() == TokenKind.COLON) {
                    lexer.next();
                    return new TernaryConditionalExpression(left, trueValue,
                            parseCompoundExpression(lexer, PRECEDENCE_QUES));
                } else {
                    return new BinaryExpression(BinaryExpression.Op.CONDITIONAL, left, trueValue);
                }
            }

            case LBRACKET: {
                lexer.next();
                final Expression indexExpression = parseCompoundExpression(lexer, 0);
                if (lexer.current().kind() == TokenKind.RBRACKET) {
                    lexer.next();
                    return new BinaryOperationExpression(left, indexExpression);
                }
                throw new ParseException("Expect a ']' after array index", lexer.cursor());
            }

            case DOT: {
                current = lexer.next();
                if (current.kind() == TokenKind.IDENTIFIER) {
                    lexer.next();
                    return new StructAccessExpression(left, current.value());
                } else {
                    throw new ParseException("Expect an identifier after struct access operator", lexer.cursor());
                }
            }

            default: {
                // Check for binary expression operators
                final BinaryExpression.Op op;
                switch (current.kind()) {
                    case AMPAMP:    op = BinaryExpression.Op.AND; break;
                    case BARBAR:    op = BinaryExpression.Op.OR; break;
                    case LT:        op = BinaryExpression.Op.LT; break;
                    case LTE:       op = BinaryExpression.Op.LTE; break;
                    case GT:        op = BinaryExpression.Op.GT; break;
                    case GTE:       op = BinaryExpression.Op.GTE; break;
                    case PLUS:      op = BinaryExpression.Op.ADD; break;
                    case SUB:       op = BinaryExpression.Op.SUB; break;
                    case STAR:      op = BinaryExpression.Op.MUL; break;
                    case SLASH:     op = BinaryExpression.Op.DIV; break;
                    case QUESQUES:  op = BinaryExpression.Op.NULL_COALESCE; break;
                    case EQ:        op = BinaryExpression.Op.ASSIGN; break;
                    case EQEQ:      op = BinaryExpression.Op.EQ; break;
                    case BANGEQ:    op = BinaryExpression.Op.NEQ; break;
                    case ARROW:     op = BinaryExpression.Op.ARROW; break;
                    default:        return left;
                }

                final int precedence = op.precedence();
                if (lastPrecedence >= precedence) {
                    return left;
                }
                lexer.next();
                return new BinaryExpression(op, left, parseCompoundExpression(lexer, precedence));
            }
        }
    }

    @Override
    public MolangLexer lexer() {
        return lexer;
    }

    @Override
    public Expression current() {
        if (current == UNSET_FLAG) {
            throw new IllegalStateException("No current parsed expression, call next() at least once!");
        }
        return (Expression) current;
    }

    @Override
    public Expression next() throws IOException {
        final Expression expr = next0();
        current = expr;
        return expr;
    }

    private Expression next0() throws IOException {
        Token token = lexer.next();
        if (token.kind() == TokenKind.EOF) {
            return null;
        }
        if (token.kind() == TokenKind.ERROR) {
            throw new ParseException("Found an invalid token (error): " + token.value(), cursor());
        }
        final Expression expression = parseCompoundExpression(lexer, -10);

        token = lexer.current();
        if (token.kind() != TokenKind.EOF && token.kind() != TokenKind.SEMICOLON) {
            throw new ParseException("Expected a semicolon, but was " + token, lexer.cursor());
        }
        return expression;
    }

    @Override
    public void close() throws IOException {
        this.lexer.close();
    }
}
