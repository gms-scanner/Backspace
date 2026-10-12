package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/** Accelerates the normal controller's held-block mining and removes its inter-block delay. */
public final class FastBreak extends Module {
    public int survival=1,creative=1,boostPercent=50;
    private static final Field BLOCK_HIT_DELAY=findField("blockHitDelay","field_78781_i");
    private static final Field BLOCK_DAMAGE=findField("curBlockDamageMP","field_78770_f");

    public FastBreak(){
        super("FastBreak","Speeds held block breaking in Survival and removes Creative block-hit delay.",Category.WORLD,0);
        addIntSetting("Survival (0/1)",0,1,1,()->survival,v->survival=v);
        addIntSetting("Creative (0/1)",0,1,1,()->creative,v->creative=v);
        addIntSetting("Survival boost (%)",5,100,5,()->boostPercent,v->boostPercent=v);
    }

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||mc.currentScreen!=null
                ||!mc.gameSettings.keyBindAttack.isKeyDown()||mc.objectMouseOver==null
                ||mc.objectMouseOver.typeOfHit!=RayTraceResult.Type.BLOCK)return;
        BlockPos pos=mc.objectMouseOver.getBlockPos();if(pos==null)return;
        IBlockState state=mc.world.getBlockState(pos);if(state.getMaterial().isReplaceable()||state.getBlockHardness(mc.world,pos)<0F)return;
        GameType gameType=mc.playerController.getCurrentGameType();
        try{
            if(gameType==GameType.CREATIVE){if(creative!=0)setInt(BLOCK_HIT_DELAY,mc.playerController,0);return;}
            if(gameType!=GameType.SURVIVAL||survival==0)return;
            setInt(BLOCK_HIT_DELAY,mc.playerController,0);
            float progress=BLOCK_DAMAGE==null?0F:BLOCK_DAMAGE.getFloat(mc.playerController);
            // Leave the controller just below completion. Vanilla's next damage tick
            // sends the ordinary STOP_DESTROY_BLOCK packet and applies server state.
            float boosted=Math.min(.99F,progress+Math.max(5,Math.min(100,boostPercent))/100F);
            setFloat(BLOCK_DAMAGE,mc.playerController,boosted);
        }catch(IllegalAccessException failure){throw new IllegalStateException("Could not update the 1.12.2 block-breaking controller",failure);}
    }

    private static void setInt(Field field,Object target,int value)throws IllegalAccessException{if(field==null)throw new IllegalStateException("PlayerControllerMP block-hit delay field unavailable");field.setInt(target,value);}
    private static void setFloat(Field field,Object target,float value)throws IllegalAccessException{if(field==null)throw new IllegalStateException("PlayerControllerMP block-damage field unavailable");field.setFloat(target,value);}
    private static Field findField(String name,String srg){try{return ReflectionHelper.findField(PlayerControllerMP.class,name,srg);}catch(Exception failure){return null;}}
}
