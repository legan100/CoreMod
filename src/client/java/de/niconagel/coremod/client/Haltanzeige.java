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

/**
 * Nächster Halt der laufenden Fahrt beim Fahrer: ein leuchtender Strahl wie beim Leuchtfeuer, darüber Name und Entfernung.
 * Immer nur der nächste; hat der Zug dort gestanden, wandert die Anzeige zum folgenden Halt.
 */
@EventBusSubscriber(modid = CoremodClient.MOD_ID, value = Dist.CLIENT)
public final class Haltanzeige {

    private static final int BEAM_HEIGHT = 64;
    private static final int COLOR = 0xFFFFFF55;

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

    /** Der nächste Halt, oder null: keine Fahrt oder alle Halte erledigt. */
    private static CoremodClient.Marker next() {
        int next = state.done().indexOf(false);
        return next < 0 ? null : state.stops().get(next);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        CoremodClient.Marker next = next();
        Minecraft minecraft = Minecraft.getInstance();
        if (next == null || minecraft.level == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Vec3 eye = event.getCamera().getPosition();
        Vector3f p = next.position();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        poseStack.pushPose();
        // Der Strahl steht mittig im Block, deshalb um einen halben Block zurück
        poseStack.translate(p.x() - eye.x - 0.5, p.y() - eye.y, p.z() - eye.z - 0.5);
        BeaconRenderer.renderBeaconBeam(poseStack, buffers, BeaconRenderer.BEAM_LOCATION,
                event.getPartialTick().getGameTimeDeltaPartialTick(false), 1.0f, minecraft.level.getGameTime(),
                0, BEAM_HEIGHT, COLOR, 0.2f, 0.25f);
        poseStack.popPose();
        buffers.endBatch();
    }

    /** Name und Entfernung erst ganz am Ende, siehe {@link Weltauswahl#labelPose}. */
    @SubscribeEvent
    public static void onRenderLabels(RenderLevelStageEvent event) {
        CoremodClient.Marker next = next();
        if (next == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        Camera camera = event.getCamera();
        Vector3f p = next.position();
        // Mit der Entfernung größer, damit der Name auch von weitem lesbar bleibt
        double distance = camera.getPosition().distanceTo(new Vec3(p));
        Component text = Component.literal(next.label()).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" · " + Zugdaten.number(distance) + " m").withStyle(ChatFormatting.GRAY));

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Weltauswahl.label(Weltauswahl.labelPose(event), buffers, camera, p, text, 4.0f, (float) Math.max(1, distance / 12));
        buffers.endBatch();
    }
}
