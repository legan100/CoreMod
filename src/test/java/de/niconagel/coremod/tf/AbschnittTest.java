package de.niconagel.coremod.tf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Neue Gleise dürfen bestehende nicht überlappen, aber direkt daran anschließen. */
class AbschnittTest {

    private static final Gleisnetz.Abschnitt EXISTING = new Gleisnetz.Abschnitt("s1", 10, 50);

    private static Abstellungen.Gleispunkt at(String segment, double distance) {
        return new Abstellungen.Gleispunkt(segment, distance, true, 0, 0, 0);
    }

    @Test
    void pointsInsideAnExistingGleisAreTaken() {
        assertTrue(EXISTING.contains(at("s1", 30)));
        // Enden und andere Segmente sind frei
        assertFalse(EXISTING.contains(at("s1", 10)));
        assertFalse(EXISTING.contains(at("s1", 50)));
        assertFalse(EXISTING.contains(at("s2", 30)));
    }

    @Test
    void pathsMayTouchButNotOverlap() {
        assertTrue(EXISTING.overlaps(new Gleisnetz.Abschnitt("s1", 40, 80)));
        // Über das ganze bestehende Gleis hinweg, obwohl Anfang und Ende außerhalb liegen
        assertTrue(EXISTING.overlaps(new Gleisnetz.Abschnitt("s1", 0, 100)));
        assertFalse(EXISTING.overlaps(new Gleisnetz.Abschnitt("s1", 50, 90)));
        assertFalse(EXISTING.overlaps(new Gleisnetz.Abschnitt("s2", 0, 100)));
    }
}
