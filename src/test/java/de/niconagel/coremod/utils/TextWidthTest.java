package de.niconagel.coremod.utils;

import de.niconagel.coremod.api.Group;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TextWidthTest {

    @Test
    void measuresLikeMinecraft() {
        assertEquals(22, TextWidth.of("Gast"));      // G a s = 6, t = 4
        assertEquals(51, TextWidth.of("Developer")); // l = 3
        assertEquals(35, TextWidth.of("Builder"));   // i = 2, l = 3
    }

    /** Alle Rang-Prefixe müssen nach dem Auffüllen exakt gleich breit sein, sonst stehen die "|" nicht untereinander. */
    @Test
    void allGroupsPadToSameWidth() {
        List<String> names = Arrays.stream(Group.values()).map(Group::getDisplayName).toList();
        int target = TextWidth.commonWidth(names);

        for (String name : names) {
            int[] padding = TextWidth.padding(target - TextWidth.of(name));
            assertNotNull(padding, name);
            assertEquals(target, TextWidth.of(name) + 4 * padding[0] + 5 * padding[1], name);
        }
    }
}
