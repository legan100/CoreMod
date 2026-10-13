package de.niconagel.coremod.api;

import java.util.Set;

/**
 * Alle Rechte, die CoreMod kennt und durchsetzt.
 * Andere Mods können eigene Rechte frei benennen (z.B. "meinemod.irgendwas") und mit
 * {@link CoreModApi#hasPermission} abfragen.
 * <p>
 * Wildcards in der permissions.json: "*" = alles, "minecraft.*" = alles unter "minecraft.".
 */
public final class Permissions {

    private Permissions() {
    }

    public static final String ALL = "*";

    // Bewegung & Kommunikation
    public static final String MOVE = "minecraft.move";
    /** Fliegen im Survival/Adventure. Creative und Spectator fliegen immer, die regelt GAMEMODE_*. */
    public static final String FLY = "minecraft.fly";
    public static final String CHAT = "minecraft.chat";

    // Welt verändern
    /** Abbauen, auch Feuerpfeile, TNT-Explosionen und Feld zertrampeln. */
    public static final String BLOCK_BREAK = "minecraft.block.break";
    /** Setzen und jede Block-Veränderung mit Items (Eimer, Feuerzeug, Knochenmehl, Axt, Hacke, …). */
    public static final String BLOCK_PLACE = "minecraft.block.place";
    /** Loren, Boote, Rüstungsständer, Rahmen, Bilder, Spawn-Eier, Endkristalle, Leinen-Knoten. */
    public static final String ENTITY_PLACE = "minecraft.entity.place";

    // Interaktion
    /** Türen, Knöpfe, Hebel, Betten, Schilder, … */
    public static final String INTERACT_BLOCK = "minecraft.interact.block";
    public static final String INTERACT_ENTITY = "minecraft.interact.entity";
    /** Jede GUI mit Slots: Kisten, Öfen, Werkbank, Villager-Handel, Esel-Inventar, … */
    public static final String CONTAINER = "minecraft.container";
    public static final String MOUNT = "minecraft.mount";

    // Items
    public static final String ITEM_USE = "minecraft.item.use";
    public static final String ITEM_DROP = "minecraft.item.drop";
    public static final String ITEM_PICKUP = "minecraft.item.pickup";

    // Kampf (Nahkampf, Schwungangriff, Projektile, Tränke, TNT, …)
    public static final String ATTACK_PLAYER = "minecraft.attack.player";
    public static final String ATTACK_ENTITY = "minecraft.attack.entity";

    // Spielmodi
    public static final String GAMEMODE_SURVIVAL = "minecraft.gamemode.survival";
    public static final String GAMEMODE_ADVENTURE = "minecraft.gamemode.adventure";
    public static final String GAMEMODE_CREATIVE = "minecraft.gamemode.creative";
    public static final String GAMEMODE_SPECTATOR = "minecraft.gamemode.spectator";

    // CoreMod-Befehle (alle Befehle folgen dem Schema "command.<name>", siehe command())
    public static final String COMMAND_UHRZEIT = command("uhrzeit");
    public static final String COMMAND_COLOR = command("color");
    public static final String COMMAND_GROUP = command("group");
    public static final String COMMAND_FLYSPEED = command("flyspeed");
    /** /flyspeed für andere Spieler. */
    public static final String COMMAND_FLYSPEED_OTHERS = command("flyspeed.others");

    /** Recht für einen beliebigen Befehl, auch Vanilla und andere Mods: "gamemode" → "command.gamemode". */
    public static String command(String name) {
        return "command." + name;
    }

    /** Prüft, ob {@code granted} das Recht enthält, direkt oder über eine Wildcard. */
    public static boolean matches(Set<String> granted, String permission) {
        if (granted.contains(ALL) || granted.contains(permission)) {
            return true;
        }

        // "minecraft.block.break" → prüft "minecraft.block.*", dann "minecraft.*"
        for (int i = permission.lastIndexOf('.'); i > 0; i = permission.lastIndexOf('.', i - 1)) {
            if (granted.contains(permission.substring(0, i) + ".*")) {
                return true;
            }
        }

        return false;
    }
}
