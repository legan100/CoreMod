package de.niconagel.coremod.tf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Zuggarnituren (Lok und Wagen in Reihenfolge), gespeichert in CoreMod/garnituren.json, bearbeitet per /mainmenu routes trains.
 * Global: Routen verweisen über die feste {@link Garnitur#id()} darauf, beliebig viele Routen können dieselbe nutzen.
 */
public final class Garnituren {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("CoreMod/garnituren.json");
    private static final Type TYPE = new TypeToken<List<Garnitur>>() {}.getType();

    private static volatile List<Garnitur> garnituren = List.of();

    private Garnituren() {
    }

    /** @param id fest, bleibt beim Umbenennen gleich */
    public record Garnitur(String id, String name, List<Fahrzeug> vehicles) {
    }

    /**
     * Ein Fahrzeug der Garnitur.
     *
     * @param type     MR-ID, siehe {@link Fahrzeugkatalog}
     * @param reversed umgedreht eingereiht
     */
    public record Fahrzeug(String type, boolean reversed) {
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            List<Garnitur> loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), TYPE);
            garnituren = loaded == null ? List.of() : List.copyOf(loaded);
        } catch (IOException | JsonParseException e) {
            throw new IllegalStateException("[CoreMod] " + FILE + " konnte nicht geladen werden", e);
        }
    }

    public static List<Garnitur> all() {
        return garnituren;
    }

    /**
     * Speichert die Garnituren aus dem Zug-Editor. Fahrzeuge, die MR (nicht mehr) kennt, sind erlaubt: sonst ließe sich nach
     * dem Entfernen eines Content-Packs gar nichts mehr speichern. Der Editor zeigt sie rot an.
     *
     * @throws IllegalArgumentException bei leeren Namen oder doppelten IDs
     */
    public static synchronized void save(List<Garnitur> changed) throws IOException {
        Set<String> ids = new HashSet<>();
        for (Garnitur garnitur : changed) {
            if (garnitur.id().isBlank() || !ids.add(garnitur.id())) throw new IllegalArgumentException("Garnitur-ID leer oder doppelt");
            if (garnitur.name().isBlank()) throw new IllegalArgumentException("Garnitur ohne Namen");
            if (garnitur.vehicles().stream().anyMatch(vehicle -> vehicle.type().isBlank())) {
                throw new IllegalArgumentException("Fahrzeug ohne Typ in " + garnitur.name());
            }
        }

        // ponytail: wer zuletzt speichert, gewinnt (wie bei den Strecken)
        Files.writeString(FILE, GSON.toJson(changed, TYPE), StandardCharsets.UTF_8);
        garnituren = List.copyOf(changed);
    }

    public static Set<String> ids() {
        Set<String> ids = new HashSet<>();
        garnituren.forEach(garnitur -> ids.add(garnitur.id()));
        return ids;
    }
}
