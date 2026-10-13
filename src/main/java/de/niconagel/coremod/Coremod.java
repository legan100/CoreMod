package de.niconagel.coremod;

import com.mojang.logging.LogUtils;
import de.niconagel.coremod.commands.*;
import de.niconagel.coremod.listener.JoinListener;
import de.niconagel.coremod.utils.SetupManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(Coremod.MOD_ID)
public class Coremod {

    public static final String MOD_ID = "coremod";
    SetupManager setupManager;
    private static final Logger LOGGER = LogUtils.getLogger();

    public Coremod() {
        setupManager = new SetupManager();
        setupManager.onSetup();
        NeoForge.EVENT_BUS.register(new JoinListener());
        NeoForge.EVENT_BUS.register(Coremod.class);

        LOGGER.info("Coremod wird geladen...");
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        time.register(event.getDispatcher());
        Color.register(event.getDispatcher());
        Group.register(event.getDispatcher());
        FlySpeed.register(event.getDispatcher());
    }

}