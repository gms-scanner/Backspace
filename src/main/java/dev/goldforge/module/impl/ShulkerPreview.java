package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tooltip preview from the item's actual BlockEntityTag. It never guesses remote contents. */
public final class ShulkerPreview extends Module {
    public int maxLines=12;
    private final LinkedHashMap<String,List<String>> cache=new LinkedHashMap<String,List<String>>(64,.75F,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,List<String>> eldest){return size()>64;}
    };
    public ShulkerPreview(){super("ShulkerPreview+","Shows known shulker contents from item NBT.",Category.PLAYER,0);
        addIntSetting("Maximum preview lines",3,27,1,()->maxLines,v->maxLines=v);}
    public void tooltip(ItemTooltipEvent event){
        ItemStack item=event.getItemStack();if(item.isEmpty()||!(item.getItem() instanceof ItemBlock)
                ||!(((ItemBlock)item.getItem()).getBlock() instanceof BlockShulkerBox))return;
        NBTTagCompound data=item.getSubCompound("BlockEntityTag");if(data==null||!data.hasKey("Items",9))return;
        String key=data.toString();List<String> lines=cache.get(key);
        if(lines==null){
            lines=new ArrayList<String>();NBTTagList items=data.getTagList("Items",10);
            for(int i=0;i<items.tagCount();i++){
                ItemStack content=new ItemStack(items.getCompoundTagAt(i));
                if(!content.isEmpty())lines.add(content.getDisplayName()+" x"+content.getCount());
            }
            cache.put(key,lines);
        }
        if(lines.isEmpty())return;
        event.getToolTip().add("§eKnown contents: "+lines.size()+" stacks");
        for(int i=0;i<Math.min(maxLines,lines.size());i++)event.getToolTip().add("§7"+lines.get(i));
        if(lines.size()>maxLines)event.getToolTip().add("§8... "+(lines.size()-maxLines)+" more");
    }
    @Override protected void onDisable(){cache.clear();}
}
