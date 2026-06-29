package org.figuramc.figura.molang.parser.ast;

import org.figuramc.figura.molang.runtime.Function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * AST node for function call expressions.
 *
 * Examples: {@code math.sqrt(9)}, {@code math.pow(3, 2)}
 *
 * Reference: YSM CallExpression
 */
public final class CallExpression implements Expression {
    public static final Function.ArgumentCollection EMPTY =
            new Function.ArgumentCollection(Collections.emptyList());

    private final Function function;
    private final Function.ArgumentCollection arguments;

    public CallExpression(Function function) {
        this(function, EMPTY);
    }

    public CallExpression(Function function, Function.ArgumentCollection arguments) {
        this.function = Objects.requireNonNull(function, "function");
        this.arguments = Objects.requireNonNull(arguments, "arguments");
    }

    public Function function() {
        return function;
    }

    public Function.ArgumentCollection arguments() {
        return arguments;
    }

    @Override
    public <R> R visit(ExpressionVisitor<R> visitor) {
        return visitor.visitCall(this);
    }

    @Override
    public String toString() {
        return "Call(" + function + ", " + arguments + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallExpression that = (CallExpression) o;
        if (!function.equals(that.function)) return false;
        return arguments.equals(that.arguments);
    }

    @Override
    public int hashCode() {
        int result = function.hashCode();
        result = 31 * result + arguments.hashCode();
        return result;
    }
}
