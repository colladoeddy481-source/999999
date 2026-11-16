package com.example.rickregen.client;

import com.example.rickregen.RickregenHandler;
import com.example.rickregen.RickregenMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
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
        final float[] roll = new float[6];
        final Vec3[] velocity = new Vec3[6];
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
                int groundY = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(start.x, start.y, start.z).getX(), BlockPos.containing(start.x, start.y, start.z).getZ());
                start = new Vec3(start.x, groundY + 0.10, start.z);
                st.pos[idx] = start;
                st.prev[idx] = start;
                st.velocity[idx] = Vec3.ZERO;
                st.yaw[idx] = (float) Math.atan2(-backX, -backZ);
                st.roll[idx] = 0.0F;
                st.active[idx] = true;
            }

            st.prev[idx] = st.pos[idx];
            Vec3 p = st.pos[idx];
            Vec3 vel = st.velocity[idx] == null ? Vec3.ZERO : st.velocity[idx];

            // Simulacion ligera tipo ragdoll: gravedad, rozamiento y rebote amortiguado.
            // En agua las piezas caen mas despacio y pierden velocidad gradualmente.
            boolean inWater = player.isInWater();
            double gravity = inWater ? 0.012 : 0.075;
            vel = vel.add(0.0, -gravity, 0.0).scale(inWater ? 0.88 : 0.94);

            // El cable actua como una cuerda: solo tira cuando supera su longitud maxima.
            double dx = anchor.x - p.x;
            double dz = anchor.z - p.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            double maxLen = (idx == RickregenHandler.HEAD) ? 0.90 : 1.10;
            if (dist > maxLen) {
                double nx = dx / Math.max(dist, 0.0001);
                double nz = dz / Math.max(dist, 0.0001);
                double tension = Math.min(0.16, (dist - maxLen) * 0.22);
                vel = vel.add(nx * tension, 0.0, nz * tension);
            }

            Vec3 next = p.add(vel);
            BlockPos below = BlockPos.containing(next.x, next.y, next.z);
            int groundY = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, below.getX(), below.getZ());
            double floor = groundY + 0.10;
            if (next.y < floor) {
                next = new Vec3(next.x, floor, next.z);
                // Rebote pequeño al tocar el suelo y friccion horizontal para evitar deslizamiento infinito.
                vel = new Vec3(vel.x * 0.72, Math.abs(vel.y) > 0.035 ? -vel.y * 0.16 : 0.0, vel.z * 0.72);
            }

            // Limita el desplazamiento horizontal sin teletransportar la pieza al muñon.
            double ndx = anchor.x - next.x;
            double ndz = anchor.z - next.z;
            double nextDist = Math.sqrt(ndx * ndx + ndz * ndz);
            if (nextDist > maxLen * 1.65) {
                double correction = (nextDist - maxLen * 1.65) / Math.max(nextDist, 0.0001);
                next = new Vec3(next.x + ndx * correction, next.y, next.z + ndz * correction);
                vel = new Vec3(vel.x * 0.65, vel.y, vel.z * 0.65);
            }

            st.velocity[idx] = vel;
            st.pos[idx] = next;
            st.roll[idx] = (float) Math.max(-0.55, Math.min(0.55, vel.z * 1.8));
            if (Math.abs(vel.x) + Math.abs(vel.z) > 0.015) {
                st.yaw[idx] = (float) Math.atan2(vel.x, vel.z);
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
            renderPiece(ps, buffers, light, model, player, idx, piece, st.yaw[idx], st.roll[idx]);
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
                                    AbstractClientPlayer player, int idx, Vec3 pos, float yawRad, float roll) {
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
        ps.mulPose(Axis.ZP.rotation(roll));
        ps.scale(-0.9375F, -0.9375F, 0.9375F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkinTextureLocation()));
        base.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        overlay.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();

        restorePose(base, savedBase);
        restorePose(overlay, savedOverlay);
    }
}
