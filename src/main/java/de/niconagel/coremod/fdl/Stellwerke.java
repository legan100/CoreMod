package de.niconagel.coremod.fdl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stellwerke für Fahrdienstleiter: wo sie erscheinen und in welchem Bereich sie sich bewegen dürfen. Gespeichert in
 * CoreMod/stellwerke.json, bearbeitet per /mainmenu stellwerk; Spawn und Bereich setzt man in der Welt ({@link StellwerkAuswahl}).
 */
public final class Stellwerke {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("CoreMod/stellwerke.json");
    private static final Type TYPE = new TypeToken<List<Stellwerk>>() {}.getType();

    private static volatile List<Stellwerk> stellwerke = List.of();

    private Stellwerke() {
    }

    public record Stellwerk(String id, String name, String dimension, Spawn spawn, Bereich bereich) {
    }

    /** Wo der Fahrdienstleiter erscheint, mit Blickrichtung. */
    public record Spawn(double x, double y, double z, float yaw, float pitch) {
    }

    /** Quader aus ganzen Blöcken, beide Ecken eingeschlossen. */
    public record Bereich(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        static Bereich of(int x1, int y1, int z1, int x2, int y2, int z2) {
            return new Bereich(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
        }

        /** Liegt der Punkt (z.B. die Füße eines Spielers) in einem der Blöcke? */
        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
        }
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            List<Stellwerk> loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), TYPE);
            stellwerke = loaded == null ? List.of() : List.copyOf(loaded);
        } catch (IOException | JsonParseException e) {
            throw new IllegalStateException("[CoreMod] " + FILE + " konnte nicht geladen werden", e);
        }
    }

    public static List<Stellwerk> all() {
        return stellwerke;
    }

    public static Stellwerk find(String id) {
        return stellwerke.stream().filter(stellwerk -> stellwerk.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * Speichert die Stellwerke aus dem Editor. Spawn und Bereich entstehen nur auf dem Server, der Editor benennt höchstens
     * um oder löscht.
     *
     * @throws IllegalArgumentException bei leeren Namen oder doppelten IDs
     */
    public static synchronized void save(List<Stellwerk> changed) throws IOException {
        Set<String> ids = new HashSet<>();
        for (Stellwerk stellwerk : changed) {
            if (stellwerk.id().isBlank() || !ids.add(stellwerk.id())) throw new IllegalArgumentException("Stellwerks-ID leer oder doppelt");
            if (stellwerk.name().isBlank()) throw new IllegalArgumentException("Stellwerk ohne Namen");
        }
        // ponytail: wer zuletzt speichert, gewinnt (wie bei Strecken, Garnituren und Abstellungen)
        Files.writeString(FILE, GSON.toJson(changed, TYPE), StandardCharsets.UTF_8);
        stellwerke = List.copyOf(changed);
    }

    /** Neues Stellwerk anhängen bzw. ein vorhandenes (gleiche ID) ersetzen. */
    static synchronized void put(Stellwerk stellwerk) throws IOException {
        List<Stellwerk> changed = new ArrayList<>(stellwerke.stream().filter(s -> !s.id().equals(stellwerk.id())).toList());
        int index = stellwerke.stream().map(Stellwerk::id).toList().indexOf(stellwerk.id());
        changed.add(index < 0 ? changed.size() : index, stellwerk);
        save(changed);
    }
}
