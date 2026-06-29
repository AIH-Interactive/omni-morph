package org.figuramc.figura.molang.parser.ast;

import org.figuramc.figura.molang.storage.StringPool;

import java.util.Objects;

/**
 * AST node for a string literal value.
 * Uses StringPool for efficient string interning.
 *
 * Reference: YSM StringExpression
 */
public final class StringExpression implements Expression {

    private final String name;
    private final int path;

    public StringExpression(String str) {
        this.name = Objects.requireNonNull(str, "value");
        this.path = StringPool.computeIfAbsent(str);
    }

    public String getName() {
        return this.name;
    }

    public int getPath() {
        return this.path;
    }

    @Override
    public <R> R visit(ExpressionVisitor<R> expressionVisitor) {
        return expressionVisitor.visitString(this);
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (obj instanceof String) {
            return this.name.equals(obj);
        }
        return (obj instanceof StringExpression) && this.path == ((StringExpression) obj).path;
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }
}
