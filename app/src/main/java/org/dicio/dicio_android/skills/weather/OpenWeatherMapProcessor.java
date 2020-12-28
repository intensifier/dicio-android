package org.dicio.dicio_android.skills.weather;

import org.dicio.skill.IntermediateProcessor;
import org.dicio.skill.standard.StandardResult;
import org.dicio.dicio_android.ApiKeys;
import org.dicio.dicio_android.util.ConnectionUtils;
import org.dicio.dicio_android.util.StringUtils;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.util.Locale;

import static org.dicio.dicio_android.Sentences_en.weather;

public class OpenWeatherMapProcessor implements IntermediateProcessor<StandardResult, WeatherOutput.Data> {

    private static final String ipInfoUrl = "https://ipinfo.io/json";
    private static final String weatherApiUrl = "https://api.openweathermap.org/data/2.5/weather";
    private static final String iconBaseUrl = "https://openweathermap.org/img/wn/";
    private static final String iconFormat = "@2x.png";


    @Override
    public WeatherOutput.Data process(final StandardResult data, final Locale locale)
            throws Exception {
        final WeatherOutput.Data result = new WeatherOutput.Data();
        result.city = data.getCapturingGroup(weather.where);
        if (result.city != null) {
            result.city = StringUtils.removePunctuation(result.city.trim());
        }

        if (result.city == null || result.city.isEmpty()) {
            final JSONObject ipInfo = ConnectionUtils.getPageJson(ipInfoUrl);
            result.city = ipInfo.getString("city");
        }

        final JSONObject weatherData;
        try {
            weatherData = ConnectionUtils.getPageJson(weatherApiUrl
                    + "?APPID=" + ApiKeys.openweathermap
                    + "&units=metric&lang=" + locale.getLanguage().toLowerCase()
                    + "&q=" + ConnectionUtils.urlEncode(result.city));
        } catch (FileNotFoundException ignored) {
            result.failed = true;
            return result;
        }

        final JSONObject weatherObject = weatherData.getJSONArray("weather").getJSONObject(0);
        final JSONObject mainObject = weatherData.getJSONObject("main");
        final JSONObject windObject = weatherData.getJSONObject("wind");

        result.city = weatherData.getString("name");
        result.description = weatherObject.getString("description");
        result.iconUrl = iconBaseUrl + weatherObject.getString("icon") + iconFormat;
        result.temp = mainObject.getDouble("temp");
        result.tempMin = mainObject.getDouble("temp_min");
        result.tempMax = mainObject.getDouble("temp_max");
        result.windSpeed = windObject.getDouble("speed");

        result.description = Character.toUpperCase(result.description.charAt(0)) +
                result.description.substring(1);
        return result;
    }
}