package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Selects and mines an exposed block at an opponent's feet or upper surround. */
public final class AutoMine extends Module {
    public int targetRange = 6;
    public int includeFloor = 1;
    public int includeHead = 0;
    public int autoTool = 1;
    public int ignoreFriends = 1;
    public int throughWalls = 0;
    private BlockPos mining;
    private EnumFacing miningFace;
    private EntityPlayer victim;
    private int originalSlot = -1;
    private int selectedTool = -1;
    private int swingTicks;

    public AutoMine() {
        super("AutoMine", "Mines exposed blocks around a target's feet and previews the next block.", Category.COMBAT, 0);
        addIntSetting("Target range", 3, 8, 1, () -> targetRange, v -> targetRange = v);
        addIntSetting("Include floor (0/1)", 0, 1, 1, () -> includeFloor, v -> includeFloor = v);
        addIntSetting("Include head ring (0/1)", 0, 1, 1, () -> includeHead, v -> includeHead = v);
        addIntSetting("Best hotbar tool (0/1)", 0, 1, 1, () -> autoTool, v -> autoTool = v);
        addIntSetting("Ignore friends (0/1)", 0, 1, 1, () -> ignoreFriends, v -> ignoreFriends = v);
        addIntSetting("Preview through walls (0/1)", 0, 1, 1, () -> throughWalls, v -> throughWalls = v);
    }

    @Override public void tick() {
        if (mc.player == null || mc.world == null || mc.playerController == null || mc.currentScreen != null) {
            reset();
            return;
        }
        EntityPlayer target = nearestTarget();
        if (target == null) { reset(); return; }
        if (mining != null && (victim != target || !validBlock(mining)
                || reachableFace(mining) == null || !nearTarget(mining, target))) reset();
        if (mining == null) {
            Candidate best = chooseBlock(target);
            if (best == null) return;
            mining = best.pos.toImmutable();
            miningFace = best.face;
            victim = target;
            swingTicks = 0;
        }
        if (autoTool != 0) {
            int slot = InventoryManager.bestHotbarTool(mc, mc.world.getBlockState(mining));
            if (slot >= 0) {
                if (originalSlot < 0) originalSlot = mc.player.inventory.currentItem;
                mc.player.inventory.currentItem = slot;
                selectedTool = slot;
            }
        }
        // Drive the ordinary mining controller. Its progress and STOP packet
        // remain tied to the actual server block rather than a local timer.
        mc.playerController.onPlayerDamageBlock(mining, miningFace);
        if (++swingTicks >= 4) {
            mc.player.swingArm(EnumHand.MAIN_HAND);
            swingTicks = 0;
        }
    }

    private EntityPlayer nearestTarget() {
        EntityPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        double rangeSq = targetRange * targetRange;
        for (EntityPlayer candidate : mc.world.playerEntities) {
            if (candidate == mc.player || candidate.isDead || candidate.getHealth() <= 0F
                    || candidate.isSpectator() || (ignoreFriends != 0 && FriendManager.isFriend(candidate.getName()))) continue;
            double distance = mc.player.getDistanceSq(candidate);
            if (distance < rangeSq && distance < bestDistance) { best = candidate; bestDistance = distance; }
        }
        return best;
    }

    private Candidate chooseBlock(EntityPlayer target) {
        BlockPos feet = new BlockPos(target.posX, target.getEntityBoundingBox().minY, target.posZ);
        Candidate best = null;
        for (int area = 0; area < 3; ++area) {
            if (area == 1 && includeFloor == 0 || area == 2 && includeHead == 0) continue;
            int y = area == 0 ? feet.getY() : area == 1 ? feet.getY() - 1 : feet.getY() + 1;
            BlockPos center = new BlockPos(feet.getX(), y, feet.getZ());
            for (EnumFacing side : EnumFacing.HORIZONTALS) {
                Candidate next = candidate(center.offset(side), area);
                if (next != null && (best == null || next.score < best.score)) best = next;
            }
            if (area <= 1) {
                Candidate next = candidate(center, area + 1);
                if (next != null && (best == null || next.score < best.score)) best = next;
            }
        }
        return best;
    }

    private Candidate candidate(BlockPos pos, int area) {
        if (!validBlock(pos)) return null;
        EnumFacing face = reachableFace(pos);
        if (face == null) return null;
        IBlockState state = mc.world.getBlockState(pos);
        int type = state.getBlock() == Blocks.OBSIDIAN || state.getBlock() == Blocks.ENDER_CHEST ? 0 : 1;
        double score = area * 12D + type * 3D + mc.player.getDistanceSq(
                pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D) * .06D;
        return new Candidate(pos, face, score);
    }

    private boolean validBlock(BlockPos pos) {
        if (!mc.world.isBlockLoaded(pos)) return false;
        IBlockState state = mc.world.getBlockState(pos);
        return !state.getMaterial().isReplaceable() && state.getBlockHardness(mc.world, pos) >= 0F
                && state.getBlock() != Blocks.BEDROCK;
    }

    private EnumFacing reachableFace(BlockPos pos) {
        Vec3d eyes = mc.player.getPositionEyes(1F);
        double reach = Math.min(targetRange, mc.playerController.getBlockReachDistance()) - .1D;
        EnumFacing best = null;
        double bestDistance = Double.MAX_VALUE;
        for (EnumFacing face : EnumFacing.values()) {
            Vec3d hit = InventoryBlocks.hitVec(pos, face).addVector(
                    face.getFrontOffsetX() * .01D, face.getFrontOffsetY() * .01D, face.getFrontOffsetZ() * .01D);
            double distance = eyes.squareDistanceTo(hit);
            if (distance > reach * reach || distance >= bestDistance) continue;
            RayTraceResult trace = mc.world.rayTraceBlocks(eyes, hit, false, true, false);
            if (trace == null || trace.typeOfHit != RayTraceResult.Type.BLOCK || !pos.equals(trace.getBlockPos())) continue;
            best = trace.sideHit;
            bestDistance = distance;
        }
        return best;
    }

    private boolean nearTarget(BlockPos pos, EntityPlayer target) {
        BlockPos feet = new BlockPos(target.posX, target.getEntityBoundingBox().minY, target.posZ);
        return Math.abs(pos.getX() - feet.getX()) <= 1 && Math.abs(pos.getZ() - feet.getZ()) <= 1
                && pos.getY() >= feet.getY() - 1 && pos.getY() <= feet.getY() + 1;
    }

    @Override public void render(float partialTicks) {
        if (mining == null || mc.player == null || mc.world == null || !validBlock(mining)) return;
        double vx = mc.getRenderManager().viewerPosX;
        double vy = mc.getRenderManager().viewerPosY;
        double vz = mc.getRenderManager().viewerPosZ;
        AxisAlignedBB box = new AxisAlignedBB(mining).grow(.003D).offset(-vx, -vy, -vz);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableTexture2D();
            if (throughWalls != 0) GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            RenderGlobal.drawSelectionBoundingBox(box, 1F, .78F, .1F, .95F);
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.popMatrix();
        }
    }

    private void reset() {
        if (mining != null && mc.playerController != null) mc.playerController.resetBlockRemoving();
        if (mc.player != null && originalSlot >= 0 && mc.player.inventory.currentItem == selectedTool)
            mc.player.inventory.currentItem = originalSlot;
        mining = null;
        miningFace = null;
        victim = null;
        originalSlot = selectedTool = -1;
        swingTicks = 0;
    }

    @Override protected void onDisable() { reset(); }
    @Override protected void onWorldUnload() { reset(); }

    private static final class Candidate {
        final BlockPos pos;
        final EnumFacing face;
        final double score;
        Candidate(BlockPos pos, EnumFacing face, double score) {
            this.pos = pos;
            this.face = face;
            this.score = score;
        }
    }
}
