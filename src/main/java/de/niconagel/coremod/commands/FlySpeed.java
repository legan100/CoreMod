package de.niconagel.coremod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import de.niconagel.coremod.api.Permissions;
import de.niconagel.coremod.permission.PermissionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /flyspeed &lt;0.1–10&gt; [Spieler]: 1 = normal, 2 = doppelt so schnell, …
 * Die Geschwindigkeit wird mit dem Spieler gespeichert und bleibt nach einem Relog erhalten.
 */
public final class FlySpeed {

    /** Vanilla-Standard für Abilities.flyingSpeed, entspricht /flyspeed 1. */
    private static final float DEFAULT_SPEED = 0.05f;

    private FlySpeed() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("flyspeed")
                        .requires(PermissionManager.require(Permissions.COMMAND_FLYSPEED))
                        .then(Commands.argument("speed", FloatArgumentType.floatArg(0.1f, 10f))
                                .executes(context -> setSpeed(
                                        context.getSource(),
                                        context.getSource().getPlayerOrException(),
                                        FloatArgumentType.getFloat(context, "speed")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(PermissionManager.require(Permissions.COMMAND_FLYSPEED_OTHERS))
                                        .executes(context -> setSpeed(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                FloatArgumentType.getFloat(context, "speed")))))
        );
    }

    private static int setSpeed(CommandSourceStack source, ServerPlayer target, float speed) {
        target.getAbilities().setFlyingSpeed(DEFAULT_SPEED * speed);
        target.onUpdateAbilities();

        target.displayClientMessage(Component.literal("Fluggeschwindigkeit: " + speed), true);
        if (source.getPlayer() != target) {
            source.sendSuccess(() -> Component.literal(
                    "Fluggeschwindigkeit von " + target.getGameProfile().getName() + ": " + speed), false);
        }
        return 1;
    }
}
