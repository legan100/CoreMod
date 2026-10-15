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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Abstellungen: Bereiche mit Gleisen, auf denen Züge eingesetzt werden können. Gespeichert in CoreMod/abstellungen.json,
 * bearbeitet per /mainmenu routes spawns. Die Gleise liegen auf MR-Splines, gesetzt per Rechtsklick (siehe Weltauswahl).
 */
public final class Abstellungen {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("CoreMod/abstellungen.json");
    private static final Type TYPE = new TypeToken<List<Abstellung>>() {}.getType();

    private static volatile List<Abstellung> abstellungen = List.of();

    private Abstellungen() {
    }

    public record Abstellung(String id, String name, List<Gleis> gleise) {
    }

    /**
     * Ein Gleis der Abstellung: Stück MR-Spline zwischen zwei Punkten, auch über mehrere Segmente.
     *
     * @param dimension MR-Scope = Dimension, z.B. "minecraft:overworld"
     * @param start     schaut in Richtung {@code end}, so steht ein eingesetzter Zug
     * @param length    Meter entlang des Gleises
     */
    public record Gleis(String id, String name, String dimension, Gleispunkt start, Gleispunkt end, double length) {
    }

    /**
     * Punkt auf einer MR-Spline. Die Koordinaten sind nur zur Anzeige, maßgeblich ist die Position auf dem Segment.
     *
     * @param distance Meter ab dem Startknoten des Segments
     * @param facing   schaut zum Endknoten des Segments
     */
    public record Gleispunkt(String segment, double distance, boolean facing, double x, double y, double z) {
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            List<Abstellung> loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), TYPE);
            abstellungen = loaded == null ? List.of() : List.copyOf(loaded);
        } catch (IOException | JsonParseException e) {
            throw new IllegalStateException("[CoreMod] " + FILE + " konnte nicht geladen werden", e);
        }
    }

    public static List<Abstellung> all() {
        return abstellungen;
    }

    /**
     * Speichert die Abstellungen aus dem Editor. Gleise entstehen nur auf dem Server (Weltauswahl), der Editor
     * benennt sie höchstens um oder löscht sie.
     *
     * @throws IllegalArgumentException bei leeren Namen oder doppelten IDs
     */
    public static synchronized void save(List<Abstellung> changed) throws IOException {
        Set<String> ids = new HashSet<>();
        for (Abstellung abstellung : changed) {
            if (abstellung.id().isBlank() || !ids.add(abstellung.id())) throw new IllegalArgumentException("Abstellungs-ID leer oder doppelt");
            if (abstellung.name().isBlank()) throw new IllegalArgumentException("Abstellung ohne Namen");
            for (Gleis gleis : abstellung.gleise()) {
                if (gleis.id().isBlank() || !ids.add(gleis.id())) throw new IllegalArgumentException("Gleis-ID leer oder doppelt");
                if (gleis.name().isBlank()) throw new IllegalArgumentException("Gleis ohne Namen in " + abstellung.name());
            }
        }

        // ponytail: wer zuletzt speichert, gewinnt (wie bei Strecken und Garnituren)
        Files.writeString(FILE, GSON.toJson(changed, TYPE), StandardCharsets.UTF_8);
        abstellungen = List.copyOf(changed);
    }

    /** Neues Gleis aus der Gleisauswahl anhängen. @return false, wenn es die Abstellung inzwischen nicht mehr gibt */
    static synchronized boolean addGleis(String abstellungId, Gleis gleis) throws IOException {
        List<Abstellung> changed = new ArrayList<>();
        boolean found = false;
        for (Abstellung abstellung : abstellungen) {
            if (abstellung.id().equals(abstellungId)) {
                List<Gleis> gleise = new ArrayList<>(abstellung.gleise());
                gleise.add(gleis);
                abstellung = new Abstellung(abstellung.id(), abstellung.name(), gleise);
                found = true;
            }
            changed.add(abstellung);
        }
        if (found) save(changed);
        return found;
    }

    public static Abstellung find(String id) {
        return abstellungen.stream().filter(abstellung -> abstellung.id().equals(id)).findFirst().orElse(null);
    }
}
