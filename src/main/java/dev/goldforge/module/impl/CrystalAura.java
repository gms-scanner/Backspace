package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.inventory.InventoryManager;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemEndCrystal;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraft.util.CombatRules;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;

/** Crystal placement and breaking with vanilla explosion exposure and self-damage limits. */
public final class CrystalAura extends Module {
    /** Profile 0 is conservative vanilla interaction pacing; profile 1 is faster HvH pacing. */
    public int profile=0, actionDelay=0, breakRange=4, aimOnly=0, targetRange=12, minDamage=4, maxSelfDamage=8, attacksPerTick=2;
    public int faceplace=1,faceplaceHealth=8,faceplaceArmorPercent=22,faceplaceMinDamage=2,friendFilter=1;
    private int cooldown;
    private long clientTicks;
    private final Map<Integer,Long> recentAttacks=new HashMap<Integer,Long>();
    private final Map<BlockPos,Long> recentPlacements=new HashMap<BlockPos,Long>();
    private BlockPos pendingPlacement,bestPlacement;
    private int pendingPlacementTicks;
    private double bestPlacementScore=Double.NEGATIVE_INFINITY;
    private int renderRefresh;
    private EntityLivingBase renderTarget;
    private List<CrystalLabel> crystalLabels=Collections.emptyList();

    public CrystalAura() {
        super("CrystalAura","Places and breaks crystals using damage, exposure, and self-safety checks.",Category.COMBAT,0);
        addIntSetting("Profile (0 NCP Strict, 1 HvH)",0,1,1,()->profile,v->profile=v);
        addIntSetting("Action delay (ticks)",0,10,1,()->actionDelay,v->actionDelay=v);
        addIntSetting("Break range",2,6,1,()->breakRange,v->breakRange=v);
        addIntSetting("Aim-only break (0/1)",0,1,1,()->aimOnly,v->aimOnly=v);
        addIntSetting("Target range",4,16,1,()->targetRange,v->targetRange=v);
        addIntSetting("Minimum damage",1,20,1,()->minDamage,v->minDamage=v);
        addIntSetting("Maximum self damage",1,20,1,()->maxSelfDamage,v->maxSelfDamage=v);
        addIntSetting("Breaks per tick",1,4,1,()->attacksPerTick,v->attacksPerTick=v);
        addIntSetting("Faceplace (0/1)",0,1,1,()->faceplace,v->faceplace=v);
        addIntSetting("Faceplace health",2,20,1,()->faceplaceHealth,v->faceplaceHealth=v);
        addIntSetting("Faceplace armor durability (%)",5,60,5,()->faceplaceArmorPercent,v->faceplaceArmorPercent=v);
        addIntSetting("Faceplace minimum damage",1,8,1,()->faceplaceMinDamage,v->faceplaceMinDamage=v);
        addIntSetting("Ignore friends (0/1)",0,1,1,()->friendFilter,v->friendFilter=v);
    }
    @Override public String hudTag(){return profile==0?"[NCP Strict]":"[HvH]";}
    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null)return;
        clientTicks++;
        if(renderRefresh--<=0){updateRenderTargets();renderRefresh=1;}
        recentAttacks.entrySet().removeIf(entry->clientTicks-entry.getValue()>8);
        recentPlacements.entrySet().removeIf(entry->clientTicks-entry.getValue()>6);
        if(cooldown>0){cooldown--;return;}
        EntityPlayerSP self=mc.player;
        List<CrystalAttack> candidates=new ArrayList<CrystalAttack>();
        for(Entity e:mc.world.loadedEntityList){
            if(!(e instanceof net.minecraft.entity.item.EntityEnderCrystal)||recentAttacks.containsKey(e.getEntityId()))continue;
            Vec3d eye=self.getPositionEyes(1.0F);
            Vec3d crystalCenter=new Vec3d(e.posX,e.posY+e.height*.5D,e.posZ);
            double reach=Math.min(breakRange,mc.playerController.getBlockReachDistance()+.15D);
            if(eye.squareDistanceTo(crystalCenter)>reach*reach)continue;
            if(aimOnly!=0&&(mc.objectMouseOver==null||mc.objectMouseOver.entityHit!=e))continue;
            double selfDmg=damage(e.posX,e.posY,e.posZ,self);
            double best=Double.NEGATIVE_INFINITY;
            for(EntityPlayer enemy:mc.world.playerEntities){
                if(!isValidTarget(enemy,self))continue;
                double dmg=damage(e.posX,e.posY,e.posZ,enemy);
                if(!safeSelfDamage(selfDmg,enemy))continue;
                double score=scoreDamage(enemy,dmg,selfDmg);
                if(dmg>=minimumDamage(enemy)&&score>best)best=score;
            }
            if(best>Double.NEGATIVE_INFINITY)candidates.add(new CrystalAttack(e,best));
        }
        Collections.sort(candidates,new Comparator<CrystalAttack>(){@Override public int compare(CrystalAttack a,CrystalAttack b){return Double.compare(b.score,a.score);}});
        int attacked=0;
        int attackLimit=profile==0?1:Math.max(1,Math.min(4,attacksPerTick));
        for(CrystalAttack candidate:candidates){
            if(attacked>=attackLimit)break;
            mc.playerController.attackEntity(self,candidate.entity);
            self.swingArm(EnumHand.MAIN_HAND);
            recentAttacks.put(candidate.entity.getEntityId(),clientTicks);
            attacked++;
        }
        if(attacked>0){cooldown=effectiveDelay();return;}
        if(waitingForPlacementAck())return;
        int crystalSlot=findCrystal(self);
        if(crystalSlot<0)return;
        BlockPos bestBase=bestPlacement;
        if(bestBase!=null&&!isValidPlacement(bestBase))bestBase=null;
        if(bestBase==null)return;
        self.inventory.currentItem=crystalSlot;
        EnumActionResult result=mc.playerController.processRightClickBlock(self,mc.world,bestBase,EnumFacing.UP,new Vec3d(bestBase.getX()+.5D,bestBase.getY()+1D,bestBase.getZ()+.5D),EnumHand.MAIN_HAND);
        if(result==EnumActionResult.SUCCESS){
            self.swingArm(EnumHand.MAIN_HAND);cooldown=effectiveDelay();
            pendingPlacement=bestBase.toImmutable();pendingPlacementTicks=0;
        }
    }

    /** Hold off on duplicate clicks while the server confirms the previous placement. */
    private boolean waitingForPlacementAck(){
        if(pendingPlacement==null)return false;
        if(hasCrystalAt(pendingPlacement)){
            recentPlacements.put(pendingPlacement,clientTicks);pendingPlacement=null;pendingPlacementTicks=0;return false;
        }
        if(++pendingPlacementTicks<2)return true;
        recentPlacements.put(pendingPlacement,clientTicks);pendingPlacement=null;pendingPlacementTicks=0;
        return false;
    }

    private boolean hasCrystalAt(BlockPos base){
        for(Entity entity:mc.world.loadedEntityList)if(entity instanceof net.minecraft.entity.item.EntityEnderCrystal&&
                Math.abs(entity.posX-(base.getX()+.5D))<.8D&&Math.abs(entity.posZ-(base.getZ()+.5D))<.8D&&entity.posY>=base.getY()+1D&&entity.posY<=base.getY()+3D)return true;
        return false;
    }

    private boolean isValidPlacement(BlockPos base){
        if(base==null||recentPlacements.containsKey(base))return false;
        net.minecraft.block.Block block=mc.world.getBlockState(base).getBlock();
        if((block!=Blocks.OBSIDIAN&&block!=Blocks.BEDROCK)||!mc.world.isAirBlock(base.up())||!mc.world.isAirBlock(base.up(2)))return false;
        if(!mc.world.getEntitiesWithinAABBExcludingEntity(null,new AxisAlignedBB(base.up())).isEmpty())return false;
        Vec3d hit=new Vec3d(base.getX()+.5D,base.getY()+1D,base.getZ()+.5D);
        double reach=mc.playerController.getBlockReachDistance();
        return mc.player.getPositionEyes(1.0F).squareDistanceTo(hit)<=reach*reach;
    }
    private int findCrystal(EntityPlayerSP p){
        int slot=InventoryManager.findItem(mc,Items.END_CRYSTAL);if(slot<0)return -1;
        if(slot<9)return slot;int preferred=p.inventory.currentItem;
        return InventoryManager.moveToHotbar(mc,slot,preferred)?preferred:-1;
    }

    private int effectiveDelay(){return profile==0?Math.max(1,actionDelay):Math.max(0,actionDelay);}

    private boolean isValidTarget(EntityPlayer target,EntityPlayerSP self){
        return target!=null&&target!=self&&!target.isDead&&target.getHealth()>0F&&self.getDistance(target)<=targetRange&&
                (friendFilter==0||!FriendManager.isFriend(target.getName()));
    }

    /** Armor-aware faceplace gate used by both crystal breaking and placement selection. */
    private double minimumDamage(EntityPlayer target){
        return faceplace!=0&&shouldFaceplace(target)?Math.min(minDamage,Math.max(1,faceplaceMinDamage)):minDamage;
    }

    private boolean shouldFaceplace(EntityPlayer target){
        if(target==null)return false;
        if(target.getHealth()+target.getAbsorptionAmount()<=faceplaceHealth)return true;
        int weakest=100;boolean hasDurableArmor=false;
        for(ItemStack armor:target.getArmorInventoryList()){
            if(armor==null||armor.isEmpty()||!armor.isItemStackDamageable()||armor.getMaxDamage()<=0)continue;
            hasDurableArmor=true;
            int remaining=(int)Math.round(100D*(armor.getMaxDamage()-armor.getItemDamage())/armor.getMaxDamage());
            weakest=Math.min(weakest,Math.max(0,remaining));
        }
        return hasDurableArmor&&weakest<=faceplaceArmorPercent;
    }

    private double scoreDamage(EntityPlayer target,double dealt,double selfDamage){
        if(target==null)return Double.NEGATIVE_INFINITY;
        double health=target.getHealth()+target.getAbsorptionAmount();
        double score=dealt-selfDamage*.72D;
        if(dealt>=health)score+=100D;
        else if(dealt>=target.getHealth())score+=18D;
        if(shouldFaceplace(target))score+=6D;
        score-=mc.player==null?0D:mc.player.getDistance(target)*.035D;
        return score;
    }

    private boolean safeSelfDamage(double selfDamage,EntityPlayer target){
        if(selfDamage<=maxSelfDamage)return true;
        if(profile==0)return false;
        double ownHealth=mc.player.getHealth()+mc.player.getAbsorptionAmount();
        double targetHealth=target.getHealth()+target.getAbsorptionAmount();
        // HvH may accept a pop trade, but never choose a lethal self-hit.
        return selfDamage<ownHealth-1D&&selfDamage>=targetHealth;
    }

    private double damage(double x,double y,double z,EntityLivingBase target){
        double distance=target.getDistance(x,y,z)/12.0D;if(distance>1)return 0;
        double exposure=mc.world.getBlockDensity(new Vec3d(x,y,z),target.getEntityBoundingBox());
        double impact=(1-distance)*exposure;
        float raw=(float)(((impact*impact+impact)*24.0D)+1.0D);
        Explosion explosion=new Explosion(mc.world,null,x,y,z,6.0F,false,true);
        raw=CombatRules.getDamageAfterAbsorb(raw,target.getTotalArmorValue(),(float)target.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
        int prot=EnchantmentHelper.getEnchantmentModifierDamage(target.getArmorInventoryList(),DamageSource.causeExplosionDamage(explosion));
        raw*=1.0F-MathHelper.clamp(prot,0,20)/25.0F;
        if(target.isPotionActive(Potion.getPotionById(11)))raw*=.25F;
        return Math.max(0,raw);
    }

    private void updateRenderTargets(){
        List<CrystalLabel> labels=new ArrayList<CrystalLabel>();
        EntityLivingBase bestTarget=null;double bestTargetScore=Double.NEGATIVE_INFINITY;
        bestPlacement=null;bestPlacementScore=Double.NEGATIVE_INFINITY;
        for(Entity entity:mc.world.loadedEntityList){
            if(!(entity instanceof net.minecraft.entity.item.EntityEnderCrystal)||mc.player.getDistance(entity)>12.0F)continue;
            EntityPlayer bestPlayer=null;double bestDamage=0D,bestScore=Double.NEGATIVE_INFINITY;
            for(EntityPlayer player:mc.world.playerEntities){
                if(!isValidTarget(player,mc.player))continue;
                double value=damage(entity.posX,entity.posY,entity.posZ,player);
                double score=scoreDamage(player,value,damage(entity.posX,entity.posY,entity.posZ,mc.player));
                if(value>=minimumDamage(player)&&score>bestScore){bestDamage=value;bestScore=score;bestPlayer=player;}
            }
            if(bestPlayer!=null){
                labels.add(new CrystalLabel(entity,null,bestDamage,false));
                if(bestScore>bestTargetScore){bestTargetScore=bestScore;bestTarget=bestPlayer;}
            }
        }
        List<CrystalLabel> placements=new ArrayList<CrystalLabel>();
        BlockPos origin=new BlockPos(mc.player.posX,mc.player.posY,mc.player.posZ);
        int range=Math.min(4,targetRange);
        for(BlockPos base:BlockPos.getAllInBox(origin.add(-range,-2,-range),origin.add(range,2,range))){
            if(mc.player.getDistanceSqToCenter(base)>range*range)continue;
            net.minecraft.block.Block block=mc.world.getBlockState(base).getBlock();
            if(block!=Blocks.OBSIDIAN&&block!=Blocks.BEDROCK)continue;
            if(recentPlacements.containsKey(base))continue;
            if(!mc.world.isAirBlock(base.up())||!mc.world.isAirBlock(base.up(2)))continue;
            AxisAlignedBB collision=new AxisAlignedBB(base.up());
            if(!mc.world.getEntitiesWithinAABBExcludingEntity(null,collision).isEmpty())continue;
            double ex=base.getX()+.5D,ey=base.getY()+1D,ez=base.getZ()+.5D;
            Vec3d clickPoint=new Vec3d(ex,ey,ez);double placeReach=mc.playerController.getBlockReachDistance();
            if(mc.player.getPositionEyes(1.0F).squareDistanceTo(clickPoint)>placeReach*placeReach)continue;
            double selfDamage=damage(ex,ey,ez,mc.player);
            EntityPlayer bestPlayer=null;double bestDamage=0D,bestScore=Double.NEGATIVE_INFINITY;
            for(EntityPlayer player:mc.world.playerEntities){
                if(!isValidTarget(player,mc.player))continue;
                if(!safeSelfDamage(selfDamage,player))continue;
                double value=damage(ex,ey,ez,player);
                double score=scoreDamage(player,value,selfDamage);
                if(value>=minimumDamage(player)&&score>bestScore){bestDamage=value;bestPlayer=player;bestScore=score;}
            }
            if(bestPlayer!=null){
                placements.add(new CrystalLabel(null,base.toImmutable(),bestDamage,true));
                double score=scoreDamage(bestPlayer,bestDamage,selfDamage)-Math.sqrt(mc.player.getDistanceSqToCenter(base))*.05;
                if(score>bestPlacementScore){bestPlacementScore=score;bestPlacement=base.toImmutable();}
                if(score>bestTargetScore){bestTargetScore=score;bestTarget=bestPlayer;}
            }
        }
        Collections.sort(placements,new Comparator<CrystalLabel>(){@Override public int compare(CrystalLabel a,CrystalLabel b){return Double.compare(b.damage,a.damage);}});
        for(int i=0;i<Math.min(3,placements.size());i++)labels.add(placements.get(i));
        crystalLabels=labels;renderTarget=bestTarget;
    }

    @Override protected void onDisable(){pendingPlacement=null;pendingPlacementTicks=0;recentAttacks.clear();recentPlacements.clear();bestPlacement=null;crystalLabels=Collections.emptyList();}

    @Override public void render(float partialTicks){
        if(mc.player==null||mc.world==null)return;
        RenderManager manager=mc.getRenderManager();
        EntityLivingBase target=renderTarget;
        if(target!=null&&target!=mc.player&&!target.isDead){
            double ix=target.lastTickPosX+(target.posX-target.lastTickPosX)*partialTicks;
            double iy=target.lastTickPosY+(target.posY-target.lastTickPosY)*partialTicks;
            double iz=target.lastTickPosZ+(target.posZ-target.lastTickPosZ)*partialTicks;
            net.minecraft.util.math.AxisAlignedBB box=target.getEntityBoundingBox().offset(ix-target.posX-manager.viewerPosX,iy-target.posY-manager.viewerPosY,iz-target.posZ-manager.viewerPosZ);
            GlStateManager.pushMatrix();GlStateManager.enableBlend();GlStateManager.disableTexture2D();GlStateManager.disableDepth();GlStateManager.depthMask(false);
            GL11.glLineWidth(2.0F);RenderGlobal.drawSelectionBoundingBox(box,1.0F,0.84F,0.0F,0.95F);
            GlStateManager.depthMask(true);GlStateManager.enableDepth();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();
        }
        for(CrystalLabel label:crystalLabels){
            double x,y,z;AxisAlignedBB bounds;
            if(label.placement){
                BlockPos base=label.base;
                x=base.getX()+.5D;y=base.getY()+2.1D;z=base.getZ()+.5D;
                bounds=new AxisAlignedBB(base.getX()+.08D,base.getY()+1.02D,base.getZ()+.08D,base.getX()+.92D,base.getY()+2.92D,base.getZ()+.92D);
            }else{
                Entity crystal=label.crystal;
                if(crystal.isDead||mc.player.getDistance(crystal)>16.0F)continue;
                x=crystal.lastTickPosX+(crystal.posX-crystal.lastTickPosX)*partialTicks;
                y=crystal.lastTickPosY+(crystal.posY-crystal.lastTickPosY)*partialTicks+crystal.height+.25D;
                z=crystal.lastTickPosZ+(crystal.posZ-crystal.lastTickPosZ)*partialTicks;
                bounds=crystal.getEntityBoundingBox().grow(.08D,.08D,.08D);
            }
            drawCrystalGlow(bounds,manager);
            drawDamageLabel(manager,x,y,z,String.format(Locale.US,"%.1f",label.damage));
        }
    }

    private void drawCrystalGlow(AxisAlignedBB worldBounds,RenderManager manager){
        AxisAlignedBB box=worldBounds.offset(-manager.viewerPosX,-manager.viewerPosY,-manager.viewerPosZ);
        GlStateManager.pushMatrix();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        GlStateManager.disableTexture2D();GlStateManager.disableCull();GlStateManager.depthMask(false);
        RenderGlobal.renderFilledBox(box,1.0F,.84F,0.0F,.20F);
        GL11.glLineWidth(2.0F);RenderGlobal.drawSelectionBoundingBox(box,1.0F,.84F,0.0F,.95F);
        GlStateManager.depthMask(true);GlStateManager.enableCull();GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();
    }

    private void drawDamageLabel(RenderManager manager,double x,double y,double z,String value){
        GlStateManager.pushMatrix();
        GlStateManager.translate(x-manager.viewerPosX,y-manager.viewerPosY,z-manager.viewerPosZ);
        GlStateManager.rotate(-manager.playerViewY,0.0F,1.0F,0.0F);
        GlStateManager.rotate(manager.options.thirdPersonView==2?-manager.playerViewX:manager.playerViewX,1.0F,0.0F,0.0F);
        GlStateManager.scale(-0.03F,-0.03F,0.03F);
        GlStateManager.disableLighting();GlStateManager.depthMask(false);GlStateManager.disableDepth();GlStateManager.enableBlend();
        int width=mc.fontRenderer.getStringWidth(value);
        Gui.drawRect(-width/2-5,-3,width/2+5,mc.fontRenderer.FONT_HEIGHT+2,0xEE050505);
        mc.fontRenderer.drawStringWithShadow(value,-width/2,0,0xFFFFFF00);
        GlStateManager.enableDepth();GlStateManager.depthMask(true);GlStateManager.disableBlend();GlStateManager.enableLighting();GlStateManager.color(1,1,1,1);
        GlStateManager.popMatrix();
    }

    private static final class CrystalAttack { final Entity entity;final double score;CrystalAttack(Entity e,double score){this.entity=e;this.score=score;} }
    private static final class CrystalLabel { final Entity crystal;final BlockPos base;final double damage;final boolean placement;CrystalLabel(Entity e,BlockPos b,double d,boolean p){crystal=e;base=b;damage=d;placement=p;} }
}
