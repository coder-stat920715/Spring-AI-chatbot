package com.agenticai.chatbot.tools;

import com.agenticai.chatbot.model.ChatModels.WeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Agentic tool: weather lookup.
 *
 * <p>Claude will call this tool automatically whenever the user asks about
 * weather. In production, replace the mock data with a real API call
 * (e.g., OpenWeatherMap, WeatherAPI).
 *
 * <p>Spring AI discovers this tool via the {@code @Tool} annotation and
 * registers it with the Claude model. Claude sees the method name,
 * description, and parameter descriptions — so make them descriptive.
 */
@Slf4j
@Component
public class WeatherTool {

    private static final Random RANDOM = new Random();

    /** Simulated weather data per city (temperature in Celsius). */
    private static final Map<String, double[]> CITY_WEATHER = Map.of(
            "london",    new double[]{12.0, 15.0, 18.0},
            "new york",  new double[]{20.0, 25.0, 30.0},
            "tokyo",     new double[]{22.0, 27.0, 32.0},
            "sydney",    new double[]{18.0, 22.0, 26.0},
            "paris",     new double[]{14.0, 19.0, 24.0},
            "dubai",     new double[]{35.0, 38.0, 42.0},
            "mumbai",    new double[]{28.0, 32.0, 36.0},
            "singapore", new double[]{30.0, 33.0, 35.0}
    );

    private static final String[] CONDITIONS =
            {"Sunny", "Partly Cloudy", "Cloudy", "Light Rain", "Clear"};

    /**
     * Returns current weather for the specified city.
     *
     * @param city The city name to get weather for (e.g., "London", "Tokyo").
     * @param unit Temperature unit — "celsius" or "fahrenheit".
     */
    @Tool(name = "get_weather",
          description = "Get the current weather conditions for any city worldwide. "
                      + "Returns temperature, weather condition, humidity, and wind speed.")
    public WeatherResponse getWeather(
            @ToolParam(description = "The city name, e.g. 'London' or 'New York'")
            String city,
            @ToolParam(description = "Temperature unit: 'celsius' or 'fahrenheit'")
            String unit) {

        log.info("[TOOL CALL] get_weather → city={}, unit={}", city, unit);

        String cityKey = city.toLowerCase().trim();
        double[] temps = CITY_WEATHER.getOrDefault(cityKey, new double[]{20.0, 25.0, 30.0});
        double tempCelsius = temps[RANDOM.nextInt(temps.length)];

        double temperature = "fahrenheit".equalsIgnoreCase(unit)
                ? (tempCelsius * 9.0 / 5.0) + 32.0
                : tempCelsius;

        String condition = CONDITIONS[RANDOM.nextInt(CONDITIONS.length)];
        int humidity = 50 + RANDOM.nextInt(40);         // 50–90%
        double windSpeed = 5 + RANDOM.nextDouble() * 25; // 5–30 km/h

        return new WeatherResponse(
                city,
                Math.round(temperature * 10.0) / 10.0,
                unit == null || unit.isBlank() ? "celsius" : unit.toLowerCase(),
                condition,
                humidity,
                Math.round(windSpeed * 10.0) / 10.0
        );
    }
}