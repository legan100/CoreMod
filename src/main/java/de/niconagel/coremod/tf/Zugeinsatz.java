package de.niconagel.coremod.tf;

import com.blockwelt.modernrailroading.core.math.Vector3;
import com.blockwelt.modernrailroading.core.rail.TrackPosition;
import com.blockwelt.modernrailroading.platform.neoforge_1_21_1.ForgePlatformBootstrap;
import com.blockwelt.modernrailroading.platform.neoforge_1_21_1.common.ForgeTrains;
import com.blockwelt.modernrailroading.rollingstock.RollingStockModule;
import com.blockwelt.modernrailroading.rollingstock.model.RollingStockDefinition;
import com.blockwelt.modernrailroading.rollingstock.model.VehicleShape;
import com.blockwelt.modernrailroading.rollingstock.vehicle.RailVehicle;
import com.blockwelt.modernrailroading.rollingstock.vehicle.Train;
import com.blockwelt.modernrailroading.rollingstock.vehicle.TrainSimulation;
import com.blockwelt.modernrailroading.track.geometry.TrackGeometrySample;
import com.blockwelt.modernrailroading.track.routing.TrackView;
import de.niconagel.coremod.tf.Abstellungen.Abstellung;
import de.niconagel.coremod.tf.Abstellungen.Gleis;
import de.niconagel.coremod.tf.Abstellungen.Gleispunkt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Setzt eine Garnitur als MR-Zug auf ein Gleis einer Abstellung: frei, lang genug und mit Weg zum ersten Halt, die Lok
 * vorne in Richtung Halt. Neben {@link Fahrzeugkatalog} und {@link Gleisnetz} die einzige Stelle in CoreMod, die MR anspricht.
 */
public final class Zugeinsatz {

    /** Abstand der Zugspitze zum Ende des Gleises. */
    private static final double MARGIN = 2;
    /**
     * ponytail: belegt heißt, eine Fahrzeugmitte steht auf dem Gleis oder so nah davor; ein langes Fahrzeug knapp hinter
     * einer Segmentgrenze zählt nicht, dafür müsste man wie MRs attachBehind entlang der Spline prüfen
     */
    private static final double OCCUPIED_MARGIN = 10;

    private Zugeinsatz() {
    }

    /** Darunter (m/s) steht ein Zug. */
    private static final double STOPPED = 0.05;
    /** So weit muss ein Zug gefahren sein, bevor er an einem Halt ankommen kann, sonst zählte schon das Einsetzen. */
    private static final double MIN_DRIVEN = 10;
    /** Spielraum um ein Fahrzeug, in dem ein Halt als erreicht gilt. */
    private static final double AT_STOP = 5;

    /**
     * Wo der Zug steht.
     *
     * @param train     MR-ID des Zugs
     * @param front     Position der Lok (Mitte)
     * @param direction Fahrtrichtung, waagrecht
     */
    public record Eingesetzt(UUID train, Gleis gleis, Vec3 front, Vec3 direction) {
    }

    /**
     * Steht der Zug, nachdem er gefahren ist, mit einem seiner Fahrzeuge am Halt?
     * ponytail: "am Halt" = ein Fahrzeug steht höchstens seine halbe Länge plus {@link #AT_STOP} davon entfernt, egal welches
     */
    public static boolean standsAt(ServerLevel level, UUID trainId, Gleispunkt stop) {
        Train train = ForgeTrains.get(level).simulation().train(trainId);
        if (train == null || Math.abs(train.speed()) > STOPPED) return false;

        TrackView view = ForgeTrains.trackView(level.dimension().location().toString());
        RollingStockModule module = ForgePlatformBootstrap.bootstrap().rollingStockModule();
        Vector3 point = new Vector3(stop.x(), stop.y(), stop.z());
        boolean driven = false;
        boolean atStop = false;
        for (RailVehicle vehicle : train.vehicles()) {
            driven |= Math.abs(vehicle.odometer()) > MIN_DRIVEN;
            if (vehicle.trackPosition() == null || !view.contains(vehicle.trackPosition())) continue;
            VehicleShape shape = module.shape(vehicle.definition());
            double half = (shape.frontReach(vehicle.definition()) + shape.rearReach(vehicle.definition())) / 2;
            atStop |= view.sample(vehicle.trackPosition()).position().distanceTo(point) < half + AT_STOP;
        }
        return driven && atStop;
    }

    /** Nimmt den ganzen Zug vom Gleis. */
    public static void remove(ServerLevel level, UUID trainId) {
        ForgeTrains trains = ForgeTrains.get(level);
        trains.simulation().removeTrain(trainId);
        trains.setDirty();
    }

    /**
     * @param ziel erster Halt der Route; das Gleis muss ihn über die Schiene erreichen
     * @throws IllegalStateException mit einem Grund für den Spieler, wenn kein Gleis passt
     */
    public static Eingesetzt einsetzen(ServerLevel level, Abstellung abstellung, List<Garnituren.Fahrzeug> vehicles,
                                       Gleispunkt ziel, String zugnummer) {
        RollingStockModule module = ForgePlatformBootstrap.bootstrap().rollingStockModule();
        List<RollingStockDefinition> definitions = new ArrayList<>();
        double trainLength = 0;
        for (Garnituren.Fahrzeug vehicle : vehicles) {
            RollingStockDefinition definition = module.catalog().find(vehicle.type());
            if (definition == null) throw new IllegalStateException("Fahrzeug " + vehicle.type() + " fehlt in ModernRailroading.");
            definitions.add(definition);
            VehicleShape shape = module.shape(definition);
            trainLength += shape.frontReach(definition) + shape.rearReach(definition);
        }

        String dimension = level.dimension().location().toString();
        TrackView view = ForgeTrains.trackView(dimension);
        ForgeTrains trains = ForgeTrains.get(level);
        // Der Grund des besten Kandidaten: frei, aber zu kurz sagt mehr als "kein Weg"
        String reason = "Die Abstellung \"" + abstellung.name() + "\" hat kein Gleis in dieser Dimension.";

        for (Gleis gleis : abstellung.gleise()) {
            if (!gleis.dimension().equals(dimension)) continue;
            Gleisnetz.Verbindung toStop = Gleisnetz.connect(dimension, gleis.start(), ziel);
            Gleisnetz.Verbindung track = Gleisnetz.connect(dimension, gleis.start(), gleis.end());
            if (toStop == null || track == null) {
                reason = "Kein Gleis in \"" + abstellung.name() + "\" erreicht den ersten Halt.";
                continue;
            }
            if (occupied(trains.simulation(), track.sections())) {
                reason = "Alle passenden Gleise in \"" + abstellung.name() + "\" sind belegt.";
                continue;
            }
            if (gleis.length() < trainLength + 2 * MARGIN) {
                reason = String.format(Locale.GERMANY, "Die Garnitur (%.0f m) passt auf kein freies Gleis in \"%s\".", trainLength, abstellung.name());
                continue;
            }

            // Zugspitze an dem Ende des Gleises, über das es zum Halt geht, mit Blick in diese Richtung
            boolean throughGleis = toStop.start().facing() == gleis.start().facing();
            TrackPosition head = throughGleis
                    ? new TrackPosition(dimension, gleis.end().segment(), gleis.end().distance(), gleis.end().facing())
                    : new TrackPosition(dimension, gleis.start().segment(), gleis.start().distance(), !gleis.start().facing());

            Train train = place(trains.simulation(), view, module, definitions, vehicles, head);
            if (train == null) {
                reason = "Die Garnitur passt nicht auf das Gleis \"" + gleis.name() + "\" (Gleisende oder Fahrzeug im Weg).";
                continue;
            }
            train.setNumber(zugnummer);
            trains.setDirty();

            TrackGeometrySample sample = view.sample(train.vehicles().getFirst().trackPosition());
            Vector3 p = sample.position();
            Vector3 t = sample.tangent();
            return new Eingesetzt(train.id(), gleis, new Vec3(p.x(), p.y(), p.z()), new Vec3(t.x(), 0, t.z()).normalize());
        }
        throw new IllegalStateException(reason);
    }

    /**
     * Erstes Fahrzeug mit der vorderen Kupplung {@link #MARGIN} hinter {@code head}, die übrigen per MR dahinter gekuppelt.
     * Umgedrehte Fahrzeuge erst danach drehen: ihre Kupplungen bleiben, wo sie sind, nur die Mitte rückt.
     *
     * @return null, wenn ein Fahrzeug nicht dahinter passt; dann steht nichts auf dem Gleis
     */
    private static Train place(TrainSimulation simulation, TrackView view, RollingStockModule module,
                               List<RollingStockDefinition> definitions, List<Garnituren.Fahrzeug> vehicles, TrackPosition head) {
        RollingStockDefinition first = definitions.getFirst();
        TrackPosition middle = view.router().advance(head, -(MARGIN + module.shape(first).frontReach(first))).position();
        Train train = simulation.place(first, middle);

        for (RollingStockDefinition definition : definitions.subList(1, definitions.size())) {
            Train single = simulation.place(definition, middle);
            if (!simulation.attachBehind(train, single, view)) {
                simulation.removeTrain(single.id());
                simulation.removeTrain(train.id());
                return null;
            }
        }

        for (int i = 0; i < vehicles.size(); i++) {
            if (!vehicles.get(i).reversed()) continue;
            RailVehicle vehicle = train.vehicles().get(i);
            VehicleShape shape = module.shape(vehicle.definition());
            // Die Position schaut zur Zugspitze; umgedreht liegt dort die hintere Kupplung des Fahrzeugs
            TrackPosition moved = view.router().advance(vehicle.trackPosition(),
                    shape.frontReach(vehicle.definition()) - shape.rearReach(vehicle.definition())).position();
            vehicle.applyTrackPosition(new TrackPosition(moved.scopeId(), moved.segmentId(), moved.distanceAlongSegmentMeters(), !moved.facingForward()));
            vehicle.setReversed(true);
        }
        return train;
    }

    private static boolean occupied(TrainSimulation simulation, List<Gleisnetz.Abschnitt> sections) {
        for (Train train : simulation.trains()) {
            for (RailVehicle vehicle : train.vehicles()) {
                TrackPosition position = vehicle.trackPosition();
                if (position == null) continue;
                for (Gleisnetz.Abschnitt section : sections) {
                    if (section.segment().equals(position.segmentId())
                            && position.distanceAlongSegmentMeters() > section.from() - OCCUPIED_MARGIN
                            && position.distanceAlongSegmentMeters() < section.to() + OCCUPIED_MARGIN) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
