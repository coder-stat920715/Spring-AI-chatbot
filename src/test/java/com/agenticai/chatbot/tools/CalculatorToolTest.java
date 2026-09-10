package com.agenticai.chatbot.tools;

import com.agenticai.chatbot.model.ChatModels.CalculatorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorToolTest {

    private CalculatorTool calculatorTool;

    @BeforeEach
    void setUp() {
        calculatorTool = new CalculatorTool();
    }

    @Test
    void testCalculate_Success() {
        String expression = "(150 * 0.18) + 25";
        CalculatorResponse response = calculatorTool.calculate(expression);

        assertNotNull(response);
        assertEquals(expression, response.expression());
        assertEquals(52.0, response.result());
    }

    @Test
    void testCalculate_Rounding() {
        // 10 divided by 3 is 3.33333333... should round to 6 decimal places
        String expression = "10 / 3";
        CalculatorResponse response = calculatorTool.calculate(expression);

        assertNotNull(response);
        assertEquals(3.333333, response.result());
    }

    @Test
    void testCalculate_NullExpression() {
        CalculatorResponse response = calculatorTool.calculate(null);

        assertNotNull(response);
        assertEquals(null, response.expression());
        assertTrue(Double.isNaN(response.result()));
    }

    @Test
    void testCalculate_BlankExpression() {
        CalculatorResponse response = calculatorTool.calculate("   ");

        assertNotNull(response);
        assertEquals("   ", response.expression());
        assertTrue(Double.isNaN(response.result()));
    }

    @Test
    void testCalculate_InvalidCharactersWhitelistGuard() {
        // Contains 'a' which is not allowed by the regex whitelist
        String expression = "10 + 5 + a";
        CalculatorResponse response = calculatorTool.calculate(expression);

        assertNotNull(response);
        assertEquals(expression, response.expression());
        assertTrue(Double.isNaN(response.result()));
    }

    @Test
    void testCalculate_EvaluationException() {
        // Valid characters but mathematically malformed syntax triggers exp4j exception
        String expression = "10 ++ 5";
        CalculatorResponse response = calculatorTool.calculate(expression);

        assertNotNull(response);
        assertEquals(expression, response.expression());
        assertFalse(Double.isNaN(response.result()));
    }
}