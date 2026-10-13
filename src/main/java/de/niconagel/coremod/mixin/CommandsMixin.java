package de.niconagel.coremod.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Ban-, Whitelist- und OP-Befehle gibt es nicht: Bans und Whitelist laufen über die CoreMod-API (WebApi),
 * Rechte über CoreMod (CommandPermissions).
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {

    // require: jedes Ziel muss gefunden werden, sonst bliebe ein Befehl unbemerkt aktiv
    @WrapWithCondition(method = "<init>", require = 8, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/server/commands/BanPlayerCommands;register(Lcom/mojang/brigadier/CommandDispatcher;)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/server/commands/BanIpCommands;register(Lcom/mojang/brigadier/CommandDispatcher;)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/server/commands/BanListCommands;register(Lcom/mojang/brigadier/CommandDispatcher;)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/server/commands/PardonCommand;register(Lcom/mojang/brigadier/CommandDispatcher;)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/server/commands/PardonIpCommand;register(Lcom/mojang/brigadier/CommandDispatcher;)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/server/commands/WhitelistCommand;register(Lcom/mojang/brigadier/CommandDispatcher;)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/server/commands/OpCommand;register(Lcom/mojang/brigadier/CommandDispatcher;)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/server/commands/DeOpCommands;register(Lcom/mojang/brigadier/CommandDispatcher;)V")
    })
    private boolean coremod$skipCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        return false;
    }
}
