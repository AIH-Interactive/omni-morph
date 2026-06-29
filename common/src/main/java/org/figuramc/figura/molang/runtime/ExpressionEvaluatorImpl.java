package org.figuramc.figura.molang.runtime;

import org.figuramc.figura.molang.parser.ast.*;
import org.figuramc.figura.molang.runtime.binding.ValueConversions;

import java.util.Iterator;
import java.util.List;

/**
 * Visitor-based Molang expression evaluator.
 *
 * Features:
 * - Native float evaluation path (zero boxing for arithmetic expressions)
 * - Boolean evaluation path
 * - Division by zero returns 0 (Molang spec)
 * - Short-circuit evaluation for &&, ||, and ternary
 *
 * Reference: YSM ExpressionEvaluatorImpl
 */
public final class ExpressionEvaluatorImpl<TEntity>
        implements ExpressionEvaluator<TEntity>, ExpressionVisitor<Object> {

    private static final Double DOUBLE_ZERO = 0.0d;
    private static final Float FLOAT_ZERO = 0.0f;

    // Binary evaluator table indexed by BinaryExpression.Op.index()
    private static final Evaluator[] BINARY_EVALUATORS = {
            // AND (0)
            (evaluator, a, b) -> {
                if (!ValueConversions.asBoolean(a.visit(evaluator))) return Boolean.FALSE;
                return ValueConversions.asBoolean(b.visit(evaluator)) ? Boolean.TRUE : Boolean.FALSE;
            },
            // OR (1)
            (evaluator, a, b) -> {
                if (ValueConversions.asBoolean(a.visit(evaluator))) return Boolean.TRUE;
                return ValueConversions.asBoolean(b.visit(evaluator)) ? Boolean.TRUE : Boolean.FALSE;
            },
            // LT (2)
            (evaluator, a, b) -> {
                return ValueConversions.asFloat(a.visit(evaluator)) < ValueConversions.asFloat(b.visit(evaluator))
                        ? Boolean.TRUE : Boolean.FALSE;
            },
            // LTE (3)
            (evaluator, a, b) -> {
                return ValueConversions.asFloat(a.visit(evaluator)) <= ValueConversions.asFloat(b.visit(evaluator))
                        ? Boolean.TRUE : Boolean.FALSE;
            },
            // GT (4)
            (evaluator, a, b) -> {
                return ValueConversions.asFloat(a.visit(evaluator)) > ValueConversions.asFloat(b.visit(evaluator))
                        ? Boolean.TRUE : Boolean.FALSE;
            },
            // GTE (5)
            (evaluator, a, b) -> {
                return ValueConversions.asFloat(a.visit(evaluator)) >= ValueConversions.asFloat(b.visit(evaluator))
                        ? Boolean.TRUE : Boolean.FALSE;
            },
            // ADD (6)
            (evaluator, a, b) -> ValueConversions.asFloat(a.visit(evaluator)) + ValueConversions.asFloat(b.visit(evaluator)),
            // SUB (7)
            (evaluator, a, b) -> ValueConversions.asFloat(a.visit(evaluator)) - ValueConversions.asFloat(b.visit(evaluator)),
            // MUL (8)
            (evaluator, a, b) -> ValueConversions.asFloat(a.visit(evaluator)) * ValueConversions.asFloat(b.visit(evaluator)),
            // DIV (9) - Molang: division by zero returns 0
            (evaluator, a, b) -> {
                float divisor = ValueConversions.asFloat(b.visit(evaluator));
                if (divisor == 0.0f) return FLOAT_ZERO;
                return ValueConversions.asFloat(a.visit(evaluator)) / divisor;
            },
            // ARROW (10) - context switch
            (evaluator, a, b) -> {
                Object val = a.visit(evaluator);
                if (val == null) return null;
                ExpressionEvaluatorImpl child = evaluator.createChild(val);
                Object res = b.visit(child);
                evaluator.returnValue = child.returnValue;
                return res;
            },
            // NULL_COALESCE (11) - a ?? b
            (evaluator, a, b) -> {
                Object val = a.visit(evaluator);
                return val != null ? val : b.visit(evaluator);
            },
            // ASSIGN (12) - a = b
            (evaluator, a, b) -> {
                Object val = b.visit(evaluator);
                if (a instanceof AssignableVariableExpression) {
                    AssignableVariable var = ((AssignableVariableExpression) a).target();
                    if (val instanceof Struct) {
                        val = ((Struct) val).copy();
                    }
                    var.assign(evaluator, val);
                } else if (a instanceof StructAccessExpression exp) {
                    if (val instanceof Struct) return val;
                    Object value = exp.left().visit(evaluator);
                    if (value instanceof Struct) {
                        ((Struct) value).putProperty(exp.path(), val);
                    } else if (exp.left() instanceof AssignableVariableExpression) {
                        AssignableVariable variable = ((AssignableVariableExpression) exp.left()).target();
                        Struct struct = new HashMapStruct();
                        struct.putProperty(exp.path(), val);
                        variable.assign(evaluator, struct);
                    }
                }
                return val;
            },
            // CONDITIONAL (13) - a ? b (without else)
            (evaluator, a, b) -> {
                Object condition = a.visit(evaluator);
                if (ValueConversions.asBoolean(condition)) {
                    return b.visit(evaluator);
                }
                return null;
            },
            // EQ (14) - a == b
            (evaluator, a, b) -> {
                Object left = a.visit(evaluator);
                Object right = b.visit(evaluator);
                if (left == right) return Boolean.TRUE;
                if (left instanceof Number || right instanceof Number)
                    return ValueConversions.asFloat(left) == ValueConversions.asFloat(right)
                            ? Boolean.TRUE : Boolean.FALSE;
                if (left == null || right == null) return Boolean.FALSE;
                return left.equals(right) ? Boolean.TRUE : Boolean.FALSE;
            },
            // NEQ (15) - a != b
            (evaluator, a, b) -> {
                Object left = a.visit(evaluator);
                Object right = b.visit(evaluator);
                if (left == right) return Boolean.FALSE;
                if (left instanceof Number || right instanceof Number)
                    return ValueConversions.asFloat(left) != ValueConversions.asFloat(right)
                            ? Boolean.TRUE : Boolean.FALSE;
                if (left == null || right == null) return Boolean.TRUE;
                return !left.equals(right) ? Boolean.TRUE : Boolean.FALSE;
            }
    };

    private final TEntity entity;
    private Object returnValue;
    private StatementExpression.Op op;
    private int cnt = 0;
    private int working = 0;

    public ExpressionEvaluatorImpl(TEntity entity) {
        this.entity = entity;
    }

    @Override
    public TEntity entity() {
        return this.entity;
    }

    @Override
    public Object eval(Expression expression) {
        try {
            return expression.visit(this);
        } finally {
            this.returnValue = null;
            this.op = null;
        }
    }

    @Override
    public float evalAsFloat(Expression expression) {
        try {
            return evalFloat(expression);
        } finally {
            this.returnValue = null;
            this.op = null;
        }
    }

    @Override
    public boolean evalAsBoolean(Expression expression) {
        try {
            return evalBool(expression);
        } finally {
            this.returnValue = null;
            this.op = null;
        }
    }

    @Override
    public Object evalAll(Iterable<Expression> iterable, boolean z) {
        if (z) this.working++;
        Object result = DOUBLE_ZERO;
        try {
            if (iterable instanceof List) {
                List<Expression> list = (List<Expression>) iterable;
                for (int i = 0; i < list.size(); i++) {
                    result = list.get(i).visit(this);
                    Object obj = popReturnValue();
                    if (obj != null) {
                        result = obj;
                        break;
                    }
                }
            } else {
                for (Expression expression : iterable) {
                    result = expression.visit(this);
                    Object obj = popReturnValue();
                    if (obj != null) {
                        result = obj;
                        break;
                    }
                }
            }
            return result;
        } finally {
            this.returnValue = null;
            this.op = null;
            if (z) this.working--;
        }
    }

    /**
     * Native float evaluation path - avoids boxing for arithmetic subtrees.
     * Falls back to visit() for non-arithmetic nodes.
     */
    private float evalFloat(Expression expr) {
        if (expr instanceof FloatExpression fe) {
            return fe.value();
        }
        if (expr instanceof BinaryExpression be) {
            switch (be.op()) {
                case ADD: return evalFloat(be.left()) + evalFloat(be.right());
                case SUB: return evalFloat(be.left()) - evalFloat(be.right());
                case MUL: return evalFloat(be.left()) * evalFloat(be.right());
                case DIV: {
                    float d = evalFloat(be.right());
                    if (d == 0.0f) return 0.0f;
                    return evalFloat(be.left()) / d;
                }
                case LT:  return evalFloat(be.left()) <  evalFloat(be.right()) ? 1.0f : 0.0f;
                case LTE: return evalFloat(be.left()) <= evalFloat(be.right()) ? 1.0f : 0.0f;
                case GT:  return evalFloat(be.left()) >  evalFloat(be.right()) ? 1.0f : 0.0f;
                case GTE: return evalFloat(be.left()) >= evalFloat(be.right()) ? 1.0f : 0.0f;
                case AND: return (evalBool(be.left()) && evalBool(be.right())) ? 1.0f : 0.0f;
                case OR:  return (evalBool(be.left()) || evalBool(be.right())) ? 1.0f : 0.0f;
                default: break;
            }
        }
        if (expr instanceof UnaryExpression ue) {
            switch (ue.op()) {
                case ARITHMETICAL_NEGATION: return -evalFloat(ue.expression());
                case PLUS: return evalFloat(ue.expression());
                case LOGICAL_NEGATION: return evalBool(ue.expression()) ? 0.0f : 1.0f;
                case RETURN: return evalFloat(ue.expression());
                default: break;
            }
        }
        if (expr instanceof TernaryConditionalExpression te) {
            return evalBool(te.condition())
                    ? evalFloat(te.trueExpression())
                    : evalFloat(te.falseExpression());
        }
        return ValueConversions.asFloat(expr.visit(this));
    }

    /**
     * Native boolean evaluation path - avoids boxing.
     */
    private boolean evalBool(Expression expr) {
        if (expr instanceof FloatExpression fe) {
            return fe.value() != 0.0f;
        }
        if (expr instanceof BinaryExpression be) {
            switch (be.op()) {
                case AND: return evalBool(be.left()) && evalBool(be.right());
                case OR:  return evalBool(be.left()) || evalBool(be.right());
                case LT:  return evalFloat(be.left()) <  evalFloat(be.right());
                case LTE: return evalFloat(be.left()) <= evalFloat(be.right());
                case GT:  return evalFloat(be.left()) >  evalFloat(be.right());
                case GTE: return evalFloat(be.left()) >= evalFloat(be.right());
                case ADD: return (evalFloat(be.left()) + evalFloat(be.right())) != 0.0f;
                case SUB: return (evalFloat(be.left()) - evalFloat(be.right())) != 0.0f;
                case MUL: {
                    float l = evalFloat(be.left());
                    if (l == 0.0f) return false;
                    return evalFloat(be.right()) != 0.0f;
                }
                case DIV: {
                    float r = evalFloat(be.right());
                    if (r == 0.0f) return false;
                    return (evalFloat(be.left()) / r) != 0.0f;
                }
                default: break;
            }
        }
        if (expr instanceof UnaryExpression ue) {
            switch (ue.op()) {
                case LOGICAL_NEGATION: return !evalBool(ue.expression());
                case ARITHMETICAL_NEGATION: return evalBool(ue.expression());
                case PLUS: return evalBool(ue.expression());
                case RETURN: return evalBool(ue.expression());
                default: break;
            }
        }
        if (expr instanceof TernaryConditionalExpression te) {
            return evalBool(te.condition())
                    ? evalBool(te.trueExpression())
                    : evalBool(te.falseExpression());
        }
        return ValueConversions.asBoolean(expr.visit(this));
    }

    public <TNewEntity> ExpressionEvaluatorImpl<TNewEntity> createChild(TNewEntity entity) {
        return new ExpressionEvaluatorImpl<>(entity);
    }

    private Object popReturnValue() {
        Object obj = this.returnValue;
        if (this.working == 0) {
            this.returnValue = null;
        }
        return obj;
    }

    // === Visitor implementations ===

    @Override
    public Object visitCall(CallExpression expression) {
        return expression.function().evaluate(this, expression.arguments());
    }

    @Override
    public Object visitFloat(FloatExpression floatExpression) {
        return floatExpression.boxed();
    }

    @Override
    public Object visitExecutionScope(ExecutionScopeExpression executionScope) {
        Object result = null;
        final List<Expression> expressions = executionScope.expressions();
        for (int i = 0; i < expressions.size(); i++) {
            result = expressions.get(i).visit(this);
            Object obj = popReturnValue();
            if (obj != null) return obj;
            if (this.cnt > 0 && this.op != null) return null;
        }
        return result;
    }

    private boolean buildExecutionScope(ExecutionScopeExpression executionScope) {
        this.cnt++;
        try {
            final List<Expression> expressions = executionScope.expressions();
            for (int i = 0; i < expressions.size(); i++) {
                expressions.get(i).visit(this);
                if (popReturnValue() != null) return true;
                StatementExpression.Op op = this.op;
                this.op = null;
                if (op == StatementExpression.Op.CONTINUE) break;
                if (op == StatementExpression.Op.BREAK) {
                    this.cnt--;
                    return true;
                }
            }
            this.cnt--;
            return false;
        } finally {
            this.cnt--;
        }
    }

    public void loopFunction(ExecutionScopeExpression executionScope, int n) {
        for (int i = 0; i < n && !buildExecutionScope(executionScope); i++) {
        }
    }

    public void forEachFunction(ExecutionScopeExpression executionScope,
                                 AssignableVariable variableAccess,
                                 Iterable<?> iterable) {
        Iterator<?> it = iterable.iterator();
        while (it.hasNext()) {
            variableAccess.assign(this, it.next());
            if (buildExecutionScope(executionScope)) return;
        }
    }

    @Override
    public Object visitIdentifier(IdentifierExpression identifierExpression) {
        throw new RuntimeException("Unknown identifier type: " + identifierExpression.name());
    }

    @Override
    public Object visitVariable(VariableExpression expression) {
        return expression.target().evaluate(this);
    }

    @Override
    public Object visitAssignableVariable(AssignableVariableExpression expression) {
        return expression.target().evaluate(this);
    }

    @Override
    public Object visitStruct(StructAccessExpression expression) {
        Object value = expression.left().visit(this);
        if (value instanceof Struct) {
            return ((Struct) value).getProperty(expression.path());
        }
        return null;
    }

    @Override
    public Object visitBinary(BinaryExpression expression) {
        return BINARY_EVALUATORS[expression.op().index()].eval(
                this,
                expression.left(),
                expression.right()
        );
    }

    @Override
    public Object visitBinaryOperation(BinaryOperationExpression expression) {
        Object left = expression.getLeft().visit(this);
        Object right = expression.getRight().visit(this);
        if (right instanceof Number) {
            int index = ((Number) right).intValue();
            if (index < 0) index = 0;
            if (left instanceof List) {
                List<?> list = (List<?>) left;
                if (list.size() > index) return list.get(index);
            }
            return null;
        }
        return null;
    }

    @Override
    public Object visitUnary(UnaryExpression expression) {
        Object value = expression.expression().visit(this);
        switch (expression.op()) {
            case LOGICAL_NEGATION:
                return ValueConversions.asBoolean(value) ? Boolean.FALSE : Boolean.TRUE;
            case ARITHMETICAL_NEGATION:
                return -ValueConversions.asFloat(value);
            case RETURN: {
                this.returnValue = value;
                return DOUBLE_ZERO;
            }
            default:
                throw new IllegalStateException("Unknown unary operation: " + expression.op());
        }
    }

    @Override
    public Object visitStatement(StatementExpression expression) {
        this.op = expression.op();
        return null;
    }

    @Override
    public Object visitString(StringExpression expression) {
        return expression;
    }

    @Override
    public Object visitTernaryConditional(TernaryConditionalExpression expression) {
        Object condition = expression.condition().visit(this);
        return ValueConversions.asBoolean(condition)
                ? expression.trueExpression().visit(this)
                : expression.falseExpression().visit(this);
    }

    @Override
    public Object visit(Expression expression) {
        throw new UnsupportedOperationException("Unsupported expression type: " + expression.getClass().getName());
    }

    private interface Evaluator {
        Object eval(ExpressionEvaluatorImpl evaluator, Expression a, Expression b);
    }
}
