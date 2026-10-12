package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/** Removes the client's right-click repeat delay for selected item classes. */
public final class FastUse extends Module {
    public int consumables=1,bows=1,blocks=0;
    private static final Field CLICK_DELAY=findField("rightClickDelayTimer","field_71467_ac");
    public FastUse(){
        super("FastUse","Reduces client right-click repeat delay for selected item categories.",Category.PLAYER,0);
        addIntSetting("Consumables (0/1)",0,1,1,()->consumables,v->consumables=v);
        addIntSetting("Bows (0/1)",0,1,1,()->bows,v->bows=v);
        addIntSetting("Block placement (0/1)",0,1,1,()->blocks,v->blocks=v);
    }
    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.currentScreen!=null||CLICK_DELAY==null||!mc.gameSettings.keyBindUseItem.isKeyDown())return;
        ItemStack stack=mc.player.getHeldItemMainhand();if(stack.isEmpty())return;
        boolean eligible=(consumables!=0&&(stack.getItem() instanceof ItemFood||stack.getItem() instanceof ItemPotion))
                ||(bows!=0&&stack.getItem() instanceof ItemBow)||(blocks!=0&&stack.getItem() instanceof ItemBlock);
        if(eligible)try{CLICK_DELAY.setInt(mc,0);}catch(IllegalAccessException failure){throw new IllegalStateException("Could not reset 1.12.2 use delay",failure);}
    }
    private static Field findField(String name,String srg){try{return ReflectionHelper.findField(net.minecraft.client.Minecraft.class,name,srg);}catch(Exception failure){return null;}}
}
