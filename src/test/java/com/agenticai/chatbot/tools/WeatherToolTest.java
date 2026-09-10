package com.agenticai.chatbot.tools;

import com.agenticai.chatbot.model.ChatModels.WeatherResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherToolTest {

    private WeatherTool weatherTool;

    @BeforeEach
    void setUp() {
        weatherTool = new WeatherTool();
    }

    @Test
    void testGetWeather_KnownCity_Celsius() {
        String city = "  London  ";
        WeatherResponse response = weatherTool.getWeather(city, "celsius");

        assertNotNull(response);
        assertEquals("  London  ", response.city());
        assertEquals("celsius", response.unit());

        // London temperatures in data are 12.0, 15.0, 18.0
        List<Double> expectedTemps = List.of(12.0, 15.0, 18.0);
        assertTrue(expectedTemps.contains(response.temperature()));

        // Assertions for dynamically generated mock attributes
        assertTrue(response.humidity() >= 50 && response.humidity() <= 90);
        assertTrue(response.windSpeed() >= 5.0 && response.windSpeed() <= 30.0);
        assertNotNull(response.condition());
    }

    @Test
    void testGetWeather_KnownCity_Fahrenheit() {
        String city = "tokyo";
        WeatherResponse response = weatherTool.getWeather(city, "fahrenheit");

        assertNotNull(response);
        assertEquals("fahrenheit", response.unit());

        // Tokyo temperatures in celsius are 22.0, 27.0, 32.0
        // Converted: (22*9/5)+32 = 71.6, (27*9/5)+32 = 80.6, (32*9/5)+32 = 89.6
        List<Double> expectedFahrenheitTemps = List.of(71.6, 80.6, 89.6);
        assertTrue(expectedFahrenheitTemps.contains(response.temperature()));
    }

    @Test
    void testGetWeather_UnknownCity_Defaults() {
        String city = "Atlantis";
        WeatherResponse response = weatherTool.getWeather(city, "celsius");

        assertNotNull(response);
        // Default temperatures for fallback logic are 20.0, 25.0, 30.0
        List<Double> fallbackTemps = List.of(20.0, 25.0, 30.0);
        assertTrue(fallbackTemps.contains(response.temperature()));
    }

    @Test
    void testGetWeather_NullUnit_DefaultsToCelsius() {
        WeatherResponse response = weatherTool.getWeather("paris", null);

        assertNotNull(response);
        assertEquals("celsius", response.unit());
    }

    @Test
    void testGetWeather_BlankUnit_DefaultsToCelsius() {
        WeatherResponse response = weatherTool.getWeather("paris", "   ");

        assertNotNull(response);
        assertEquals("celsius", response.unit());
    }
}