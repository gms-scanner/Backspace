package dev.goldforge.module;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public abstract class Module {
    protected final Minecraft mc = Minecraft.getMinecraft();
    public final String name, description;
    public final Category category;
    public boolean enabled;
    public int key;
    private boolean suspended;
    private final List<ModuleSetting> settings = new ArrayList<ModuleSetting>();
    protected Module(String name, String description, Category category, int key) {
        this.name = name; this.description = description; this.category = category; this.key = key;
    }
    public final void setEnabled(boolean value) {
        if(value==enabled)return;
        if(value){
            enabled=true;suspended=false;
            try{onEnable();}
            catch(VirtualMachineError fatal){enabled=false;throw fatal;}
            catch(ThreadDeath fatal){enabled=false;throw fatal;}
            catch(Throwable failure){
                enabled=false;
                try{onDisable();}catch(Throwable cleanup){failure.addSuppressed(cleanup);}
                ModuleManager.reportFailure(this,"enable",failure);
            }
        }else{
            enabled=false;
            if(!suspended)try{onDisable();}catch(Throwable failure){ModuleManager.reportFailure(this,"disable",failure);}
            suspended=false;
        }
        dev.goldforge.ConfigManager.save();
    }
    public void setKey(int value) { key=value; dev.goldforge.ConfigManager.save(); }
    public void toggle() { setEnabled(!enabled); }
    protected void onEnable() {}
    protected void onDisable() {}
    protected void onWorldUnload() { onDisable(); }
    protected void onWorldLoad() { onEnable(); }
    public void tick() {}
    public void render(float partialTicks) {}
    public void onInputUpdate(InputUpdateEvent event) {}
    public void onOverlay(RenderGameOverlayEvent.Post event) {}
    public void onOverlayPre(RenderGameOverlayEvent.Pre event) {}
    public String hudTag() { return ""; }
    public final boolean isSuspended(){return suspended;}
    final void suspendForWorldChange(){
        if(!enabled||suspended)return;
        suspended=true;
        try{onWorldUnload();}catch(Throwable failure){ModuleManager.reportFailure(this,"world-unload",failure);setEnabled(false);}
    }
    final void resumeAfterWorldChange(){
        if(!enabled||!suspended)return;
        try{onWorldLoad();suspended=false;}catch(Throwable failure){ModuleManager.reportFailure(this,"world-load",failure);setEnabled(false);}
    }
    protected final void addIntSetting(String name,int min,int max,int step,IntSupplier getter,IntConsumer setter) {
        settings.add(new ModuleSetting(name,min,max,step,getter,setter));
    }
    protected final void addDoubleSetting(String name,double min,double max,double step,
                                          DoubleSupplier getter,DoubleConsumer setter) {
        settings.add(new ModuleSetting(name,min,max,step,getter,setter));
    }
    public final List<ModuleSetting> getSettings() { return Collections.unmodifiableList(settings); }
}

