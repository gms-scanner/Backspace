package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

/** Populates the vanilla sign editor and optionally submits through its normal close path. */
public final class AutoSign extends Module {
    public String line1="Backspace",line2="",line3="",line4="";
    public int autoSubmit=0;
    private static final Field SIGN_FIELD=findSignField();
    private final Map<GuiEditSign,Integer> pending=new IdentityHashMap<GuiEditSign,Integer>();
    private GuiEditSign scheduled;
    private int submitDelay;

    public AutoSign(){
        super("AutoSign","Fills sign editor lines with configurable text and optionally submits.",Category.WORLD,0);
        addIntSetting("Auto submit (0/1)",0,1,1,()->autoSubmit,v->autoSubmit=v);
    }
    public void screenOpened(GuiOpenEvent event){
        if(!(event.getGui() instanceof GuiEditSign))return;
        GuiEditSign screen=(GuiEditSign)event.getGui();
        pending.put(screen,0);scheduled=screen;submitDelay=0;
    }
    public void init(GuiScreenEvent.InitGuiEvent.Post event){
        if(!(event.getGui() instanceof GuiEditSign))return;
        GuiEditSign screen=(GuiEditSign)event.getGui();
        if(!pending.containsKey(screen))pending.put(screen,0);
        fill(screen);
        if(autoSubmit!=0){scheduled=screen;submitDelay=1;}
    }
    @Override public void tick(){
        if(scheduled==null)return;
        if(mc.currentScreen!=scheduled){pending.remove(scheduled);scheduled=null;return;}
        if(submitDelay-->0)return;
        GuiEditSign screen=scheduled;scheduled=null;submitDelay=0;
        fill(screen);
        // GuiEditSign.onGuiClosed sends the version-correct CPacketUpdateSign.
        mc.displayGuiScreen(null);
    }
    public void changed(GuiOpenEvent event){
        GuiEditSign old=mc.currentScreen instanceof GuiEditSign?(GuiEditSign)mc.currentScreen:null;
        if(old!=null&&old!=event.getGui())pending.remove(old);
    }
    @Override protected void onDisable(){pending.clear();scheduled=null;submitDelay=0;}
    private void fill(GuiEditSign screen){
        if(screen==null||mc.world==null||mc.player==null||SIGN_FIELD==null)return;
        try{
            TileEntitySign sign=(TileEntitySign)SIGN_FIELD.get(screen);
            if(sign==null||sign.isInvalid())return;
            sign.signText[0]=component(line1);sign.signText[1]=component(line2);
            sign.signText[2]=component(line3);sign.signText[3]=component(line4);
        }catch(IllegalAccessException failure){throw new IllegalStateException("Could not access the 1.12.2 sign editor tile",failure);}
    }
    private static TextComponentString component(String text){return new TextComponentString(text==null?"":text);}
    private static Field findSignField(){
        for(Field field:GuiEditSign.class.getDeclaredFields())if(TileEntitySign.class.isAssignableFrom(field.getType())){field.setAccessible(true);return field;}
        return null;
    }
}
