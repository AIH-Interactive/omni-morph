package org.figuramc.figura.molang.parser.ast;

import org.figuramc.figura.molang.runtime.AssignableVariable;
import org.figuramc.figura.molang.runtime.Function;
import org.figuramc.figura.molang.runtime.Variable;

import java.util.Objects;

/**
 * AST node for a named identifier reference.
 * The parser resolves identifiers to their runtime targets
 * (numbers, strings, functions, variables) at parse time.
 *
 * Reference: YSM IdentifierExpression
 */
public final class IdentifierExpression implements Expression {

    private final String name;
    private final Object target;

    private IdentifierExpression(String name, Object target) {
        Objects.requireNonNull(name, "name");
        this.name = name.toLowerCase(); // case-insensitive
        this.target = target;
    }

    /**
     * Factory method: creates the appropriate expression type based on the target object.
     */
    public static Expression get(String str, Object obj) {
        if (obj instanceof Number) {
            return new FloatExpression(((Number) obj).floatValue());
        }
        if (obj instanceof String) {
            return new StringExpression((String) obj);
        }
        if (obj instanceof Function) {
            return new CallExpression((Function) obj);
        }
        if (obj instanceof AssignableVariable) {
            return new AssignableVariableExpression((AssignableVariable) obj);
        }
        if (obj instanceof Variable) {
            return new VariableExpression((Variable) obj);
        }
        return new IdentifierExpression(str, obj);
    }

    public String name() {
        return name;
    }

    public Object target() {
        return target;
    }

    @Override
    public <R> R visit(ExpressionVisitor<R> visitor) {
        return visitor.visitIdentifier(this);
    }

    @Override
    public String toString() {
        return "Identifier(" + name + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentifierExpression that = (IdentifierExpression) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
