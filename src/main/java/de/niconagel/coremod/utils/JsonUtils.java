package de.niconagel.coremod.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class JsonUtils {

    private JsonUtils() {
    }

    public static String getString(
            String json,
            String key
    ) {

        try {

            JsonElement root =
                    JsonParser.parseString(json);

            if (!root.isJsonObject()) {
                return null;
            }

            JsonObject object =
                    root.getAsJsonObject();

            if (!object.has(key)
                    || object.get(key).isJsonNull()) {

                return null;
            }

            return object
                    .get(key)
                    .getAsString();

        } catch (Exception e) {

            return null;
        }
    }
}
