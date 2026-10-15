package de.niconagel.coremod.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Spieler, die einen erlaubten Bereich verlassen haben, zurückbringen: Stellwerk und Umkreis des eigenen Zugs. */
public final class Grenze {

    /** Weiter draußen hilft kein Stoß mehr, dann wird zurückgesetzt. */
    private static final double TELEPORT_DISTANCE = 3;
    private static final double PUSH = 0.6;

    private Grenze() {
    }

    /** Stoß Richtung {@code inside} (eine Stelle im Bereich), wie ein Knockback; ist der Spieler weiter weg, wird er dorthin gesetzt. */
    public static void pushBack(ServerPlayer player, Vec3 inside, String message) {
        Vec3 back = inside.subtract(player.position());
        if (back.length() > TELEPORT_DISTANCE) {
            player.teleportTo(inside.x, inside.y, inside.z);
        } else {
            Vec3 push = back.normalize().scale(PUSH);
            // Etwas anheben wie bei einem Knockback, außer er soll nach unten
            player.setDeltaMovement(push.x, back.y >= 0 ? Math.max(push.y, 0.25) : push.y, push.z);
            // Bewegung bestimmt der Client: so bekommt er sie geschickt
            player.hurtMarked = true;
        }
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
    }
}
