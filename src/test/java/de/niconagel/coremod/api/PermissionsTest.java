package de.niconagel.coremod.api;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PermissionsTest {

    @Test
    void wildcards() {
        assertTrue(Permissions.matches(Set.of("*"), Permissions.FLY));
        assertTrue(Permissions.matches(Set.of(Permissions.FLY), Permissions.FLY));
        assertTrue(Permissions.matches(Set.of("minecraft.*"), Permissions.BLOCK_BREAK));
        assertTrue(Permissions.matches(Set.of("minecraft.block.*"), Permissions.BLOCK_BREAK));

        assertFalse(Permissions.matches(Set.of(), Permissions.FLY));
        assertFalse(Permissions.matches(Set.of("minecraft.block.*"), Permissions.FLY));
        assertFalse(Permissions.matches(Set.of("minecraft.block"), Permissions.BLOCK_BREAK));
        assertFalse(Permissions.matches(Set.of("coremod.*"), Permissions.MOVE));
    }

    @Test
    void groupNames() {
        assertEquals(Group.TF_AZUBI, Group.byName("TF-Azubi"));
        assertEquals(Group.TF_AZUBI, Group.byName("tf_azubi"));
        assertEquals(Group.FDL, Group.byName("fdl"));
        // Die API liefert "Developer", angezeigt wird "Dev"; erkannt werden muss weiter der volle Name
        assertEquals(Group.DEVELOPER, Group.byName("Developer"));
        assertEquals("Dev", Group.DEVELOPER.getPrefix());
        assertNull(Group.byName("Spieler"));
        assertNull(Group.byName(null));
    }
}
