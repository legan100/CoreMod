package de.niconagel.coremod.tf;

import de.niconagel.coremod.mainmenu.MainMenuNetwork;
import de.niconagel.coremod.mainmenu.MainMenuNetwork.AuswahlState;
import de.niconagel.coremod.mainmenu.MainMenuNetwork.Marker;
import de.niconagel.coremod.tf.Abstellungen.Abstellung;
import de.niconagel.coremod.tf.Abstellungen.Gleis;
import de.niconagel.coremod.tf.Abstellungen.Gleispunkt;
import de.niconagel.coremod.tf.Strecken.Haltepunkt;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Punkte auf MR-Splines in der Welt setzen, während der Editor zu ist. Der Spieler sieht die Splines in seiner Nähe, der
 * Client zeigt an, wo ein Klick landet, und schickt den Punkt; hier wird er auf die Spline gesetzt und geprüft.
 * <ul>
 *     <li>{@link #GLEIS}: Anfang und Ende eines Gleises einer Abstellung, danach öffnet sich der Abstellungs-Editor wieder.</li>
 *     <li>{@link #HALTE}: Haltepunkte einer neuen Route, je einer pro Klick; danach wählt der Spieler eine Abstellung, von
 *     der der Zug den ersten Halt erreicht, und die Route ist angelegt.</li>
 * </ul>
 */
@EventBusSubscriber(value = Dist.DEDICATED_SERVER)
public final class Weltauswahl {

    // Modus für den Client, 0 = aus
    public static final int GLEIS = 1;
    public static final int HALTE = 2;

    // Aktionen vom Client
    public static final int SET = 0;
    /** Schleichen + Rechtsklick: beim Gleis abbrechen, bei Haltepunkten fertig. */
    public static final int DONE = 1;
    /** Linksklick: letzten Punkt zurücknehmen. */
    public static final int UNDO = 2;

    /** Splines so weit um den Spieler werden angezeigt, … */
    private static final double LINES_RADIUS = 160;
    /** … und neu geschickt, wenn er sich so weit davon entfernt hat. */
    private static final double REFRESH_DISTANCE = 64;
    /** Wie weit ein Klick neben der Spline liegen darf. */
    private static final double SNAP_DISTANCE = 3;
    private static final double REACH = 128;

    private sealed interface Auswahl permits GleisAuswahl, RoutenAuswahl {
        String dimension();

        Vec3 center();

        /** Neuer Standort; in einer anderen Dimension fängt alles neu an, Gleise und Routen liegen in einer Dimension. */
        Auswahl moved(String dimension, Vec3 center);
    }

    /** @param start null, solange der Anfang noch nicht gesetzt ist */
    private record GleisAuswahl(String abstellung, String name, String dimension, Gleispunkt start, Vec3 center) implements Auswahl {
        @Override
        public Auswahl moved(String dimension, Vec3 center) {
            return new GleisAuswahl(abstellung, name, dimension, dimension.equals(this.dimension) ? start : null, center);
        }
    }

    private record RoutenAuswahl(String strecke, String name, String number, String garnitur, List<Haltepunkt> stops,
                                 String dimension, Vec3 center) implements Auswahl {
        @Override
        public Auswahl moved(String dimension, Vec3 center) {
            return new RoutenAuswahl(strecke, name, number, garnitur, dimension.equals(this.dimension) ? stops : List.of(), dimension, center);
        }

        RoutenAuswahl withStops(List<Haltepunkt> stops) {
            return new RoutenAuswahl(strecke, name, number, garnitur, stops, dimension, center);
        }
    }

    private static final Map<UUID, Auswahl> ACTIVE = new ConcurrentHashMap<>();

    private Weltauswahl() {
    }

    // ---------------------------------------------------------------- Start

    public static void startGleis(ServerPlayer player, String abstellungId, String name) {
        if (name.isBlank() || Abstellungen.find(abstellungId) == null) return;
        begin(player, new GleisAuswahl(abstellungId, name.strip(), dimension(player), null, player.position()));
    }

    /** Neue Route: Name, TrainOS-Nummer und Garnitur kommen aus dem Formular, Haltepunkte und Abstellung folgen. */
    public static void startRoute(ServerPlayer player, String strecke, String name, String number, String garnitur) {
        if (name.isBlank() || !number.matches("\\d{3}") || Strecken.route(number) != null) return;
        if (Strecken.all().stream().noneMatch(s -> s.name().equals(strecke))) return;
        begin(player, new RoutenAuswahl(strecke, name.strip(), number, garnitur, List.of(), dimension(player), player.position()));
    }

    private static void begin(ServerPlayer player, Auswahl auswahl) {
        ACTIVE.put(player.getUUID(), auswahl);
        send(player, auswahl);
    }

    // ---------------------------------------------------------------- Klicks

    /** @param name Name des Haltepunkts (nur bei {@link #HALTE} und {@link #SET}) */
    public static void click(ServerPlayer player, Vector3f clicked, int action, String name) {
        switch (ACTIVE.get(player.getUUID())) {
            case GleisAuswahl gleis -> clickGleis(player, gleis, clicked, action);
            case RoutenAuswahl route -> clickRoute(player, route, clicked, action, name);
            case null -> {
            }
        }
    }

    private static void clickGleis(ServerPlayer player, GleisAuswahl auswahl, Vector3f clicked, int action) {
        if (action == DONE) {
            finish(player, MainMenuNetwork.OpenEditor.SPAWNS);
            return;
        }
        if (action == UNDO) {
            update(player, new GleisAuswahl(auswahl.abstellung(), auswahl.name(), auswahl.dimension(), null, auswahl.center()));
            return;
        }

        Gleispunkt punkt = snap(player, auswahl, clicked);
        if (punkt == null) return;

        List<Belegt> belegt = belegt(auswahl.dimension());
        for (Belegt other : belegt) {
            if (other.sections().stream().anyMatch(section -> section.contains(punkt))) {
                fail(player, "Hier liegt schon " + other.describe() + ".");
                return;
            }
        }

        if (auswahl.start() == null) {
            update(player, new GleisAuswahl(auswahl.abstellung(), auswahl.name(), auswahl.dimension(), punkt, auswahl.center()));
            return;
        }

        Gleisnetz.Verbindung verbindung = Gleisnetz.connect(auswahl.dimension(), auswahl.start(), punkt);
        if (verbindung == null) {
            fail(player, "Keine Verbindung zum Anfang (anderes Gleis oder nur mit Wenden erreichbar).");
            return;
        }
        if (verbindung.length() < 1) {
            fail(player, "Das Gleis ist zu kurz.");
            return;
        }
        // Anfang und Ende frei reicht nicht: der Weg dazwischen könnte über ein ganzes anderes Gleis führen
        for (Belegt other : belegt) {
            if (verbindung.sections().stream().anyMatch(section -> other.sections().stream().anyMatch(section::overlaps))) {
                fail(player, "Der Weg führt über " + other.describe() + ".");
                return;
            }
        }

        Gleis gleis = new Gleis(UUID.randomUUID().toString().substring(0, 8), auswahl.name(), auswahl.dimension(),
                verbindung.start(), verbindung.end(), verbindung.length());
        try {
            if (!Abstellungen.addGleis(auswahl.abstellung(), gleis)) {
                fail(player, "Die Abstellung gibt es nicht mehr.");
                return;
            }
        } catch (IOException e) {
            fail(player, "Gleis nicht gespeichert: " + e.getMessage());
            return;
        }
        success(player, "Gleis \"" + gleis.name() + "\" angelegt (" + Math.round(gleis.length()) + " m).");
        finish(player, MainMenuNetwork.OpenEditor.SPAWNS);
    }

    private static void clickRoute(ServerPlayer player, RoutenAuswahl auswahl, Vector3f clicked, int action, String name) {
        List<Haltepunkt> stops = auswahl.stops();
        switch (action) {
            case UNDO -> {
                if (!stops.isEmpty()) update(player, auswahl.withStops(stops.subList(0, stops.size() - 1)));
            }
            case DONE -> {
                // Ohne Haltepunkte heißt fertig: abbrechen
                if (stops.isEmpty()) {
                    finish(player, MainMenuNetwork.OpenEditor.ROUTES);
                    return;
                }
                List<MainMenuNetwork.Choice> choices = reachable(auswahl);
                if (choices.isEmpty()) {
                    fail(player, "Keine Abstellung erreicht den ersten Halt \"" + stops.getFirst().name() + "\" über die Schiene.");
                    return;
                }
                PacketDistributor.sendToPlayer(player, new MainMenuNetwork.ChooseAbstellung(choices));
            }
            default -> {
                if (name.isBlank()) return;
                Gleispunkt punkt = snap(player, auswahl, clicked);
                if (punkt == null) return;
                List<Haltepunkt> more = new ArrayList<>(stops);
                more.add(new Haltepunkt(name.strip(), auswahl.dimension(), punkt));
                update(player, auswahl.withStops(more));
            }
        }
    }

    /** Abstellung für die neue Route gewählt: Route anlegen und zurück in den Routen-Editor. */
    public static void chooseAbstellung(ServerPlayer player, String abstellungId) {
        if (!(ACTIVE.get(player.getUUID()) instanceof RoutenAuswahl auswahl)) return;
        // Der Client darf nur anbieten, was hier erreichbar ist; geprüft wird trotzdem
        if (reachable(auswahl).stream().noneMatch(choice -> choice.id().equals(abstellungId))) return;

        Strecken.Route route = new Strecken.Route(auswahl.number(), auswahl.name(), auswahl.garnitur(), abstellungId, auswahl.stops());
        try {
            if (Strecken.route(auswahl.number()) != null) {
                fail(player, "Die Routennummer " + auswahl.number() + " gibt es inzwischen schon.");
                return;
            }
            if (!Strecken.addRoute(auswahl.strecke(), route)) {
                fail(player, "Die Strecke gibt es nicht mehr.");
                return;
            }
        } catch (IOException | IllegalArgumentException e) {
            fail(player, "Route nicht gespeichert: " + e.getMessage());
            return;
        }
        success(player, "Route " + route.name() + " (" + route.number() + ") angelegt.");
        finish(player, MainMenuNetwork.OpenEditor.ROUTES);
    }

    /** Abstellungen mit mindestens einem Gleis, von dem aus ein Zug den ersten Halt über die Schiene erreicht. */
    private static List<MainMenuNetwork.Choice> reachable(RoutenAuswahl auswahl) {
        Gleispunkt first = auswahl.stops().getFirst().punkt();
        String dimension = auswahl.stops().getFirst().dimension();
        List<MainMenuNetwork.Choice> choices = new ArrayList<>();
        for (Abstellung abstellung : Abstellungen.all()) {
            long count = abstellung.gleise().stream()
                    .filter(gleis -> gleis.dimension().equals(dimension) && Gleisnetz.connect(dimension, gleis.start(), first) != null)
                    .count();
            if (count == 0) continue;
            choices.add(new MainMenuNetwork.Choice(abstellung.id(), abstellung.name(),
                    count + " von " + abstellung.gleise().size() + (abstellung.gleise().size() == 1 ? " Gleis" : " Gleisen")
                            + " erreichen \"" + auswahl.stops().getFirst().name() + "\""));
        }
        return choices;
    }

    // ---------------------------------------------------------------- Hilfen

    private static Gleispunkt snap(ServerPlayer player, Auswahl auswahl, Vector3f clicked) {
        Vec3 point = new Vec3(clicked.x(), clicked.y(), clicked.z());
        Gleispunkt punkt = point.distanceTo(player.getEyePosition()) > REACH ? null : Gleisnetz.snap(auswahl.dimension(), point, SNAP_DISTANCE);
        if (punkt == null) fail(player, "Hier liegt kein Gleis.");
        return punkt;
    }

    private static void update(ServerPlayer player, Auswahl auswahl) {
        ACTIVE.put(player.getUUID(), auswahl);
        send(player, auswahl);
    }

    /** Auswahl beenden und den Editor wieder öffnen. */
    private static void finish(ServerPlayer player, int editor) {
        ACTIVE.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, AuswahlState.INACTIVE);
        PacketDistributor.sendToPlayer(player, MainMenuNetwork.OpenEditor.of(editor));
    }

    private static void fail(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
    }

    private static void success(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GREEN), true);
    }

    /** Ein schon angelegtes Gleis und die Stücke der Segmente, die es belegt. */
    private record Belegt(Abstellung abstellung, Gleis gleis, Gleisnetz.Verbindung verbindung) {
        List<Gleisnetz.Abschnitt> sections() {
            return verbindung.sections();
        }

        String describe() {
            return "das Gleis \"" + gleis.name() + "\" (" + abstellung.name() + ")";
        }
    }

    /**
     * Alle Gleise aller Abstellungen in der Dimension: keine zwei Abstellungen teilen sich ein Stück Gleis.
     * Gleise, deren Spline es nicht mehr gibt, belegen nichts.
     */
    private static List<Belegt> belegt(String dimension) {
        List<Belegt> belegt = new ArrayList<>();
        for (Abstellung abstellung : Abstellungen.all()) {
            for (Gleis gleis : abstellung.gleise()) {
                if (!gleis.dimension().equals(dimension)) continue;
                Gleisnetz.Verbindung verbindung = Gleisnetz.connect(dimension, gleis.start(), gleis.end());
                if (verbindung != null) belegt.add(new Belegt(abstellung, gleis, verbindung));
            }
        }
        return belegt;
    }

    /** Splines in der Nähe und, je nach Modus, Gleise und Punkte an den Client. */
    private static void send(ServerPlayer player, Auswahl auswahl) {
        List<List<Vector3f>> network = Gleisnetz.lines(auswahl.dimension(), auswahl.center(), LINES_RADIUS);
        AuswahlState state = switch (auswahl) {
            case GleisAuswahl gleis -> {
                List<List<Vector3f>> own = new ArrayList<>();
                List<List<Vector3f>> others = new ArrayList<>();
                for (Belegt belegt : belegt(gleis.dimension())) {
                    (belegt.abstellung().id().equals(gleis.abstellung()) ? own : others).add(belegt.verbindung().line());
                }
                List<Marker> markers = gleis.start() == null ? List.of() : List.of(new Marker(vector(gleis.start()), "Anfang"));
                yield new AuswahlState(GLEIS, "Gleis \"" + gleis.name() + "\" setzen", network, own, others, markers);
            }
            case RoutenAuswahl route -> {
                List<Marker> markers = new ArrayList<>();
                for (int i = 0; i < route.stops().size(); i++) {
                    markers.add(new Marker(vector(route.stops().get(i).punkt()), (i + 1) + ". " + route.stops().get(i).name()));
                }
                yield new AuswahlState(HALTE, "Haltepunkte für " + route.name() + " (" + route.number() + ")", network, List.of(), List.of(), markers);
            }
        };
        PacketDistributor.sendToPlayer(player, state);
    }

    private static Vector3f vector(Gleispunkt punkt) {
        return new Vector3f((float) punkt.x(), (float) punkt.y(), (float) punkt.z());
    }

    /** Wer weit fliegt oder die Dimension wechselt, bekommt die Splines um seinen neuen Standort. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        Auswahl auswahl = ACTIVE.get(player.getUUID());
        if (auswahl == null) return;
        if (dimension(player).equals(auswahl.dimension()) && player.position().distanceTo(auswahl.center()) < REFRESH_DISTANCE) return;
        update(player, auswahl.moved(dimension(player), player.position()));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    private static String dimension(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }
}
