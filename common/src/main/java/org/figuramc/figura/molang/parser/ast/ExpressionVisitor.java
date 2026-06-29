package org.figuramc.figura.molang.parser.ast;

/**
 * Visitor interface for Molang AST expression nodes.
 *
 * Each expression type has a default visit method that falls back
 * to the generic visit(Expression) method.
 *
 * Reference: YSM ExpressionVisitor
 */
public interface ExpressionVisitor<R> {
    R visit(Expression expression);

    default R visitFloat(FloatExpression expression) {
        return visit(expression);
    }

    default R visitString(StringExpression expression) {
        return visit(expression);
    }

    default R visitIdentifier(IdentifierExpression expression) {
        return visit(expression);
    }

    default R visitVariable(VariableExpression expression) {
        return visit(expression);
    }

    default R visitAssignableVariable(AssignableVariableExpression expression) {
        return visit(expression);
    }

    default R visitStruct(StructAccessExpression expression) {
        return visit(expression);
    }

    default R visitTernaryConditional(TernaryConditionalExpression expression) {
        return visit(expression);
    }

    default R visitUnary(UnaryExpression expression) {
        return visit(expression);
    }

    default R visitExecutionScope(ExecutionScopeExpression expression) {
        return visit(expression);
    }

    default R visitBinary(BinaryExpression expression) {
        return visit(expression);
    }

    default R visitCall(CallExpression expression) {
        return visit(expression);
    }

    default R visitStatement(StatementExpression expression) {
        return visit(expression);
    }

    default R visitBinaryOperation(BinaryOperationExpression expression) {
        return visit(expression);
    }
}
