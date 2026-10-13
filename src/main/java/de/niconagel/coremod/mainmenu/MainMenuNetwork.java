package de.niconagel.coremod.mainmenu;

import de.niconagel.coremod.Coremod;
import de.niconagel.coremod.client.MainMenuClient;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pakete zwischen Server und Client fürs Hauptmenü.
 * Die Kanäle sind Pflicht: Wer CoreMod nicht installiert hat, kann dem Server nicht beitreten.
 */
@EventBusSubscriber(modid = Coremod.MOD_ID)
public final class MainMenuNetwork {

    private MainMenuNetwork() {
    }

    /** Server → Client: Hauptmenü öffnen. */
    public record OpenMenu() implements CustomPacketPayload {
        public static final Type<OpenMenu> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "open_main_menu"));
        public static final StreamCodec<ByteBuf, OpenMenu> CODEC = StreamCodec.unit(new OpenMenu());

        @Override
        public Type<OpenMenu> type() {
            return TYPE;
        }
    }

    /** Client → Server: "Spielen" gedrückt. */
    public record Play() implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "play"));
        public static final StreamCodec<ByteBuf, Play> CODEC = StreamCodec.unit(new Play());

        @Override
        public Type<Play> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // Nur statische Aufrufe ohne Client-Typen in der Signatur: so wird MainMenuClient auf dem Server nie geladen
        registrar.playToClient(OpenMenu.TYPE, OpenMenu.CODEC, (payload, context) -> MainMenuClient.open());
        registrar.playToServer(Play.TYPE, Play.CODEC, (payload, context) -> MainMenu.play((ServerPlayer) context.player()));
    }
}
