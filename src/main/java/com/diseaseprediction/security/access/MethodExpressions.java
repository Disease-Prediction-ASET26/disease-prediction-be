package com.diseaseprediction.security.access;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads a resource id from a guarded call with a SpEL expression over the method arguments, such as
 * {@code #id} or {@code #request.assessmentId}. After the call, {@code #result} holds the return
 * value. Parsed expressions are cached.
 */
@Component
public class MethodExpressions {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    /**
     * @param result the return value, or {@code null} before the call or when it failed
     * @return the id, or {@code null} when the expression yields nothing
     * @throws IllegalArgumentException when the expression yields something that is not an id
     */
    public UUID resourceId(JoinPoint call, String expression, Object result) {
        MethodSignature signature = (MethodSignature) call.getSignature();
        MethodBasedEvaluationContext context =
                new MethodBasedEvaluationContext(
                        call.getTarget(), signature.getMethod(), call.getArgs(), parameterNames);
        context.setVariable("result", result);
        Object value = cache.computeIfAbsent(expression, parser::parseExpression).getValue(context);
        return switch (value) {
            case null -> null;
            case UUID id -> id;
            case String text -> UUID.fromString(text);
            default ->
                    throw new IllegalArgumentException(
                            "Expression " + expression + " does not yield a resource id.");
        };
    }
}
