package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemSword;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;

/** Attacks the manually targeted player when vanilla's attack cooldown is ready. */
public final class TriggerBot extends Module {
    public int minimumStrength=90,extraDelay=0,friendFilter=1,weaponOnly=0,requireAttackKey=0;
    private int cooldown;

    public TriggerBot(){
        super("TriggerBot","Attacks the player under your crosshair when your weapon cooldown is ready.",Category.COMBAT,0);
        addIntSetting("Minimum attack strength (%)",10,100,5,()->minimumStrength,v->minimumStrength=v);
        addIntSetting("Extra delay (ticks)",0,20,1,()->extraDelay,v->extraDelay=v);
        addIntSetting("Ignore friends (0/1)",0,1,1,()->friendFilter,v->friendFilter=v);
        addIntSetting("Require sword or axe (0/1)",0,1,1,()->weaponOnly,v->weaponOnly=v);
        addIntSetting("Require attack key (0/1)",0,1,1,()->requireAttackKey,v->requireAttackKey=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null)return;
        if(cooldown>0){cooldown--;return;}
        if(requireAttackKey!=0&&!mc.gameSettings.keyBindAttack.isKeyDown())return;
        RayTraceResult hit=mc.objectMouseOver;
        if(hit==null||hit.typeOfHit!=RayTraceResult.Type.ENTITY||!(hit.entityHit instanceof EntityPlayer))return;
        EntityPlayer target=(EntityPlayer)hit.entityHit;
        if(target==mc.player||target.isDead||target.getHealth()<=0F||target.isSpectator())return;
        if(friendFilter!=0&&FriendManager.isFriend(target.getName()))return;
        double reach=mc.playerController.getBlockReachDistance();
        if(mc.player.getDistanceSq(target)>reach*reach)return;
        if(weaponOnly!=0&&!(mc.player.getHeldItemMainhand().getItem() instanceof ItemSword)&&!(mc.player.getHeldItemMainhand().getItem() instanceof ItemAxe))return;
        if(mc.player.getCooledAttackStrength(0F)<minimumStrength/100F)return;
        mc.playerController.attackEntity(mc.player,target);
        mc.player.swingArm(EnumHand.MAIN_HAND);
        cooldown=extraDelay;
    }
}
