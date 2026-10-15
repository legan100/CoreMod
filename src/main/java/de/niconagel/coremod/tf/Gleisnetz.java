package de.niconagel.coremod.tf;

import com.blockwelt.modernrailroading.core.math.Vector3;
import com.blockwelt.modernrailroading.core.rail.TrackPosition;
import com.blockwelt.modernrailroading.platform.neoforge_1_21_1.common.ForgeTrains;
import com.blockwelt.modernrailroading.track.geometry.TrackGeometry;
import com.blockwelt.modernrailroading.track.geometry.TrackGeometrySample;
import com.blockwelt.modernrailroading.track.routing.TrackHeading;
import com.blockwelt.modernrailroading.track.routing.TrackRoute;
import com.blockwelt.modernrailroading.track.routing.TrackView;
import de.niconagel.coremod.tf.Abstellungen.Gleispunkt;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Das Gleisnetz von ModernRailroading: Punkte auf die Spline setzen, zwei Punkte zu einem Gleis verbinden, Splines zum
 * Anzeigen. Neben {@link Fahrzeugkatalog} die einzige Stelle in CoreMod, die MR anspricht. Nur auf dem Server-Thread.
 */
public final class Gleisnetz {

    private Gleisnetz() {
    }

    /**
     * Zwei Punkte, verbunden entlang des Gleises.
     *
     * @param start    schaut in Richtung end
     * @param line     der Weg als Linie, zum Anzeigen
     * @param sections die Stücke der Segmente, die der Weg belegt
     */
    public record Verbindung(Gleispunkt start, Gleispunkt end, double length, List<Vector3f> line, List<Abschnitt> sections) {
    }

    /** Stück eines Segments, {@code from} ≤ {@code to} in Metern ab dessen Startknoten. */
    public record Abschnitt(String segment, double from, double to) {

        /** Echte Überlappung; nur am Ende berühren zählt nicht, so können Gleise direkt hintereinander liegen. */
        public boolean overlaps(Abschnitt other) {
            return segment.equals(other.segment) && from < other.to - TOUCH && other.from < to - TOUCH;
        }

        /** Liegt der Punkt auf dem Abschnitt, Endpunkte ausgenommen. */
        public boolean contains(Gleispunkt punkt) {
            return segment.equals(punkt.segment()) && punkt.distance() > from + TOUCH && punkt.distance() < to - TOUCH;
        }
    }

    /** Spielraum in Metern, ab dem Gleise als überlappend gelten. */
    private static final double TOUCH = 0.1;

    /** @return der Punkt auf der Spline, der {@code point} am nächsten liegt, oder null, wenn keine Spline so nah ist */
    public static Gleispunkt snap(String dimension, Vec3 point, double maxDistance) {
        TrackView view = ForgeTrains.trackView(dimension);
        TrackPosition position = view.nearest(new Vector3(point.x, point.y, point.z), maxDistance);
        return position == null ? null : punkt(view, position.segmentId(), position.distanceAlongSegmentMeters(), true);
    }

    /** @return false, wenn das Segment des Punkts nicht mehr existiert (Gleis umgebaut oder abgerissen) */
    public static boolean exists(String dimension, Gleispunkt punkt) {
        return ForgeTrains.trackView(dimension).network().findSegment(punkt.segment()) != null;
    }

    /**
     * Kürzester Weg von a nach b entlang des Gleises, in beide Richtungen und über Weichen hinweg (egal wie sie stehen),
     * aber ohne zu wenden.
     *
     * @return null, wenn es keinen Weg gibt oder ein Punkt nicht mehr auf dem Netz liegt
     */
    public static Verbindung connect(String dimension, Gleispunkt a, Gleispunkt b) {
        TrackView view = ForgeTrains.trackView(dimension);
        if (view.network().findSegment(a.segment()) == null || view.network().findSegment(b.segment()) == null) return null;

        double aLength = view.geometry(a.segment()).length();
        double bLength = view.geometry(b.segment()).length();
        List<TrackHeading> bestWay = null;
        double best = Double.MAX_VALUE;

        for (boolean aForward : new boolean[]{true, false}) {
            TrackHeading from = new TrackHeading(a.segment(), aForward);
            // Gleich auf demselben Segment
            if (a.segment().equals(b.segment())) {
                double along = aForward ? b.distance() - a.distance() : a.distance() - b.distance();
                if (along >= 0 && along < best) {
                    best = along;
                    bestWay = List.of(from);
                }
                continue;
            }
            for (boolean bForward : new boolean[]{true, false}) {
                Optional<TrackRoute> route = view.router().findRoute(from, new TrackHeading(b.segment(), bForward));
                if (route.isEmpty()) continue;
                // lengthMeters zählt alle Segmente nach dem ersten ganz, vom ersten und letzten gehört nur ein Teil dazu
                double length = (aForward ? aLength - a.distance() : a.distance())
                        + route.get().lengthMeters() - bLength
                        + (bForward ? b.distance() : bLength - b.distance());
                if (length < best) {
                    best = length;
                    bestWay = route.get().headings();
                }
            }
        }
        if (bestWay == null) return null;

        List<Vector3f> line = new ArrayList<>();
        List<Abschnitt> sections = new ArrayList<>();
        for (int i = 0; i < bestWay.size(); i++) {
            TrackHeading heading = bestWay.get(i);
            TrackGeometry geometry = view.geometry(heading.segmentId());
            // Stück des Segments in Fahrtrichtung: am ersten ab a, am letzten bis b, dazwischen ganz
            double from = i == 0 ? a.distance() : heading.forward() ? 0 : geometry.length();
            double to = i == bestWay.size() - 1 ? b.distance() : heading.forward() ? geometry.length() : 0;
            addLine(line, geometry, from, to);
            sections.add(new Abschnitt(heading.segmentId(), Math.min(from, to), Math.max(from, to)));
        }

        boolean startForward = bestWay.getFirst().forward();
        boolean endForward = bestWay.getLast().forward();
        return new Verbindung(punkt(view, a.segment(), a.distance(), startForward), punkt(view, b.segment(), b.distance(), endForward),
                best, line, sections);
    }

    /** Alle Splines, die {@code center} näher als {@code radius} kommen, je Segment eine Linie. */
    public static List<List<Vector3f>> lines(String dimension, Vec3 center, double radius) {
        TrackView view = ForgeTrains.trackView(dimension);
        List<List<Vector3f>> lines = new ArrayList<>();
        Vector3 c = new Vector3(center.x, center.y, center.z);
        view.network().forEachSegment(segment -> {
            List<TrackGeometrySample> samples = view.geometry(segment.id()).samples();
            if (samples.stream().noneMatch(sample -> sample.position().distanceTo(c) < radius)) return;
            lines.add(samples.stream().map(sample -> vector(sample.position())).toList());
        });
        return lines;
    }

    private static void addLine(List<Vector3f> line, TrackGeometry geometry, double from, double to) {
        line.add(vector(geometry.sampleAtArcLength(from).position()));
        List<TrackGeometrySample> samples = new ArrayList<>(geometry.samples());
        if (to < from) samples = samples.reversed();
        for (TrackGeometrySample sample : samples) {
            double arc = sample.arcLengthMeters();
            if (arc > Math.min(from, to) && arc < Math.max(from, to)) line.add(vector(sample.position()));
        }
        line.add(vector(geometry.sampleAtArcLength(to).position()));
    }

    private static Gleispunkt punkt(TrackView view, String segment, double distance, boolean facing) {
        Vector3 position = view.geometry(segment).sampleAtArcLength(distance).position();
        return new Gleispunkt(segment, distance, facing, position.x(), position.y(), position.z());
    }

    private static Vector3f vector(Vector3 v) {
        return new Vector3f((float) v.x(), (float) v.y(), (float) v.z());
    }
}
