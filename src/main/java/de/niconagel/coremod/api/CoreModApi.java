package de.niconagel.coremod.api;

import de.niconagel.coremod.permission.PermissionManager;

import java.util.Set;
import java.util.UUID;

/**
 * Öffentliche Schnittstelle für andere Mods.
 * Nur das Paket {@code de.niconagel.coremod.api} gilt als stabil, alles andere kann sich ändern.
 */
public final class CoreModApi {

    private CoreModApi() {
    }

    /** Gruppe des Spielers, {@link Group#GAST} wenn unbekannt. */
    public static Group getGroup(UUID player) {
        return PermissionManager.getGroup(player);
    }

    public static boolean hasPermission(UUID player, String permission) {
        return PermissionManager.has(player, permission);
    }

    /** Die in der permissions.json eingetragenen Rechte der Gruppe (inkl. Wildcards). */
    public static Set<String> getPermissions(Group group) {
        return PermissionManager.getPermissions(group);
    }
}
