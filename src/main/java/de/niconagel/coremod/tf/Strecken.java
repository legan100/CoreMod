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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strecken und Routen für Triebfahrzeugführer, gespeichert in CoreMod/strecken.json, bearbeitet per /mainmenu routes.
 * Neue Routen entstehen über den Assistenten in {@link Weltauswahl} (Haltepunkte in der Welt, dann die Abstellung).
 */
public final class Strecken {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Path.of("CoreMod/strecken.json");
    private static final Type TYPE = new TypeToken<List<Strecke>>() {}.getType();

    private static volatile List<Strecke> strecken = List.of();

    private Strecken() {
    }

    /** @param name eindeutig, darüber findet der Routen-Assistent die Strecke wieder */
    public record Strecke(String name, List<Route> routes) {
    }

    /**
     * Eine Fahrt auf der Strecke.
     *
     * @param number     TrainOS-Routennummer, dreistellig und eindeutig über alle Strecken; die Zugnummer entsteht beim Start daraus
     * @param name       z.B. "RE10"
     * @param garnitur   ID der Garnitur ({@link Garnituren}), "" = noch keine
     * @param abstellung ID der Abstellung, in der der Zug eingesetzt wird ({@link Abstellungen}), "" = noch keine
     * @param stops      Haltepunkte in Fahrtreihenfolge; der erste und letzte sind Von und Nach
     */
    public record Route(String number, String name, String garnitur, String abstellung, List<Haltepunkt> stops) {
    }

    /** Ein Halt der Route: Punkt auf einer MR-Spline. */
    public record Haltepunkt(String name, String dimension, Abstellungen.Gleispunkt punkt) {
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) return;

            List<Strecke> loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), TYPE);
            // Routen aus der Zeit vor Haltepunkten und Abstellungen haben deren Felder nicht, Gson lässt sie dann null
            strecken = loaded == null ? List.of() : loaded.stream()
                    .map(strecke -> new Strecke(strecke.name(), strecke.routes().stream()
                            .map(r -> new Route(r.number(), r.name() == null ? r.number() : r.name(),
                                    r.garnitur() == null ? "" : r.garnitur(),
                                    r.abstellung() == null ? "" : r.abstellung(),
                                    r.stops() == null ? List.of() : r.stops()))
                            .toList()))
                    .toList();
        } catch (IOException | JsonParseException e) {
            throw new IllegalStateException("[CoreMod] " + FILE + " konnte nicht geladen werden", e);
        }
    }

    public static List<Strecke> all() {
        return strecken;
    }

    /**
     * Speichert die Strecken aus dem Routen-Editor (/mainmenu routes). Der Editor prüft dasselbe schon vorher,
     * hier nur, weil die Daten vom Client kommen.
     *
     * @throws IllegalArgumentException bei leeren oder doppelten Namen, fehlenden Angaben oder doppelten Routennummern
     */
    public static synchronized void save(List<Strecke> changed) throws IOException {
        Set<String> names = new HashSet<>();
        Set<String> numbers = new HashSet<>();
        for (Strecke strecke : changed) {
            if (strecke.name().isBlank()) throw new IllegalArgumentException("Strecke ohne Namen");
            if (!names.add(strecke.name())) throw new IllegalArgumentException("Streckenname doppelt: " + strecke.name());
            for (Route route : strecke.routes()) {
                if (route.number().isBlank() || route.name().isBlank()) {
                    throw new IllegalArgumentException("Unvollständige Route auf " + strecke.name());
                }
                if (!numbers.add(route.number())) {
                    throw new IllegalArgumentException("Routennummer doppelt: " + route.number());
                }
            }
        }

        // ponytail: wer zuletzt speichert, gewinnt; bei mehreren gleichzeitigen Editoren ggf. Versionsnummer mitschicken
        Files.writeString(FILE, GSON.toJson(changed, TYPE), StandardCharsets.UTF_8);
        strecken = List.copyOf(changed);
    }

    /** Neue Route aus dem Assistenten anhängen. @return false, wenn es die Strecke inzwischen nicht mehr gibt */
    static synchronized boolean addRoute(String streckeName, Route route) throws IOException {
        List<Strecke> changed = new ArrayList<>();
        boolean found = false;
        for (Strecke strecke : strecken) {
            if (strecke.name().equals(streckeName)) {
                List<Route> routes = new ArrayList<>(strecke.routes());
                routes.add(route);
                strecke = new Strecke(strecke.name(), routes);
                found = true;
            }
            changed.add(strecke);
        }
        if (found) save(changed);
        return found;
    }

    /** Nach dem Löschen von Garnituren: Routen, die auf eine gelöschte zeigen, stehen danach auf "keine Garnitur". */
    public static synchronized void unlinkMissingGarnituren(Set<String> existing) throws IOException {
        boolean changed = strecken.stream().flatMap(strecke -> strecke.routes().stream())
                .anyMatch(route -> !route.garnitur().isEmpty() && !existing.contains(route.garnitur()));
        if (!changed) return;

        save(strecken.stream()
                .map(strecke -> new Strecke(strecke.name(), strecke.routes().stream()
                        .map(r -> existing.contains(r.garnitur()) ? r : new Route(r.number(), r.name(), "", r.abstellung(), r.stops()))
                        .toList()))
                .toList());
    }

    /** @return die Route mit dieser Routennummer oder null */
    public static Route route(String number) {
        for (Strecke strecke : strecken) {
            for (Route route : strecke.routes()) {
                if (route.number().equals(number)) return route;
            }
        }
        return null;
    }

    /**
     * Zugnummer beim Start: TrainOS-Nummer und die Abfahrtsminute, 5 Minuten Vorbereitung eingerechnet.
     * Route 384 um 13:40 gestartet → Abfahrt 13:45 → "38445".
     */
    public static String zugnummer(String routeNumber, LocalTime startedAt) {
        return routeNumber + String.format("%02d", startedAt.plusMinutes(5).getMinute());
    }
}
