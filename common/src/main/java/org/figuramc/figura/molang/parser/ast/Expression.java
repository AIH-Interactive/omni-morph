package org.figuramc.figura.molang.parser.ast;

/**
 * Root interface for all Molang AST expression nodes.
 *
 * Every expression in Molang evaluates to a value (typically float).
 * Expressions implement the Visitor pattern for evaluation.
 *
 * Reference: YSM Expression
 */
public interface Expression {

    /**
     * Visits this expression with the given visitor.
     */
    <R> R visit(ExpressionVisitor<R> visitor);
}
