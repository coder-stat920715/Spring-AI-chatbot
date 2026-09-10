package com.agenticai.chatbot.tools;

import com.agenticai.chatbot.model.ChatModels.CalculatorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * Agentic tool: safe arithmetic calculator.
 *
 * <p>Claude calls this when the user asks for calculations. Using a script
 * engine provides basic expression evaluation. In production, consider
 * a dedicated math library (e.g., exp4j, JEval) for richer support
 * and stricter sandboxing.
 */
@Slf4j
@Component
public class CalculatorTool {

    /**
     * Evaluates a mathematical expression and returns the numeric result.
     *
     * @param expression A mathematical expression, e.g. "(150 * 0.18) + 25".
     */
    @Tool(name = "calculate",
            description = "Evaluate arithmetic expressions such as addition, subtraction, "
                    + "multiplication, division, percentages, and parenthesised expressions. "
                    + "Example: '(150 * 0.18) + 25'")
    public CalculatorResponse calculate(
            @ToolParam(description = "A valid arithmetic expression, e.g. '(100 + 50) * 0.15'")
            String expression) {

        log.info("[TOOL CALL] calculate → expression={}", expression);

        // Fail early for null, empty, or whitespace-only expressions
        if (expression == null || expression.isBlank()) {
            return new CalculatorResponse(expression, Double.NaN);
        }

        // Whitelist guard — only allow digits, arithmetic operators, spaces, dots, parentheses, and exponents
        if (!expression.matches("[0-9+\\-*/%. ()^]+")) {
            return new CalculatorResponse(expression, Double.NaN);
        }

        try {
            // Using exp4j to evaluate the mathematical expression safely
            Expression expr = new ExpressionBuilder(expression).build();
            double result = expr.evaluate();

            // Round to 6 decimal places as required by specifications
            result = Math.round(result * 1_000_000.0) / 1_000_000.0;
            return new CalculatorResponse(expression, result);

        } catch (Exception e) {
            log.warn("Calculator evaluation failed for expression '{}': {}", expression, e.getMessage());
            return new CalculatorResponse(expression, Double.NaN);
        }
    }
}