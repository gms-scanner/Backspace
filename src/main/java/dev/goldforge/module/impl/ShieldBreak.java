package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemSword;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Uses ordinary attacks to disable an actively blocking shield or hit from behind its facing. */
public final class ShieldBreak extends Module {
    /** 0 = axe disable, 1 = rear hit, 2 = automatic. */
    public int mode = 2, minimumStrength = 95, rearDot = 0, friendFilter = 1, requireWeapon = 1;
    private int delayTicks;

    public ShieldBreak() {
        super("ShieldBreak", "Uses an axe to trigger vanilla shield disable, or waits for a rear-angle hit.", Category.COMBAT, 0);
        addIntSetting("Mode (0 axe, 1 rear, 2 auto)", 0, 2, 1, () -> mode, v -> mode = v);
        addIntSetting("Minimum attack strength (%)", 10, 100, 5, () -> minimumStrength, v -> minimumStrength = v);
        addIntSetting("Rear angle threshold (%)", -100, 100, 10, () -> rearDot, v -> rearDot = v);
        addIntSetting("Ignore friends (0/1)", 0, 1, 1, () -> friendFilter, v -> friendFilter = v);
        addIntSetting("Require sword or axe (0/1)", 0, 1, 1, () -> requireWeapon, v -> requireWeapon = v);
    }

    @Override public void tick() {
        if (mc.player == null || mc.world == null || mc.playerController == null || mc.currentScreen != null) return;
        if (delayTicks > 0) { delayTicks--; return; }

        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.ENTITY || !(hit.entityHit instanceof EntityPlayer)) return;
        EntityPlayer target = (EntityPlayer) hit.entityHit;
        if (target == mc.player || target.isDead || target.getHealth() <= 0F || target.isSpectator()) return;
        if (friendFilter != 0 && FriendManager.isFriend(target.getName())) return;

        double reach = mc.playerController.getBlockReachDistance();
        if (mc.player.getDistanceSq(target) > reach * reach) return;
        if (mc.player.getCooledAttackStrength(0F) < minimumStrength / 100F) return;

        boolean blocking = target.isHandActive() && target.getActiveItemStack().getItem() == Items.SHIELD;
        boolean rearHit = isBehindTarget(target);
        int axeSlot = findAxeHotbarSlot();
        boolean useAxe = (mode == 0 && blocking) || (mode == 2 && blocking && !rearHit && axeSlot >= 0);
        boolean allowed = useAxe || ((mode == 1 || mode == 2) && rearHit);
        if (!allowed) return;

        if (useAxe) {
            if (axeSlot < 0) return;
            attackWithAxe(target, axeSlot);
        } else {
            if (requireWeapon != 0 && !(mc.player.getHeldItemMainhand().getItem() instanceof ItemSword)
                    && !(mc.player.getHeldItemMainhand().getItem() instanceof ItemAxe)) return;
            attack(target);
        }
        delayTicks = 1;
    }

    private boolean isBehindTarget(EntityPlayer target) {
        Vec3d targetLook = target.getLook(1F).normalize();
        Vec3d towardAttacker = mc.player.getPositionEyes(1F).subtract(target.getPositionEyes(1F)).normalize();
        double facingDot = targetLook.dotProduct(towardAttacker);
        return facingDot <= rearDot / 100.0D;
    }

    private int findAxeHotbarSlot() {
        for (int slot = 0; slot < 9; slot++)
            if (mc.player.inventory.getStackInSlot(slot).getItem() instanceof ItemAxe) return slot;
        return -1;
    }

    private void attackWithAxe(EntityPlayer target, int axeSlot) {
        int previous = mc.player.inventory.currentItem;
        mc.player.inventory.currentItem = axeSlot;
        attack(target);
        mc.player.inventory.currentItem = previous;
    }

    private void attack(EntityPlayer target) {
        mc.playerController.attackEntity(mc.player, target);
        mc.player.swingArm(EnumHand.MAIN_HAND);
    }
}
