package dev.goldforge.module.impl;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.Block;
import java.util.HashSet;
import java.util.Set;
public final class BlockFilter extends Module {
    public String blocks="minecraft:bedrock";
    public int whitelist=0;
    private static volatile Set<String> selected=new HashSet<String>();
    private static volatile boolean active,allowList;
    private String cached="";
    public BlockFilter(){super("BlockFilter","Filters shared block selection. Edit IDs with /moduleconfig BlockFilter.",Category.MISC,0);
        addIntSetting("Whitelist (0 blacklist, 1 whitelist)",0,1,1,()->whitelist,v->whitelist=v);}
    @Override protected void onEnable(){cached="";tick();}
    @Override public void tick(){
        String value=blocks==null?"":blocks;
        if(!value.equals(cached)){
            Set<String> next=new HashSet<String>();
            for(String id:value.split("[,;\\s]+"))if(!id.isEmpty())next.add(id.contains(":")?id:"minecraft:"+id);
            selected=next;cached=value;
        }
        allowList=whitelist!=0;active=true;
    }
    public static boolean allows(Block block){
        if(!active)return true;
        String id=String.valueOf(block.getRegistryName());
        return allowList==selected.contains(id);
    }
    @Override protected void onDisable(){active=false;}
    @Override protected void onWorldUnload(){active=false;}
}
