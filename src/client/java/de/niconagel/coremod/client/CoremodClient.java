package de.niconagel.coremod.client;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/**
 * Client-Teil von CoreMod: nur das Hauptmenü. Es öffnet sich ausschließlich auf Anweisung des Servers,
 * auf Servern ohne CoreMod (und im Einzelspieler) macht die Mod also nichts.
 */
@Mod(value = CoremodClient.MOD_ID, dist = Dist.CLIENT)
public final class CoremodClient {

    public static final String MOD_ID = "coremod_client";

    public CoremodClient(IEventBus modBus) {
        modBus.addListener(CoremodClient::registerPayloads);
    }

    // Kanäle, Version und Aufbau müssen zu MainMenuNetwork in der Server-Mod passen

    /** Ein wählbarer Job. {@code id} geht beim Wählen an den Server zurück. */
    public record JobEntry(String id, String name, String description) {
        static final StreamCodec<ByteBuf, JobEntry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, JobEntry::id,
                ByteBufCodecs.STRING_UTF8, JobEntry::name,
                ByteBufCodecs.STRING_UTF8, JobEntry::description,
                JobEntry::new);
    }

    /** Server → Client: Hauptmenü öffnen bzw. die wählbaren Jobs aktualisieren. */
    public record OpenMenu(List<JobEntry> jobs) implements CustomPacketPayload {
        public static final Type<OpenMenu> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "open_main_menu"));
        public static final StreamCodec<ByteBuf, OpenMenu> CODEC = JobEntry.CODEC.apply(ByteBufCodecs.list()).map(OpenMenu::new, OpenMenu::jobs);

        @Override
        public Type<OpenMenu> type() {
            return TYPE;
        }
    }

    /** Client → Server: Job gewählt. */
    public record Play(String job) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "play"));
        public static final StreamCodec<ByteBuf, Play> CODEC = ByteBufCodecs.STRING_UTF8.map(Play::new, Play::job);

        @Override
        public Type<Play> type() {
            return TYPE;
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional: Server ohne CoreMod lassen sich trotzdem betreten
        PayloadRegistrar registrar = event.registrar("2").optional();
        registrar.playToClient(OpenMenu.TYPE, OpenMenu.CODEC, (payload, context) -> MainMenuClient.open(payload.jobs()));
        registrar.playToServer(Play.TYPE, Play.CODEC, (payload, context) -> {
            // kommt nur beim Server an
        });
    }
}
