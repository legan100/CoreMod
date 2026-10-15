package de.niconagel.coremod.fdl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Der Bereich umfasst beide Eckblöcke ganz, egal in welcher Reihenfolge die Ecken gesetzt wurden. */
class BereichTest {

    @Test
    void cornersInAnyOrderGiveTheSameArea() {
        assertEquals(Stellwerke.Bereich.of(0, 64, 0, 9, 67, 4), Stellwerke.Bereich.of(9, 67, 4, 0, 64, 0));
    }

    @Test
    void bothCornerBlocksAreInsideTheirWholeWidth() {
        Stellwerke.Bereich b = Stellwerke.Bereich.of(0, 64, 0, 9, 67, 4);
        assertTrue(b.contains(0.0, 64.0, 0.0));
        // Bis an die äußere Kante des letzten Blocks
        assertTrue(b.contains(9.99, 67.99, 4.99));
        assertFalse(b.contains(10.0, 64.0, 0.0));
        assertFalse(b.contains(-0.01, 64.0, 0.0));
        assertFalse(b.contains(5.0, 68.0, 2.0));
    }
}
