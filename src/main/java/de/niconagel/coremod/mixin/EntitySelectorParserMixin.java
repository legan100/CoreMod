package de.niconagel.coremod.mixin;

import de.niconagel.coremod.api.Permissions;
import de.niconagel.coremod.permission.PermissionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Selektoren (@a, @p, @e, …) für Spieler über das Recht statt über OP-Level 2. Gilt beim Ausführen und für Vorschläge. */
@Mixin(EntitySelectorParser.class)
public abstract class EntitySelectorParserMixin {

    @Inject(method = "allowSelectors(Ljava/lang/Object;)Z", at = @At("HEAD"), cancellable = true)
    private static void coremod$allowSelectors(Object provider, CallbackInfoReturnable<Boolean> cir) {
        if (provider instanceof CommandSourceStack source
                && source.getPlayer() instanceof ServerPlayer player && !player.isFakePlayer()) {
            cir.setReturnValue(PermissionManager.has(player.getUUID(), Permissions.SELECTOR));
        }
    }
}
