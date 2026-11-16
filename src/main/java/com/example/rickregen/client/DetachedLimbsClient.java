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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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
 * Extremidades desprendidas con fisica real sobre el terreno (incluyendo cuevas e interiores),
 * cables/tendones curvos con gravedad y efecto de retraccion al regenerarse.
 */
@Mod.EventBusSubscriber(modid = RickregenMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DetachedLimbsClient {

    private static final ResourceLocation TENDON_TEXTURE = new ResourceLocation("minecraft", "textures/block/red_nether_bricks.png");

    private static final class DragState {
        final boolean[] active = new boolean[6];
        final Vec3[] pos = new Vec3[6];
        final Vec3[] prev = new Vec3[6];
        final float[] yaw = new float[6];
        final float[] prevYaw = new float[6];
        final float[] roll = new float[6];
        final Vec3[] velocity = new Vec3[6];
    }

    private static final Map<UUID, DragState> DRAG = new HashMap<>();
    private static ModelPart cableSegmentPart;

    private static ModelPart cableSegment() {
        if (cableSegmentPart == null) {
            MeshDefinition mesh = new MeshDefinition();
            // Segmento fino de tendon/nervio (0.75 x 16 x 0.75 unidades de modelo)
            mesh.getRoot().addOrReplaceChild("cable",
                    CubeListBuilder.create().texOffs(0, 0).addBox(-0.38F, 0.0F, -0.38F, 0.76F, 16.0F, 0.76F),
                    PartPose.ZERO);
            cableSegmentPart = LayerDefinition.create(mesh, 16, 16).bakeRoot().getChild("cable");
        }
        return cableSegmentPart;
    }

    /**
     * Calcula la altura exacta del suelo debajo de (x, y, z) sin teletransportar piezas al techo
     * cuando el jugador esta bajo arboles, cuevas o edificios.
     */
    private static double findLocalFloorY(Level level, double x, double refY, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int startY = Mth.floor(refY + 0.85);
        int minY = Math.max(level.getMinBuildHeight(), Mth.floor(refY - 3.5));

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int y = startY; y >= minY; y--) {
            mpos.set(bx, y, bz);
            BlockState state = level.getBlockState(mpos);
            if (!state.isAir() && state.blocksMotion()) {
                VoxelShape shape = state.getCollisionShape(level, mpos);
                if (!shape.isEmpty()) {
                    double top = y + shape.max(net.minecraft.core.Direction.Axis.Y);
                    if (top <= refY + 1.15) {
                        return top;
                    }
                }
            }
        }
        return refY;
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
                // Separacion inicial natural para que las extremidades no se monten unas encima de otras
                double sideDir = (idx == RickregenHandler.RIGHT_LEG || idx == RickregenHandler.RIGHT_ARM) ? 1.0 : -1.0;
                if (idx == RickregenHandler.HEAD) sideDir = 0.15;
                double backDist = (idx <= 2) ? 0.55 : (idx <= 4 ? 0.35 : 0.25);

                double sx = anchor.x + backX * backDist + rightX * sideDir * 0.28;
                double sz = anchor.z + backZ * backDist + rightZ * sideDir * 0.28;
                double floorY = findLocalFloorY(player.level(), sx, base.y, sz) + 0.06;

                Vec3 start = new Vec3(sx, floorY, sz);
                st.pos[idx] = start;
                st.prev[idx] = start;
                st.velocity[idx] = new Vec3(
                        (backX * 0.04) + (rightX * sideDir * 0.05),
                        0.08,
                        (backZ * 0.04) + (rightZ * sideDir * 0.05)
                );
                float initialYaw = (float) Math.atan2(-backX, -backZ) + (float) (sideDir * 0.35);
                st.yaw[idx] = initialYaw;
                st.prevYaw[idx] = initialYaw;
                st.roll[idx] = 0.0F;
                st.active[idx] = true;
            }

            st.prev[idx] = st.pos[idx];
            st.prevYaw[idx] = st.yaw[idx];
            Vec3 p = st.pos[idx];
            Vec3 vel = st.velocity[idx] == null ? Vec3.ZERO : st.velocity[idx];

            // Repulsion suave entre extremidades caidas para evitar que se superpongan
            for (int other = 1; other < idx; other++) {
                if (st.active[other] && st.pos[other] != null) {
                    Vec3 diff = p.subtract(st.pos[other]);
                    double dHoriz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
                    if (dHoriz < 0.32 && dHoriz > 0.0001) {
                        double push = (0.32 - dHoriz) * 0.12;
                        vel = vel.add((diff.x / dHoriz) * push, 0.0, (diff.z / dHoriz) * push);
                    }
                }
            }

            boolean inWater = player.isInWater();
            double gravity = inWater ? 0.012 : 0.070;
            vel = vel.add(0.0, -gravity, 0.0).scale(inWater ? 0.86 : 0.90);

            // Tension de cuerda/tendon
            double dx = anchor.x - p.x;
            double dz = anchor.z - p.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            double maxLen = (idx == RickregenHandler.HEAD) ? 0.72 : 0.92;
            if (dist > maxLen) {
                double nx = dx / Math.max(dist, 0.0001);
                double nz = dz / Math.max(dist, 0.0001);
                double tension = Math.min(0.18, (dist - maxLen) * 0.28);
                vel = vel.add(nx * tension, 0.0, nz * tension);
            }

            Vec3 next = p.add(vel);
            double floor = findLocalFloorY(player.level(), next.x, Math.max(p.y, base.y), next.z) + 0.06;
            if (next.y < floor) {
                next = new Vec3(next.x, floor, next.z);
                vel = new Vec3(vel.x * 0.68, Math.abs(vel.y) > 0.035 ? -vel.y * 0.14 : 0.0, vel.z * 0.68);
            }

            // Limite elastico maximo
            double ndx = anchor.x - next.x;
            double ndz = anchor.z - next.z;
            double nextDist = Math.sqrt(ndx * ndx + ndz * ndz);
            if (nextDist > maxLen * 1.45) {
                double correction = (nextDist - maxLen * 1.45) / Math.max(nextDist, 0.0001);
                next = new Vec3(next.x + ndx * correction, next.y, next.z + ndz * correction);
                vel = new Vec3(vel.x * 0.60, vel.y, vel.z * 0.60);
            }

            st.velocity[idx] = vel;
            st.pos[idx] = next;

            double speedHoriz = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            st.roll[idx] = (float) Mth.clamp(speedHoriz * 1.6, -0.35, 0.35);
            if (speedHoriz > 0.015) {
                float targetYaw = (float) Math.atan2(vel.x, vel.z);
                st.yaw[idx] = rotLerpRad(0.25F, st.yaw[idx], targetYaw);
            }
        }
    }

    private static float rotLerpRad(float delta, float start, float end) {
        float diff = end - start;
        while (diff < -(float) Math.PI) diff += (float) (Math.PI * 2.0);
        while (diff >= (float) Math.PI) diff -= (float) (Math.PI * 2.0);
        return start + delta * diff;
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
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        boolean crawling = RickregenHandler.isCrawling(player);

        for (int idx = 1; idx <= 5; idx++) {
            if (!st.active[idx] || st.pos[idx] == null) {
                continue;
            }
            Vec3 piece = st.prev[idx].lerp(st.pos[idx], partial).subtract(origin);
            Vec3 anchor = RickregenHandler.getLimbOffset(player, idx, bodyYaw, crawling);
            float interpYaw = rotLerpRad(partial, st.prevYaw[idx], st.yaw[idx]);

            renderCatenaryTendon(ps, buffers, light, anchor, piece.add(0.0, 0.07, 0.0));
            renderPiece(ps, buffers, light, model, player, idx, piece, interpYaw, st.roll[idx]);
        }
    }

    /**
     * Dibuja un tendon/cable curvo con caida por gravedad (5 segmentos) entre el muñon y la pieza,
     * evitando que parezca un palo rigido recto.
     */
    private static void renderCatenaryTendon(PoseStack ps, MultiBufferSource buffers, int light, Vec3 from, Vec3 to) {
        double totalDist = to.distanceTo(from);
        if (totalDist < 0.03) {
            return;
        }
        int segments = 5;
        // Cuanto mas cerca esta la pieza, mas cuelga el cable; al tensarse se estira
        double slack = Math.max(0.02, 0.16 * (1.0 - Math.min(1.0, totalDist / 1.15)));

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TENDON_TEXTURE));
        Vec3 prevPt = from;

        for (int i = 1; i <= segments; i++) {
            float t = (float) i / segments;
            double sag = Math.sin(t * Math.PI) * slack;
            Vec3 currPt = new Vec3(
                    Mth.lerp(t, from.x, to.x),
                    Math.max(0.03, Mth.lerp(t, from.y, to.y) - sag),
                    Mth.lerp(t, from.z, to.z)
            );
            renderCableSegment(ps, vc, light, prevPt, currPt);
            prevPt = currPt;
        }
    }

    private static void renderCableSegment(PoseStack ps, VertexConsumer vc, int light, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.005) {
            return;
        }
        ps.pushPose();
        ps.translate(from.x, from.y, from.z);
        Vector3f dir = new Vector3f((float) (d.x / len), (float) (d.y / len), (float) (d.z / len));
        Quaternionf rot = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), dir);
        ps.mulPose(rot);
        ps.scale(0.55F, (float) len, 0.55F);
        cableSegment().render(ps, vc, light, OverlayTexture.NO_OVERLAY);
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
        ps.translate(pos.x, pos.y + (idx == RickregenHandler.HEAD ? 0.24 : 0.11), pos.z);
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
