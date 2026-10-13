package de.niconagel.coremod.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class JsonUtils {

    private JsonUtils() {}

    public static String getString(String json, String key) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;

            JsonObject object = root.getAsJsonObject();

            String value = get(object, key);
            if (value != null) return value;

            if (object.has("data") && object.get("data").isJsonObject())
                return get(object.getAsJsonObject("data"), key);

        } catch (Exception ignored) {}

        return null;
    }

    private static String get(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull())
            return null;

        try {
            return object.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }
}
