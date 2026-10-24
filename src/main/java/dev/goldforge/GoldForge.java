package dev.goldforge;

import dev.goldforge.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;

@Mod(modid = GoldForge.MODID, name = GoldForge.NAME, version = GoldForge.VERSION, acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:oneconfig")
public final class GoldForge {
    public static final String MODID = "goldforge";
    public static final String NAME = "Backspace";
    public static final String VERSION = "dev-4.0-beta";
    public static final ModuleManager MODULES = new ModuleManager();
    private static final Field SPLASH_FIELD=findSplashField();
    private static final String[] CUSTOM_SPLASHES={
        splash(TextFormatting.GOLD,"Glory to the 888!"),
        splash(TextFormatting.DARK_GRAY,"Charkholl was here"),
        splash(TextFormatting.GREEN,"Backspace, a W client."),
        splash(TextFormatting.RED,"Anarchy forever!"),
        splash(TextFormatting.DARK_PURPLE,"2f2f.me"),
        splash(TextFormatting.DARK_GRAY,"opsec :)"),
        splash(TextFormatting.RED,"NF will never know the meta"),
        "the password is...","if you saw this, dms are open","do not press backspace",
        "Total B.S! (Backspace)","Built in server seeker mayb.",splash(TextFormatting.GREEN,"Now with NoCom!"),
        "Did you see the message before?","nothing was logged.",splash(TextFormatting.RED,"We logged. EVERYTHING!"),
        "check the chest :)","wrong version, sorry","please remain calm",splash(TextFormatting.YELLOW,"You are using a unofficial copy!"),
        splash(TextFormatting.RED,"Please unplug the USB drive."),"there is no backspace team","Only Indie :) No team.",
        "made somewhere on the internet","compiled at 03:33",splash(TextFormatting.DARK_RED,"sinister"),
        splash(TextFormatting.DARK_PURPLE,"333 larp"),"probably undetected","probably","Reminder: Trust no one.",
        "the changelog was deleted","ask nobody","don't ask why","Ambatu-",splash(TextFormatting.YELLOW,"⚠ Please do the Age Verif."),
        splash(TextFormatting.DARK_RED,"Built in spyware"),"nobody knows","You dont know what P.B.S.D means","dihh",
        splash(TextFormatting.DARK_GRAY,"X2091, Z13074"),"connection: suspicious","connection: normal",
        "Not only do we have your IP.. /j","boii so tuff :skull:","newgen","Agartha added to minecraft?",
        splash(TextFormatting.RED,"error: feature not found"),"Please stop using hacked clients -Mojang",
        "this feature is temporary","the bug is a feature","Added in 10/3/2026","working as intended",
        "intended by nobody","tested on production","production is the test","never tested","tested once",
        "works first try","first try btw","second try btw","third time's the charm",
        splash(TextFormatting.YELLOW,"Install the latest version!"),"version 0.0.0","version ∞",
        splash(TextFormatting.GOLD,"build 888"),splash(TextFormatting.RED,"build skid"),"Skiddiest skiddy",
        "source unavailable","trust me bro","charkhol: op bot","documentation was here","read the source",
        "what source?",splash(TextFormatting.RED,"Istg if you decompile"),"do not inspect!","inspection complete",
        "nothing suspicious","something suspicious","very suspicious","suspiciously vanilla","Client brand: Backspace",
        "Want a sneak peek?","It's just lunar client mod!","No, it won't be open sourced, stop asking","CtrlShiftN, and goon!",
        "license pending","Did you read our privacy policy?","terms may apply",splash(TextFormatting.RED,"Blame Hause"),
        splash(TextFormatting.DARK_RED,"Big Brother is watching"),"probably disabled","telemetry","we don't collect anything",
        "we collect everything","nothing leaves this computer","except the packets","You forgot to bring your lava to school!",
        "Lavacasting is awesome",splash(TextFormatting.GOLD,"Don't forget your daily prayer to 0,0!"),
        "you left something behind","you forgot the shulker","check your coords","If someone found your base, you know why!",
        "someone found it","Guess what","Chicken Butt","Damn you, Optimi!","FBNP was the best","Cactus dupe rial!",
        "Backspace pro max","simulation distance: 888",splash(TextFormatting.YELLOW,"Buy the new version or wait in a queue!"),
        "20 TPS","If you play 2b2t, you have below average IQ.","2b2t has rules","qbasty quit milking the server ban",
        "I'd like to solve the puzzle... N","Newfg!"
    };

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        ConfigManager.load();
        FriendManager.load();
        MinecraftForge.EVENT_BUS.register(MODULES);
        MinecraftForge.EVENT_BUS.register(this);
        net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(new ConfigManager());
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable(){@Override public void run(){ConfigManager.save();}},"Backspace-config-save"));
        ClientCommandHandler.instance.registerCommand(new GuiCommand());
        ClientCommandHandler.instance.registerCommand(new FriendCommand());
        ClientCommandHandler.instance.registerCommand(new PrinterCommand());
        ClientCommandHandler.instance.registerCommand(new WaypointCommand());
        ClientCommandHandler.instance.registerCommand(new WhisperSpamCommand());
    }

    @SubscribeEvent public void chooseMainMenuSplash(GuiScreenEvent.InitGuiEvent.Post event){
        if(!(event.getGui() instanceof GuiMainMenu)||SPLASH_FIELD==null)return;
        String next=SplashManager.choose(CUSTOM_SPLASHES);
        try{SPLASH_FIELD.set(event.getGui(),next);}catch(IllegalAccessException ignored){}
    }

    @SubscribeEvent public void openBackspaceMenu(net.minecraftforge.client.event.GuiOpenEvent event) {
        if (event.getGui() instanceof GuiMainMenu) event.setGui(new dev.goldforge.gui.BackspaceMainMenu());
    }

    private static String splash(TextFormatting color,String text){return color.toString()+text;}
    private static Field findSplashField(){
        try{return ReflectionHelper.findField(GuiMainMenu.class,"splashText","field_73975_c");}
        catch(Exception ignored){return null;}
    }

    @SubscribeEvent public void keyInput(InputEvent.KeyInputEvent event) {
        if(!Keyboard.getEventKeyState())return;
        Minecraft mc=Minecraft.getMinecraft();
        if(Keyboard.getEventKey()==Keyboard.KEY_RSHIFT && mc.currentScreen==null) {
            mc.displayGuiScreen(new dev.goldforge.gui.PixelClickGui());
            return;
        }
        if(mc.currentScreen==null) MODULES.onKey(Keyboard.getEventKey());
    }

    @SubscribeEvent public void drawMainMenuBrand(GuiScreenEvent.DrawScreenEvent.Post event) {
        if(!(event.getGui() instanceof GuiMainMenu))return;
        Minecraft mc=Minecraft.getMinecraft();
        dev.goldforge.gui.BrandMark.draw(4,4,20);
        int textX=28;
        mc.fontRenderer.drawStringWithShadow(NAME,textX,10,0xFFFFD700);
        int versionX=textX+mc.fontRenderer.getStringWidth(NAME)+7;
        mc.fontRenderer.drawString(VERSION,versionX,10,0xFFEEEEEE);
    }

    @SubscribeEvent public void drawHandsEditorPreview(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.getGui() instanceof dev.goldforge.gui.HandsEditor)
            ((dev.goldforge.gui.HandsEditor) event.getGui()).renderLiveHands(event.getRenderPartialTicks());
    }

    @SubscribeEvent public void drawInGameBrand(net.minecraftforge.client.event.RenderGameOverlayEvent.Post event) {
        if(event.getType()!=net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.TEXT)return;
        Minecraft mc=Minecraft.getMinecraft();
        net.minecraft.client.gui.ScaledResolution resolution=new net.minecraft.client.gui.ScaledResolution(mc);
        int groupWidth=18+6+mc.fontRenderer.getStringWidth(NAME)+6+mc.fontRenderer.getStringWidth(VERSION);
        int x=dev.goldforge.gui.BrandMark.hudRight?resolution.getScaledWidth()-dev.goldforge.gui.BrandMark.hudX-groupWidth:dev.goldforge.gui.BrandMark.hudX;
        int y=dev.goldforge.gui.BrandMark.hudBottom?resolution.getScaledHeight()-dev.goldforge.gui.BrandMark.hudY-18:dev.goldforge.gui.BrandMark.hudY;
        dev.goldforge.gui.BrandMark.draw(x,y,18);
        int textX=x+24;
        mc.fontRenderer.drawStringWithShadow(NAME,textX,y+5,0xFFFFD700);
        textX+=mc.fontRenderer.getStringWidth(NAME)+6;
        mc.fontRenderer.drawString(VERSION,textX,y+6,0xFFF0F0F0);
    }
}
