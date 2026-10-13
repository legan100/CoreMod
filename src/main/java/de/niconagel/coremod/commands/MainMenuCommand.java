package de.niconagel.coremod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niconagel.coremod.api.Permissions;
import de.niconagel.coremod.mainmenu.MainMenu;
import de.niconagel.coremod.permission.PermissionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;

/**
 * /mainmenu setview                   → View = eigene Augenposition und Blickrichtung
 * /mainmenu setview &lt;x y z&gt; &lt;yaw pitch&gt; → View = angegebene Werte (Dimension = eigene)
 */
public final class MainMenuCommand {

    private MainMenuCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("mainmenu")
                        .requires(PermissionManager.require(Permissions.COMMAND_MAINMENU))
                        .then(Commands.literal("setview")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    return setView(context.getSource(), player.getEyePosition(), player.getYRot(), player.getXRot());
                                })
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .then(Commands.argument("rotation", RotationArgument.rotation())
                                                .executes(context -> {
                                                    Vec2 rotation = RotationArgument.getRotation(context, "rotation").getRotation(context.getSource());
                                                    // Vec2: y = Yaw, x = Pitch (wie bei /tp)
                                                    return setView(context.getSource(), Vec3Argument.getVec3(context, "pos"), rotation.y, rotation.x);
                                                }))))
        );
    }

    private static int setView(CommandSourceStack source, Vec3 pos, float yaw, float pitch) throws CommandSyntaxException {
        String dimension = source.getLevel().dimension().location().toString();

        try {
            MainMenu.setView(new MainMenu.View(dimension, pos.x, pos.y, pos.z, yaw, pitch));
        } catch (IOException e) {
            source.sendFailure(Component.literal("View konnte nicht gespeichert werden: " + e.getMessage()));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(String.format(
                "Hauptmenü-View gesetzt: %s %.1f %.1f %.1f (Yaw %.1f, Pitch %.1f)", dimension, pos.x, pos.y, pos.z, yaw, pitch)), true);
        return 1;
    }
}
