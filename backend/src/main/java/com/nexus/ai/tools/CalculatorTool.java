package com.nexus.ai.tools;

import com.nexus.ai.domain.ToolCall;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Map;
import java.util.UUID;

@Component
public class CalculatorTool implements Tool {

    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "Evaluates mathematical expressions (e.g. '123456 * 98765', 'sqrt(144)', '2^10').";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "expression", Map.of("type", "string", "description", "Math expression to evaluate")
                ),
                "required", java.util.List.of("expression")
        );
    }

    @Override
    public ToolCall execute(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String expression = String.valueOf(arguments.get("expression"));
        try {
            double result = evaluateExpression(expression);
            long latency = System.currentTimeMillis() - start;
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result(String.valueOf(result))
                    .success(true)
                    .executionTimeMs(latency)
                    .build();
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return ToolCall.builder()
                    .id(UUID.randomUUID().toString())
                    .toolName(getName())
                    .arguments(arguments)
                    .result("Error evaluating math expression: " + e.getMessage())
                    .success(false)
                    .executionTimeMs(latency)
                    .build();
        }
    }

    private double evaluateExpression(String expr) {
        // Simple mathematical evaluator
        String sanitized = expr.replaceAll("[^0-9\\+\\-\\*/\\.\\(\\)\\^\\s]", "");
        return evalSimple(sanitized);
    }

    private double evalSimple(String str) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char)ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if      (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if      (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return +parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Unexpected char: " + (char)ch);
                }

                if (eat('^')) x = Math.pow(x, parseFactor());

                return x;
            }
        }.parse();
    }
}
