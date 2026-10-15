package de.niconagel.coremod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

import java.util.List;

/**
 * Halte der laufenden Fahrt beim Fahrer: an jedem Halt ein leuchtender Strahl wie beim Leuchtfeuer, darüber Name und
 * Entfernung. Der nächste Halt leuchtet gelb, die weiteren weiß; wo der Zug schon gestanden hat, ist der Strahl weg.
 */
@EventBusSubscriber(modid = CoremodClient.MOD_ID, value = Dist.CLIENT)
public final class Haltanzeige {

    private static final int BEAM_HEIGHT = 64;
    private static final int NEXT = 0xFFFFFF55;
    private static final int LATER = 0xFFFFFFFF;

    private static CoremodClient.FahrtState state = CoremodClient.FahrtState.NONE;

    private Haltanzeige() {
    }

    static void update(CoremodClient.FahrtState newState) {
        state = newState;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = CoremodClient.FahrtState.NONE;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (state.stops().isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long gameTime = minecraft.level.getGameTime();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-eye.x, -eye.y, -eye.z);
        int next = state.done().indexOf(false);
        for (int i = 0; i < state.stops().size(); i++) {
            if (state.done().get(i)) continue;
            Vector3f p = state.stops().get(i).position();

            // Der Strahl steht mittig im Block, deshalb um einen halben Block zurück
            poseStack.pushPose();
            poseStack.translate(p.x() - 0.5f, p.y(), p.z() - 0.5f);
            BeaconRenderer.renderBeaconBeam(poseStack, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1.0f, gameTime,
                    0, BEAM_HEIGHT, i == next ? NEXT : LATER, 0.2f, 0.25f);
            poseStack.popPose();

            // Mit der Entfernung größer, damit der Name auch von weitem lesbar bleibt
            double distance = eye.distanceTo(new Vec3(p));
            Component text = Component.literal(state.stops().get(i).label())
                    .withStyle(i == next ? ChatFormatting.YELLOW : ChatFormatting.WHITE)
                    .append(Component.literal(" · " + Zugdaten.number(distance) + " m").withStyle(ChatFormatting.GRAY));
            Weltauswahl.label(poseStack, buffers, camera, p, text, 4.0f, (float) Math.max(1, distance / 12));
        }
        poseStack.popPose();
        buffers.endBatch();
    }
}
