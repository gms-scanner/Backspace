package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Field;

/** Clears the local attack click gate while an end crystal is targeted. */
public final class NoHitDelay extends Module {
    private static final Field LEFT_CLICK_COUNTER=findField();
    public NoHitDelay(){super("NoHitDelay","Removes the local left-click gate while attacking an end crystal.",Category.COMBAT,0);}
    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.currentScreen!=null||LEFT_CLICK_COUNTER==null||!mc.gameSettings.keyBindAttack.isKeyDown())return;
        if(mc.objectMouseOver!=null&&mc.objectMouseOver.entityHit instanceof EntityEnderCrystal){
            try{LEFT_CLICK_COUNTER.setInt(mc,0);}catch(IllegalAccessException failure){throw new IllegalStateException("Could not reset 1.12.2 attack delay",failure);}
        }
    }
    private static Field findField(){try{return ReflectionHelper.findField(net.minecraft.client.Minecraft.class,"leftClickCounter","field_71429_W");}catch(Exception failure){return null;}}
}
