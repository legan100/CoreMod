package de.niconagel.coremod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.List;

/**
 * Punkte auf MR-Splines in der Welt setzen (Gegenstück zu Weltauswahl auf dem Server), für Gleise einer Abstellung und
 * Haltepunkte einer Route. Zeigt die Splines in der Nähe (weiß), Gleise der Abstellung (grün) und anderer Abstellungen
 * (rot, dort darf kein Gleis hin), die gesetzten Punkte mit Namen und den Punkt, auf den der Spieler zielt (gelb).
 * Rechtsklick setzt, Linksklick nimmt zurück, Schleichen + Rechtsklick bricht ab bzw. schließt die Haltepunkte ab.
 */
@EventBusSubscriber(modid = CoremodClient.MOD_ID, value = Dist.CLIENT)
public final class Weltauswahl {

    // wie Weltauswahl in der Server-Mod
    private static final int GLEIS = 1;
    private static final int HALTE = 2;
    private static final int SET = 0;
    private static final int DONE = 1;
    private static final int UNDO = 2;

    /** Linien etwas über der Spline, sonst verschwinden sie in den Schienen. */
    private static final float LIFT = 0.15f;
    private static final double REACH = 128;
    /** Wie weit der Blick neben der Spline vorbeigehen darf; wächst mit der Entfernung. */
    private static final double TOLERANCE = 0.75;
    /** Gedrückt gehaltene Maustaste wiederholt den Klick alle 4 Ticks; nur der erste zählt. */
    private static final long REPEAT_MS = 300;

    private static CoremodClient.AuswahlState state = CoremodClient.AuswahlState.INACTIVE;
    /** Punkt auf der Spline, auf den der Spieler gerade zielt, null wenn keiner. */
    private static Vector3f hover;
    private static long lastClick;

    private Weltauswahl() {
    }

    static void update(CoremodClient.AuswahlState newState) {
        state = newState;
        if (!active()) hover = null;
    }

    private static boolean active() {
        return state.mode() != 0;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        update(CoremodClient.AuswahlState.INACTIVE);
    }

    /** Rechtsklick setzt einen Punkt statt etwas zu benutzen, Linksklick nimmt zurück statt abzubauen. */
    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active() || !(event.isUseItem() || event.isAttack())) return;
        event.setCanceled(true);
        event.setSwingHand(false);

        long now = Util.getMillis();
        boolean repeated = now - lastClick < REPEAT_MS;
        lastClick = now;
        if (repeated) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (event.isAttack()) {
            send(new Vector3f(), UNDO, "");
        } else if (minecraft.player != null && minecraft.player.isShiftKeyDown()) {
            send(new Vector3f(), DONE, "");
        } else if (hover != null && state.mode() == HALTE) {
            // Erst benennen, dann setzen; Abbrechen setzt nichts
            Vector3f point = new Vector3f(hover);
            minecraft.setScreen(new FormScreen(null, "Haltepunkt benennen", List.of("Name"),
                    List.of("Halt " + (state.markers().size() + 1)), values -> {
                if (values.getFirst().isEmpty()) return "Bitte einen Namen eingeben.";
                send(point, SET, values.getFirst());
                return null;
            }));
        } else if (hover != null) {
            send(new Vector3f(hover), SET, "");
        }
    }

    private static void send(Vector3f point, int action, String name) {
        PacketDistributor.sendToServer(new CoremodClient.AuswahlClick(point, action, name));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!active() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        hover = aim(eye, new Vec3(camera.getLookVector()));

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-eye.x, -eye.y, -eye.z);
        PoseStack.Pose pose = poseStack.last();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        for (List<Vector3f> line : state.network()) polyline(lines, pose, line, 0xFFFFFF, 140);
        for (List<Vector3f> line : state.own()) polyline(lines, pose, line, 0x55FF55, 255);
        for (List<Vector3f> line : state.others()) polyline(lines, pose, line, 0xFF5555, 255);

        int markerColor = state.mode() == GLEIS ? 0x55FF55 : 0x55FFFF;
        for (CoremodClient.Marker marker : state.markers()) marker(lines, pose, marker.position(), markerColor);
        // Beim Gleis vom Anfang zum Ziel, damit man sieht, was entsteht
        if (state.mode() == GLEIS && !state.markers().isEmpty() && hover != null) {
            segment(lines, pose, state.markers().getFirst().position(), hover, 0xFFFF55, 120);
        }
        if (hover != null) marker(lines, pose, hover, occupied(hover) ? 0xFF5555 : 0xFFFF55);
        buffers.endBatch(RenderType.lines());

        for (CoremodClient.Marker marker : state.markers()) label(poseStack, buffers, camera, marker);
        buffers.endBatch();
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!active()) return;
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() / 2;
        boolean gleis = state.mode() == GLEIS;
        boolean empty = state.markers().isEmpty();

        Component step;
        if (hover != null && occupied(hover)) {
            step = Component.literal("Hier liegt schon ein Gleis einer Abstellung").withStyle(ChatFormatting.RED);
        } else {
            String text = gleis ? (empty ? "Rechtsklick auf die Spline: Anfang setzen" : "Rechtsklick auf die Spline: Ende setzen")
                    : "Rechtsklick auf die Spline: " + (state.markers().size() + 1) + ". Haltepunkt setzen";
            step = Component.literal(text).withStyle(hover == null ? ChatFormatting.GRAY : ChatFormatting.YELLOW);
        }
        String keys = (empty ? "" : gleis ? "Linksklick: Anfang zurücknehmen · " : "Linksklick: letzten zurücknehmen · ")
                + "Schleichen + Rechtsklick: " + (gleis || empty ? "abbrechen" : "fertig, weiter zur Abstellung");

        graphics.drawCenteredString(minecraft.font, Component.literal(state.title()).withStyle(ChatFormatting.BOLD), x, 12, 0xFFFFFF);
        graphics.drawCenteredString(minecraft.font, step, x, 24, 0xFFFFFF);
        graphics.drawCenteredString(minecraft.font, Component.literal(keys).withStyle(ChatFormatting.GRAY), x, 36, 0xFFFFFF);
    }

    /** Punkt auf den angezeigten Splines, den der Blick zuerst trifft (mit etwas Spielraum), oder null. */
    private static Vector3f aim(Vec3 origin, Vec3 direction) {
        Vector3f best = null;
        double bestAlong = REACH;
        for (List<Vector3f> line : state.network()) {
            for (int i = 0; i + 1 < line.size(); i++) {
                Vec3 a = new Vec3(line.get(i));
                Vec3 u = new Vec3(line.get(i + 1)).subtract(a);
                // Nächste Punkte zwischen Blickstrahl und Splinestück, das Stück auf [0, 1] begrenzt
                double b = direction.dot(u);
                double c = u.dot(u);
                Vec3 w = origin.subtract(a);
                double d = direction.dot(w);
                double e = u.dot(w);
                double denominator = c - b * b;
                double s = denominator < 1.0E-9 ? 0 : Mth.clamp((e - b * d) / denominator, 0, 1);
                Vec3 onLine = a.add(u.scale(s));
                double along = direction.dot(onLine.subtract(origin));
                if (along <= 0 || along >= bestAlong) continue;
                if (origin.add(direction.scale(along)).distanceTo(onLine) < TOLERANCE + along * 0.01) {
                    bestAlong = along;
                    best = onLine.toVector3f();
                }
            }
        }
        return best;
    }

    /**
     * Liegt der Punkt auf einem schon angelegten Gleis (eigene Abstellung oder andere)? Nur beim Gleis setzen und nur für
     * die Anzeige, entscheiden tut der Server. Die Enden zählen nicht, dort darf ein neues Gleis anschließen.
     */
    private static boolean occupied(Vector3f point) {
        if (state.mode() != GLEIS) return false;
        for (List<List<Vector3f>> lines : List.of(state.own(), state.others())) {
            for (List<Vector3f> line : lines) {
                if (point.distance(line.getFirst()) < 0.5f || point.distance(line.getLast()) < 0.5f) continue;
                for (int i = 0; i + 1 < line.size(); i++) {
                    if (distanceToSegment(point, line.get(i), line.get(i + 1)) < 0.2f) return true;
                }
            }
        }
        return false;
    }

    private static float distanceToSegment(Vector3f p, Vector3f a, Vector3f b) {
        Vector3f ab = new Vector3f(b).sub(a);
        float lengthSq = ab.lengthSquared();
        float t = lengthSq < 1.0E-8f ? 0 : Mth.clamp(new Vector3f(p).sub(a).dot(ab) / lengthSq, 0, 1);
        return p.distance(new Vector3f(a).add(ab.mul(t)));
    }

    private static void polyline(VertexConsumer lines, PoseStack.Pose pose, List<Vector3f> line, int rgb, int alpha) {
        for (int i = 0; i + 1 < line.size(); i++) segment(lines, pose, line.get(i), line.get(i + 1), rgb, alpha);
    }

    private static void segment(VertexConsumer lines, PoseStack.Pose pose, Vector3f a, Vector3f b, int rgb, int alpha) {
        Vector3f normal = new Vector3f(b).sub(a);
        if (normal.lengthSquared() < 1.0E-8f) return;
        normal.normalize();
        vertex(lines, pose, a, normal, rgb, alpha);
        vertex(lines, pose, b, normal, rgb, alpha);
    }

    private static void vertex(VertexConsumer lines, PoseStack.Pose pose, Vector3f p, Vector3f normal, int rgb, int alpha) {
        lines.addVertex(pose, p.x(), p.y() + LIFT, p.z())
                .setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha)
                .setNormal(pose, normal.x(), normal.y(), normal.z());
    }

    /** Senkrechter Strich mit Kreuz am Fuß, gut zu sehen auch von weiter weg. */
    private static void marker(VertexConsumer lines, PoseStack.Pose pose, Vector3f p, int rgb) {
        segment(lines, pose, p, new Vector3f(p).add(0, 2.5f, 0), rgb, 255);
        segment(lines, pose, new Vector3f(p).add(-0.5f, 0, 0), new Vector3f(p).add(0.5f, 0, 0), rgb, 255);
        segment(lines, pose, new Vector3f(p).add(0, 0, -0.5f), new Vector3f(p).add(0, 0, 0.5f), rgb, 255);
    }

    /** Name über dem Punkt, zur Kamera gedreht und durch Blöcke sichtbar, wie ein Namensschild. */
    private static void label(PoseStack poseStack, MultiBufferSource buffers, Camera camera, CoremodClient.Marker marker) {
        Font font = Minecraft.getInstance().font;
        Vector3f p = marker.position();
        poseStack.pushPose();
        poseStack.translate(p.x(), p.y() + 3.0f, p.z());
        poseStack.mulPose(camera.rotation());
        poseStack.scale(0.025f, -0.025f, 0.025f);
        Component text = Component.literal(marker.label());
        font.drawInBatch(text, -font.width(text) / 2f, 0, 0xFFFFFFFF, false, poseStack.last().pose(), buffers,
                Font.DisplayMode.SEE_THROUGH, 0x40000000, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
