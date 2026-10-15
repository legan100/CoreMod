package de.niconagel.coremod.mainmenu;

import de.niconagel.coremod.Coremod;
import de.niconagel.coremod.api.Job;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/**
 * Pakete zwischen Server und Client fürs Hauptmenü. Die Gegenseite steckt in der Client-Mod (CoremodClient),
 * Kanäle, Version und Aufbau müssen dort gleich sein.
 * Hier sind die Kanäle Pflicht: Wer die Client-Mod nicht installiert hat, kann dem Server nicht beitreten.
 */
@EventBusSubscriber(modid = Coremod.MOD_ID)
public final class MainMenuNetwork {

    private MainMenuNetwork() {
    }

    /** Ein wählbarer Job, als Text: die Client-Mod kennt das Job-Enum nicht. */
    public record JobEntry(String id, String name, String description) {
        static final StreamCodec<ByteBuf, JobEntry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, JobEntry::id,
                ByteBufCodecs.STRING_UTF8, JobEntry::name,
                ByteBufCodecs.STRING_UTF8, JobEntry::description,
                JobEntry::new);

        static JobEntry of(Job job) {
            return new JobEntry(job.name(), job.getDisplayName(), job.getDescription());
        }
    }

    /** Server → Client: Hauptmenü öffnen bzw. die wählbaren Jobs aktualisieren. */
    public record OpenMenu(List<JobEntry> jobs) implements CustomPacketPayload {
        public static final Type<OpenMenu> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "open_main_menu"));
        public static final StreamCodec<ByteBuf, OpenMenu> CODEC = JobEntry.CODEC.apply(ByteBufCodecs.list()).map(OpenMenu::new, OpenMenu::jobs);

        @Override
        public Type<OpenMenu> type() {
            return TYPE;
        }
    }

    /** Client → Server: Job gewählt, {@link JobEntry#id()}. */
    public record Play(String job) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "play"));
        public static final StreamCodec<ByteBuf, Play> CODEC = ByteBufCodecs.STRING_UTF8.map(Play::new, Play::job);

        @Override
        public Type<Play> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");

        registrar.playToClient(OpenMenu.TYPE, OpenMenu.CODEC, (payload, context) -> {
            // kommt nur beim Client an
        });
        registrar.playToServer(Play.TYPE, Play.CODEC, (payload, context) -> {
            Job job = Job.byName(payload.job());
            if (job != null) MainMenu.play((ServerPlayer) context.player(), job);
        });
    }
}
