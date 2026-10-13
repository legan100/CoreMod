package de.niconagel.coremod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.niconagel.coremod.api.Permissions;
import de.niconagel.coremod.permission.PermissionManager;
import de.niconagel.coremod.utils.InternalMethods;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class time {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("uhrzeit")
                        .requires(PermissionManager.require(Permissions.COMMAND_UHRZEIT))
                        .executes(context -> showTime(context.getSource()))
        );
    }

    private static int showTime(CommandSourceStack source) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return 0;
        }

        player.sendSystemMessage(
                Component.literal(
                        "Aktuelle Uhrzeit: " + InternalMethods.getTime()
                )
        );

        return 1;
    }
}
