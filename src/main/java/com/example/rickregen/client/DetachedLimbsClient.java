package com.example.rickregen.client;

import com.example.rickregen.RickregenHandler;
import com.example.rickregen.RickregenMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cada extremidad desprendida queda tirada en el suelo, unida al muñon por un cable, y el jugador
 * la arrastra: la pieza solo se mueve cuando el cable se tensa (como arrastrar una cuerda).
 */
@Mod.EventBusSubscriber(modid = RickregenMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DetachedLimbsClient {

    private static final ResourceLocation CABLE_TEXTURE = new ResourceLocation("minecraft", "textures/block/gray_concrete.png");

    private static final class DragState {
        final boolean[] active = new boolean[6];
        final Vec3[] pos = new Vec3[6];
        final Vec3[] prev = new Vec3[6];
        final float[] yaw = new float[6];
    }

    private static final Map<UUID, DragState> DRAG = new HashMap<>();
    private static ModelPart cablePart;

    private static ModelPart cable() {
        if (cablePart == null) {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("cable",
                    CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 16.0F, 1.0F),
                    PartPose.ZERO);
            cablePart = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("cable");
        }
        return cablePart;
    }

    // ---------------------------------------------------------------- simulacion (cada tick del cliente)

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        UUID id = player.getUUID();

        boolean anyMissing = false;
        for (int idx = 1; idx <= 5; idx++) {
            if (RickregenHandler.isLimbMissing(player, idx, 0.0F)) {
                anyMissing = true;
                break;
            }
        }
        if (!anyMissing) {
            DRAG.remove(id);
            return;
        }

        DragState st = DRAG.computeIfAbsent(id, k -> new DragState());
        boolean crawling = RickregenHandler.isCrawling(player);
        float bodyYaw = player.yBodyRot;
        Vec3 base = player.position();
        double yawRad = Math.toRadians(bodyYaw);
        double backX = Math.sin(yawRad), backZ = -Math.cos(yawRad);
        double rightX = -Math.cos(yawRad), rightZ = -Math.sin(yawRad);

        for (int idx = 1; idx <= 5; idx++) {
            if (!RickregenHandler.isLimbMissing(player, idx, 0.0F)) {
                st.active[idx] = false;
                continue;
            }

            Vec3 anchor = base.add(RickregenHandler.getLimbOffset(player, idx, bodyYaw, crawling));

            if (!st.active[idx]) {
                double lateral = (idx - 3) * 0.25;
                Vec3 start = new Vec3(
                        anchor.x + backX * 0.5 + rightX * lateral,
                        base.y,
                        anchor.z + backZ * 0.5 + rightZ * lateral);
                st.pos[idx] = start;
                st.prev[idx] = start;
                st.yaw[idx] = (float) Math.atan2(-backX, -backZ);
                st.active[idx] = true;
            }

            st.prev[idx] = st.pos[idx];
            Vec3 p = st.pos[idx];

            double dx = anchor.x - p.x;
            double dz = anchor.z - p.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            double maxLen = (idx == RickregenHandler.HEAD) ? 0.8 : 1.0;

            double nx = p.x;
            double nz = p.z;
            if (dist > maxLen) {
                double pull = (dist - maxLen) / dist;
                nx += dx * pull;
                nz += dz * pull;
            }
            double ny = p.y + (base.y - p.y) * 0.4;
            st.pos[idx] = new Vec3(nx, ny, nz);

            if (dist > 0.05) {
                st.yaw[idx] = (float) Math.atan2(dx, dz);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DRAG.clear();
    }

    // ---------------------------------------------------------------- dibujo

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        DragState st = DRAG.get(player.getUUID());
        if (st == null) {
            return;
        }
        if (!(event.getRenderer().getModel() instanceof PlayerModel<?> model)) {
            return;
        }

        float partial = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();

        Vec3 origin = player.getPosition(partial);
        float bodyYaw = net.minecraft.util.Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        boolean crawling = RickregenHandler.isCrawling(player);

        for (int idx = 1; idx <= 5; idx++) {
            if (!st.active[idx] || st.pos[idx] == null) {
                continue;
            }
            Vec3 piece = st.prev[idx].lerp(st.pos[idx], partial).subtract(origin);
            Vec3 anchor = RickregenHandler.getLimbOffset(player, idx, bodyYaw, crawling);

            renderCable(ps, buffers, light, anchor, piece.add(0.0, 0.1, 0.0));
            renderPiece(ps, buffers, light, model, player, idx, piece, st.yaw[idx]);
        }
    }

    private static void renderCable(PoseStack ps, MultiBufferSource buffers, int light, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.02) {
            return;
        }
        ps.pushPose();
        ps.translate(from.x, from.y, from.z);
        Vector3f dir = new Vector3f((float) (d.x / len), (float) (d.y / len), (float) (d.z / len));
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), dir);
        ps.mulPose(rot);
        ps.scale(0.5F, (float) len, 0.5F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CABLE_TEXTURE));
        cable().render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    private static float[] savePose(ModelPart p) {
        return new float[]{p.x, p.y, p.z, p.xRot, p.yRot, p.zRot, p.xScale, p.yScale, p.zScale, p.visible ? 1.0F : 0.0F};
    }

    private static void restorePose(ModelPart p, float[] s) {
        p.setPos(s[0], s[1], s[2]);
        p.setRotation(s[3], s[4], s[5]);
        p.xScale = s[6];
        p.yScale = s[7];
        p.zScale = s[8];
        p.visible = s[9] > 0.5F;
    }

    private static void preparePose(ModelPart p) {
        p.setPos(0.0F, 0.0F, 0.0F);
        p.setRotation(0.0F, 0.0F, 0.0F);
        p.xScale = 1.0F;
        p.yScale = 1.0F;
        p.zScale = 1.0F;
        p.visible = true;
    }

    private static void renderPiece(PoseStack ps, MultiBufferSource buffers, int light, PlayerModel<?> model,
                                    AbstractClientPlayer player, int idx, Vec3 pos, float yawRad) {
        ModelPart base;
        ModelPart overlay;
        switch (idx) {
            case RickregenHandler.RIGHT_LEG -> { base = model.rightLeg; overlay = model.rightPants; }
            case RickregenHandler.LEFT_LEG -> { base = model.leftLeg; overlay = model.leftPants; }
            case RickregenHandler.RIGHT_ARM -> { base = model.rightArm; overlay = model.rightSleeve; }
            case RickregenHandler.LEFT_ARM -> { base = model.leftArm; overlay = model.leftSleeve; }
            default -> { base = model.head; overlay = model.hat; }
        }

        float[] savedBase = savePose(base);
        float[] savedOverlay = savePose(overlay);
        preparePose(base);
        preparePose(overlay);

        ps.pushPose();
        ps.translate(pos.x, pos.y + (idx == RickregenHandler.HEAD ? 0.26 : 0.13), pos.z);
        ps.mulPose(Axis.YP.rotation(yawRad));
        ps.mulPose(Axis.XP.rotationDegrees(idx == RickregenHandler.HEAD ? -90.0F : 90.0F));
        ps.scale(-0.9375F, -0.9375F, 0.9375F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkinTextureLocation()));
        base.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        overlay.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();

        restorePose(base, savedBase);
        restorePose(overlay, savedOverlay);
    }
}
