package won.bot.skeleton.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class TranslatorAPI {

    static String locationiqKey = "XXX";
    static String deeplKey = "XXX";

    static Optional<String> languageForGPS(String lat, String lon) {

        Optional<String> countryCode = countyCodeOfGPS(lat, lon);
        if (!countryCode.isPresent()) return Optional.empty();
        return languageOfCountryCode(countryCode.get());

    }

    static Optional<String> countyCodeOfGPS(String lat, String lon) {

        try {

            String infoURI = String.format("https://eu1.locationiq.com/v1/reverse.php?key=%s&lat=%s&lon=%s&format=json", locationiqKey, lat, lon);

            System.out.println(infoURI);
            RestTemplate restTemplate = new RestTemplate();
            String rawResponse = restTemplate.getForObject(infoURI, String.class);
            JsonParser jsonParser = new JsonParser();
            JsonElement parsedJson = jsonParser.parse(rawResponse);

            String countryCode = parsedJson.getAsJsonObject().get("address").getAsJsonObject().get("country_code").getAsString();

            return Optional.of(countryCode);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static Optional<String> languageOfCountryCode(String countryCode) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String countryURI = "https://restcountries.eu/rest/v2/alpha/" + countryCode;
            JSONObject countryModel = restTemplate.getForObject(countryURI, JSONObject.class);
            List<HashMap<String, String>> languages = (List<HashMap<String, String>>) countryModel.get("languages");
            return Optional.of(languages.get(0).get("iso639_1"));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static Optional<String> translate(String sourceLanguage, String targetLanguage, String text) {

        try {
            RestTemplate restTemplate = new RestTemplate();
            String translationURI = String.format("https://api.deepl.com/v2/translate?target_lang=%s&source_lang=%s&auth_key=%s&text=%s", targetLanguage, sourceLanguage, deeplKey, text);

            String rawResponse = restTemplate.getForObject(translationURI, String.class);

            JsonParser jsonParser = new JsonParser();
            JsonElement parsedJson = jsonParser.parse(rawResponse);

            String translatedText = parsedJson.getAsJsonObject().get("translations").getAsJsonArray().get(0).getAsJsonObject().get("text").getAsString();

            return Optional.of(translatedText);

        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<String> translateWithGps(String sourceLat, String sourceLon, String targetLat, String targetLon, String text) {
        Optional<String> sourceLanguage = languageForGPS(sourceLat, sourceLon);
        Optional<String> targetLanguage = languageForGPS(targetLat, targetLon);

        if (!sourceLanguage.isPresent() || !targetLanguage.isPresent()) return Optional.empty();

        return translate(sourceLanguage.get(), targetLanguage.get(), text);
    }

    private static class RequestDTO {
        String sourceLat, sourceLon;
        String targetLat, targetLon;
        String text;
    }

    private static class ResponseDTO {
        String message;
        String status;
    }

    public static String handleRequest(String jsonInput) {
        Gson gson = new Gson();
        ResponseDTO responseDTO = new ResponseDTO();

        try {
            RequestDTO requestDTO = gson.fromJson(jsonInput, RequestDTO.class);

            Optional<String> translation = translateWithGps(requestDTO.sourceLat, requestDTO.sourceLon, requestDTO.targetLat, requestDTO.targetLon, requestDTO.text);

            if (!translation.isPresent()) {
                responseDTO.status = "error";
                responseDTO.message = "error with external api";
            } else {
                responseDTO.status = "success";
                responseDTO.message = translation.get();
            }

        } catch (Exception e) {

            responseDTO.status = "error";
            responseDTO.message = "request is in invalid format";

        }

        return gson.toJson(responseDTO);
    }

    //simple test of api
    public static void main(String[] args) {
        RequestDTO requestDTO = new RequestDTO();
        requestDTO.sourceLat = "48.2082";
        requestDTO.sourceLon = "16.3738";
        requestDTO.targetLat = "40.7487727";
        requestDTO.targetLon = "-73.9849336";
        requestDTO.text = "ich sitze im baumhaus";

        Gson gson = new Gson();
        String requestString = gson.toJson(requestDTO);
        System.out.println(requestString);
        System.out.println(handleRequest(requestString));
    }
}
