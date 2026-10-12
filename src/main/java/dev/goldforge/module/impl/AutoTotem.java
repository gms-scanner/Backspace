package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import dev.goldforge.inventory.InventoryManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.CombatRules;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.enchantment.EnchantmentHelper;

/** Keeps a totem in the offhand and optionally eats a golden apple during lower-risk windows. */
public final class AutoTotem extends Module {
    public int advanced=0, gapHealth=14, threatRadius=10;
    private boolean startedGap;
    private int scanDelay,gapRetry,gapReturnSlot=-1;
    private Threat cachedThreat=new Threat(0D,false,false);

    public AutoTotem(){
        super("AutoTotem","Automatically equips an offhand totem; Advanced mode gaps at low health when crystal risk permits.",Category.PLAYER,0);
        addIntSetting("Advanced (0/1)",0,1,1,()->advanced,v->advanced=v);
        addIntSetting("Gap health",4,20,1,()->gapHealth,v->gapHealth=v);
        addIntSetting("Threat radius",4,16,1,()->threatRadius,v->threatRadius=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null)return;
        if(scanDelay--<=0){cachedThreat=assessThreat();scanDelay=2;}
        ItemStack offhand=mc.player.getHeldItemOffhand();
        if(offhand.getItem()!=Items.TOTEM_OF_UNDYING){
            stopGap();
            if(findAndEquipTotem())return;
        }
        if(advanced==0||mc.player.getHealth()+mc.player.getAbsorptionAmount()>gapHealth||cachedThreat.lethal||cachedThreat.imminent){
            stopGap();return;
        }
        gapIfSafe();
    }

    private boolean findAndEquipTotem(){
        int slot=InventoryManager.findItem(mc,Items.TOTEM_OF_UNDYING);if(slot<0)return false;
        int hotbar=slot<9?slot:mc.player.inventory.currentItem;
        if(slot>=9&&!InventoryManager.moveToHotbar(mc,slot,hotbar))return false;
        mc.playerController.windowClick(mc.player.inventoryContainer.windowId,45,hotbar,ClickType.SWAP,mc.player);return true;
    }

    private void gapIfSafe(){
        if(mc.player.isHandActive()){
            if(mc.player.getActiveHand()==EnumHand.MAIN_HAND&&mc.player.getHeldItemMainhand().getItem()==Items.GOLDEN_APPLE)startedGap=true;
            return;
        }
        if(gapRetry>0){gapRetry--;return;}
        int slot=findApple();
        if(slot<0){stopGap();return;}
        if(gapReturnSlot<0)gapReturnSlot=mc.player.inventory.currentItem;
        mc.player.inventory.currentItem=slot;
        mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND);
        startedGap=mc.player.isHandActive()&&mc.player.getActiveHand()==EnumHand.MAIN_HAND;
        gapRetry=startedGap?0:3;
    }

    private int findApple(){
        int slot=InventoryManager.findItem(mc,Items.GOLDEN_APPLE);if(slot<0)return -1;
        if(slot<9)return slot;int preferred=mc.player.inventory.currentItem;
        return InventoryManager.moveToHotbar(mc,slot,preferred)?preferred:-1;
    }

    private void stopGap(){
        if(startedGap&&mc.player!=null&&mc.player.isHandActive()&&mc.player.getActiveHand()==EnumHand.MAIN_HAND)mc.playerController.onStoppedUsingItem(mc.player);
        if(gapReturnSlot>=0&&mc.player!=null&&gapReturnSlot<9&&mc.player.inventory.getStackInSlot(gapReturnSlot).getItem()!=Items.GOLDEN_APPLE)mc.player.inventory.currentItem=gapReturnSlot;
        startedGap=false;
        gapReturnSlot=-1;gapRetry=0;
    }

    /** Multiple warning signals: live crystal damage, likely opponent placements, and nearby held crystals. */
    private Threat assessThreat(){
        double worst=0;boolean imminent=false,hasCrystal=false,armedOpponent=false;
        EntityPlayer self=mc.player;
        for(Entity entity:mc.world.loadedEntityList){
            if(!(entity instanceof EntityEnderCrystal)||self.getDistance(entity)>threatRadius)continue;
            hasCrystal=true;
            double damage=explosionDamage(entity.posX,entity.posY,entity.posZ,self);
            worst=Math.max(worst,damage);
            if(damage>=self.getHealth()+self.getAbsorptionAmount()-1.0D)imminent=true;
        }
        // Check exposed obsidian/bedrock bases near enemies holding crystals. This estimates a threat before a crystal entity exists.
        for(EntityPlayer enemy:mc.world.playerEntities){
            if(enemy==self||enemy.isDead||enemy.getDistance(self)>threatRadius+3||!holdsCrystal(enemy))continue;
            armedOpponent=true;
            BlockPos center=new BlockPos(enemy.posX,enemy.posY,enemy.posZ);
            for(BlockPos base:BlockPos.getAllInBox(center.add(-2,-1,-2),center.add(2,1,2))){
                if(!crystalBase(base))continue;
                Vec3d explosion=new Vec3d(base.getX()+.5D,base.getY()+1D,base.getZ()+.5D);
                if(enemy.getPositionEyes(1F).squareDistanceTo(explosion)>36D)continue;
                double damage=explosionDamage(explosion.x,explosion.y,explosion.z,self);
                worst=Math.max(worst,damage*.82D); // Slightly discount potential placement for the player's exact timing.
                if(damage>=self.getHealth()+self.getAbsorptionAmount()-1.0D)imminent=true;
            }
        }
        // Nearby crystal users are a separate signal when the base is outside our compact search area.
        for(EntityPlayer enemy:mc.world.playerEntities)if(enemy!=self&&!enemy.isDead&&enemy.getDistance(self)<6&&holdsCrystal(enemy)){armedOpponent=true;break;}
        boolean popRisk=(hasCrystal||armedOpponent)&&worst>=Math.max(3D,(self.getHealth()+self.getAbsorptionAmount())*.55D);
        return new Threat(worst,imminent,imminent||popRisk);
    }

    private boolean holdsCrystal(EntityPlayer player){
        return player.getHeldItemMainhand().getItem()==Items.END_CRYSTAL||player.getHeldItemOffhand().getItem()==Items.END_CRYSTAL;
    }

    private boolean crystalBase(BlockPos pos){
        if(mc.world.getBlockState(pos).getBlock()!=Blocks.OBSIDIAN&&mc.world.getBlockState(pos).getBlock()!=Blocks.BEDROCK)return false;
        if(!mc.world.isAirBlock(pos.up())||!mc.world.isAirBlock(pos.up(2)))return false;
        return mc.world.getEntitiesWithinAABBExcludingEntity(null,new net.minecraft.util.math.AxisAlignedBB(pos.up())).isEmpty();
    }

    private double explosionDamage(double x,double y,double z,EntityLivingBase target){
        double distance=target.getDistance(x,y,z)/12D;if(distance>1D)return 0D;
        double exposure=mc.world.getBlockDensity(new Vec3d(x,y,z),target.getEntityBoundingBox());
        double impact=(1D-distance)*exposure;
        float raw=(float)((impact*impact+impact)*24D+1D);
        Explosion explosion=new Explosion(mc.world,null,x,y,z,6F,false,true);
        raw=CombatRules.getDamageAfterAbsorb(raw,target.getTotalArmorValue(),(float)target.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
        int protection=EnchantmentHelper.getEnchantmentModifierDamage(target.getArmorInventoryList(),DamageSource.causeExplosionDamage(explosion));
        raw*=1F-MathHelper.clamp(protection,0,20)/25F;
        if(target.isPotionActive(Potion.getPotionById(11)))raw*=.25F;
        return Math.max(0D,raw);
    }

    @Override protected void onDisable(){stopGap();scanDelay=0;cachedThreat=new Threat(0D,false,false);}

    private static final class Threat{
        final double damage;final boolean lethal,imminent;
        Threat(double damage,boolean lethal,boolean imminent){this.damage=damage;this.lethal=lethal;this.imminent=imminent;}
    }
}

