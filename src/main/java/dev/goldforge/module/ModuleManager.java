package dev.goldforge.module;

import dev.goldforge.module.impl.*;
import dev.goldforge.network.PhasePacketObserver;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import dev.goldforge.render.BackspaceCapeLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;

public final class ModuleManager {
    private static final Logger LOGGER=LogManager.getLogger("Backspace/Modules");
    private final List<Module> modules = new ArrayList<Module>();
    private final Map<Category,List<Module>> byCategory = new EnumMap<Category,List<Module>>(Category.class);
    private final Map<String,Long> timings=new LinkedHashMap<String,Long>();
    private final Map<String,TimingWindow> timingWindows=new LinkedHashMap<String,TimingWindow>();
    private final Set<RenderPlayer> capeLayerRenderers=Collections.newSetFromMap(new WeakHashMap<RenderPlayer,Boolean>());
    private boolean resumePending;
    public ModuleManager() {
        modules.add(new TreeBurner()); modules.add(new Staircase()); modules.add(new Speed()); modules.add(new NoSlow()); modules.add(new AutoSprint()); modules.add(new SafeWalk()); modules.add(new Step()); modules.add(new Phase()); modules.add(new PacketFly()); modules.add(new ElytraFly()); modules.add(new NoFall()); modules.add(new GroundSpoof()); modules.add(new AntiLevitate()); modules.add(new PacketFlyLab()); modules.add(new AutoWither()); modules.add(new AutoFire()); modules.add(new AutoTNT()); modules.add(new AutoTorch()); modules.add(new LavaCast()); modules.add(new Freecam());
        modules.add(new CustomChat()); modules.add(new AutoReconnect()); modules.add(new PingSpoof()); modules.add(new WhisperSpam()); modules.add(new BoatPhase()); modules.add(new InventoryHud());
        modules.add(new AutoNuker()); modules.add(new AutoLava()); modules.add(new CrystalAura()); modules.add(new KillAura()); modules.add(new AutoCity()); modules.add(new AutoMine()); modules.add(new HoleFill()); modules.add(new WebTrap()); modules.add(new AutoTrap()); modules.add(new AntiWeb()); modules.add(new AutoPot()); modules.add(new TriggerBot()); modules.add(new ShieldBreak()); modules.add(new NoHitDelay()); modules.add(new WTap()); modules.add(new BowAssist()); modules.add(new AutoWeapon()); modules.add(new Criticals()); modules.add(new Velocity()); modules.add(new AutoTotem()); modules.add(new AutoEat()); modules.add(new AutoPearl()); modules.add(new AutoFish()); modules.add(new AutoMend()); modules.add(new AutoShear()); modules.add(new DurabilityAlert()); modules.add(new AutoWalk()); modules.add(new AutoTool()); modules.add(new AutoArmor()); modules.add(new AutoRefill()); modules.add(new AutoRespawn()); modules.add(new AutoLog()); modules.add(new FakePlayer()); modules.add(new AutoBanner()); modules.add(new Scaffold());
        modules.add(new ChunkBanHelper()); modules.add(new BookBanHelper()); modules.add(new AutoSign()); modules.add(new FastBreak()); modules.add(new Printer());
        modules.add(new FastUse()); modules.add(new CraftCarry());
        modules.add(new Surround()); modules.add(new SelfTrap()); modules.add(new WitherPreview()); modules.add(new BaseFinder()); modules.add(new HudArrayList()); modules.add(new NoRender());
        modules.add(new Snapper()); modules.add(new PlayerESP()); modules.add(new Nametags()); modules.add(new Radar()); modules.add(new ColorEnchant()); modules.add(new ShaderFX()); modules.add(new Hands()); modules.add(new KillFX()); modules.add(new WaypointESP()); modules.add(new HudInfo()); modules.add(new LagWatch()); modules.add(new BlockESP()); modules.add(new BreakESP()); modules.add(new PopChams()); modules.add(new LogoutSpots()); modules.add(new PearlTrace()); modules.add(new Fullbright()); modules.add(new Cape()); modules.add(new NewChunks());
        modules.add(new Jesus()); modules.add(new DIESP());
        for(Category category:Category.values())byCategory.put(category,new ArrayList<Module>());
        for(Module module:modules)byCategory.get(module.category).add(module);
        for(Category category:Category.values())byCategory.put(category,Collections.unmodifiableList(byCategory.get(category)));
    }
    public List<Module> all() { return Collections.unmodifiableList(modules); }
    public List<Module> in(Category category) { List<Module> result=byCategory.get(category);return result==null?Collections.<Module>emptyList():result; }
    public Module get(String name) { for (Module m : modules) if (m.name.equalsIgnoreCase(name)) return m; return null; }
    public void onKey(int key) { for (Module m : modules) if (key != 0 && key == m.key) safe(m,"key-toggle",new Runnable(){@Override public void run(){m.toggle();}}); }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc=Minecraft.getMinecraft();
            PhasePacketObserver.ensureInstalled();
            MovementManager.beginClientTick();
            if(mc.player!=null&&mc.world!=null){
                dev.goldforge.ConfigManager.applyServerProfileIfAvailable();
                dev.goldforge.ConfigManager.restoreEnabled();
                if(resumePending){for(Module module:modules)module.resumeAfterWorldChange();resumePending=false;}
            }
            for (Module m : modules) if (m.enabled&&!m.isSuspended()) safe(m,"tick",new Runnable(){@Override public void run(){m.tick();}});
            MovementManager.commitClientTick();
            MovementReplay.tick();
        }
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        for (final Module m : modules) if (m.enabled&&!m.isSuspended()) {
            safe(m,"render",new Runnable(){@Override public void run(){
                if (m instanceof WitherPreview) ((WitherPreview)m).render(event);
                else m.render(event.getPartialTicks());
            }});
        }
        MovementReplay.render(event);
    }
    @SubscribeEvent public void installCapeLayer(RenderPlayerEvent.Pre event){
        if(!(event.getEntityPlayer() instanceof net.minecraft.client.entity.AbstractClientPlayer)
                ||event.getEntityPlayer()!=Minecraft.getMinecraft().player)return;
        Module module=get("Cape");
        if(!(module instanceof Cape)||!module.enabled||module.isSuspended())return;
        RenderPlayer renderer=event.getRenderer();
        if(capeLayerRenderers.add(renderer))renderer.addLayer(new BackspaceCapeLayer(renderer));
    }
    @SubscribeEvent public void specificHand(final RenderSpecificHandEvent event){
        if(Minecraft.getMinecraft().currentScreen instanceof dev.goldforge.gui.HandsEditor){
            event.setCanceled(true);
            return;
        }
        final Module module=get("ShaderFX");
        if(module instanceof ShaderFX&&module.enabled&&!module.isSuspended())safe(module,"hand-mask",new Runnable(){@Override public void run(){((ShaderFX)module).onSpecificHandPre(event);}});
        final Module hands=get("Hands");
        if(hands instanceof Hands&&hands.enabled&&!hands.isSuspended()&&!event.isCanceled())safe(hands,"hand-transform",new Runnable(){@Override public void run(){((Hands)hands).onSpecificHand(event);}});
    }
    @SubscribeEvent public void inputUpdate(InputUpdateEvent event) {
        for(final Module module:modules) if(module.enabled&&!module.isSuspended())safe(module,"input",new Runnable(){@Override public void run(){module.onInputUpdate(event);}});
    }
    @SubscribeEvent public void cameraSetup(final net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup event) {
        final Module module=get("Freecam");
        if(module instanceof Freecam&&module.enabled&&!module.isSuspended())safe(module,"camera",new Runnable(){@Override public void run(){((Freecam)module).onCameraSetup(event);}});
    }
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post event) {
        for(final Module module:modules) if(module.enabled&&!module.isSuspended())safe(module,"overlay",new Runnable(){@Override public void run(){module.onOverlay(event);}});
    }
    @SubscribeEvent public void overlayPre(RenderGameOverlayEvent.Pre event) {
        for(final Module module:modules) if(module.enabled&&!module.isSuspended())safe(module,"overlay-pre",new Runnable(){@Override public void run(){module.onOverlayPre(event);}});
    }
    @SubscribeEvent public void worldUnload(WorldEvent.Unload event){if(event.getWorld()!=null&&event.getWorld().isRemote){MovementManager.clear();MovementReplay.worldChanged();for(Module module:modules)module.suspendForWorldChange();}}
    @SubscribeEvent public void worldLoad(WorldEvent.Load event){if(event.getWorld()!=null&&event.getWorld().isRemote)resumePending=true;}
    @SubscribeEvent public void chunkLoad(ChunkEvent.Load event){if(event.getWorld()==null||!event.getWorld().isRemote)return;Module module=get("NewChunks");if(module instanceof NewChunks&&module.enabled&&!module.isSuspended())safe(module,"chunk-load",new Runnable(){@Override public void run(){((NewChunks)module).onChunkLoad(event.getChunk());}});}
    @SubscribeEvent public void sound(net.minecraftforge.client.event.sound.PlaySoundEvent event) {
        Module module=get("NoRender");
        if(module instanceof NoRender && module.enabled)safe(module,"sound",new Runnable(){@Override public void run(){((NoRender)module).onSound(event);}});
    }
    @SubscribeEvent public void clientChat(ClientChatReceivedEvent event){
        final String message=event.getMessage().getUnformattedText();
        Module phase=get("Phase");if(phase instanceof Phase&&phase.enabled&&!phase.isSuspended())safe(phase,"chat-diagnostic",new Runnable(){@Override public void run(){((Phase)phase).onChatMessage(message);}});
        final Module packetFly=get("PacketFlyLab");if(packetFly instanceof PacketFlyLab&&packetFly.enabled&&!packetFly.isSuspended())safe(packetFly,"ncp-chat",new Runnable(){@Override public void run(){PacketFlyLab.recordNcpMessage(message);}});
    }
    @SubscribeEvent public void guiOpen(GuiOpenEvent event){
        Module module=get("AutoSign");if(module instanceof AutoSign&&module.enabled&&!module.isSuspended()){((AutoSign)module).changed(event);((AutoSign)module).screenOpened(event);}
    }
    @SubscribeEvent public void guiInit(GuiScreenEvent.InitGuiEvent.Post event){
        Module module=get("AutoSign");if(module instanceof AutoSign&&module.enabled&&!module.isSuspended())((AutoSign)module).init(event);
    }
    @SubscribeEvent public void livingSpecials(RenderLivingEvent.Specials.Pre event){
        Module shader=get("ShaderFX");
        if(shader instanceof ShaderFX&&shader.enabled&&((ShaderFX)shader).isCapturing()){
            event.setCanceled(true);return;
        }
        Module module=get("Nametags");if(module instanceof Nametags&&module.enabled&&!module.isSuspended())((Nametags)module).cancelVanilla(event);
    }
    @SubscribeEvent public void livingDeath(final LivingDeathEvent event){
        final Module module=get("KillFX");
        if(module instanceof KillFX&&module.enabled&&!module.isSuspended())safe(module,"living-death",new Runnable(){@Override public void run(){((KillFX)module).onLivingDeath(event);}});
    }
    @SubscribeEvent public void attackEntity(final AttackEntityEvent event){
        final Module module=get("WTap");
        if(module instanceof WTap&&module.enabled&&!module.isSuspended())safe(module,"attack-reset",new Runnable(){@Override public void run(){((WTap)module).onAttack(event);}});
    }

    public Map<String,Long> recentTimingsNanos(){return Collections.unmodifiableMap(new LinkedHashMap<String,Long>(timings));}
    public Map<String,Long> averageTimingsNanos(){Map<String,Long> result=new LinkedHashMap<String,Long>();for(Map.Entry<String,TimingWindow> entry:timingWindows.entrySet())result.put(entry.getKey(),entry.getValue().average());return Collections.unmodifiableMap(result);}

    private void safe(Module module,String hook,Runnable action){
        long start=System.nanoTime();
        try{action.run();}
        catch(VirtualMachineError fatal){throw fatal;}
        catch(ThreadDeath fatal){throw fatal;}
        catch(Throwable failure){
            reportFailure(module,hook,failure);
            module.setEnabled(false);
        }finally{long elapsed=System.nanoTime()-start;String key=module.name+"."+hook;timings.put(key,elapsed);TimingWindow window=timingWindows.get(key);if(window==null){window=new TimingWindow();timingWindows.put(key,window);}window.add(elapsed);}
    }

    private static final class TimingWindow{
        private final long[] samples=new long[32];private int cursor,count;private long sum;
        void add(long sample){if(count==samples.length)sum-=samples[cursor];else count++;samples[cursor]=sample;sum+=sample;cursor=(cursor+1)%samples.length;}
        long average(){return count==0?0:sum/count;}
    }

    public static void reportFailure(Module module,String hook,Throwable failure){
        Minecraft mc=Minecraft.getMinecraft();
        String screen=mc.currentScreen==null?"none":mc.currentScreen.getClass().getSimpleName();
        String player=mc.player==null?"none":mc.player.getName()+" @ "+String.format(Locale.ROOT,"%.1f, %.1f, %.1f",mc.player.posX,mc.player.posY,mc.player.posZ);
        LOGGER.error("Module {} failed during {} (screen={}, world={}, player={})",module==null?"unknown":module.name,hook,screen,mc.world==null?"none":mc.world.getWorldInfo().getWorldName(),player,failure);
    }
}
