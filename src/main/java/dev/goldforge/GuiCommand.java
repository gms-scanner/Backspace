package dev.goldforge;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import dev.goldforge.module.Module;
import dev.goldforge.module.MovementSnapshot;
import java.util.Map;

public final class GuiCommand extends CommandBase {
    @Override public String getName() { return "backspace"; }
    @Override public String getUsage(ICommandSender sender) { return "/backspace [modules|debug|renderdebug|movementdebug|packetlog|phase|packetfly|replay|reloadconfig|profile]"; }
    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        Minecraft.getMinecraft().addScheduledTask(() -> run(sender,args));
    }
    private void run(ICommandSender sender,String[] args){
        if(args.length==0){Minecraft.getMinecraft().displayGuiScreen(new dev.goldforge.gui.PixelClickGui());return;}
        String action=args[0].toLowerCase(java.util.Locale.ROOT);
        if("modules".equals(action)){for(Module module:GoldForge.MODULES.all())tell(sender,(module.enabled?"§aON §f":"§7OFF ")+module.name+" §8["+module.category.name()+"]");return;}
        if("movementdebug".equals(action)){movementdebug(sender);return;}
        if("replay".equals(action)){replay(sender,args);return;}
        if("packetlog".equals(action)){packetlog(sender,args);return;}
        if("packetfly".equals(action)){packetfly(sender,args);return;}
        if("phase".equals(action)){phase(sender,args);return;}
        if("debug".equals(action)){debug(sender,false);return;}
        if("renderdebug".equals(action)){debug(sender,true);return;}
        if("reloadconfig".equals(action)){ConfigManager.reload();tell(sender,"Configuration reloaded.");return;}
        if("profile".equals(action)){profile(sender,args);return;}
        tell(sender,getUsage(sender));
    }
    private void profile(ICommandSender sender,String[] args){
        if(args.length==2&&"list".equalsIgnoreCase(args[1])){tell(sender,"Profiles: "+String.join(", ",ConfigManager.profiles()));return;}
        if(args.length==3&&"server".equalsIgnoreCase(args[1])){String name=ConfigManager.currentServerProfileName();if(name==null){tell(sender,"Join a multiplayer server first.");return;}if("save".equalsIgnoreCase(args[2]))tell(sender,ConfigManager.saveProfile(name)?"Saved this server's profile as "+name+".":"Could not save the server profile.");else if("load".equalsIgnoreCase(args[2]))tell(sender,ConfigManager.loadProfile(name)?"Loaded this server's profile.":"No saved profile exists for this server yet.");else tell(sender,"Usage: /backspace profile server save|load");return;}
        if(args.length==3&&"save".equalsIgnoreCase(args[1])){tell(sender,ConfigManager.saveProfile(args[2])?"Saved profile "+args[2]+".":"Could not save profile. Use letters, numbers, _ or - (max 32).");return;}
        if(args.length==3&&"load".equalsIgnoreCase(args[1])){tell(sender,ConfigManager.loadProfile(args[2])?"Loaded profile "+args[2]+".":"Profile not found or invalid.");return;}
        tell(sender,"Usage: /backspace profile list | save <name> | load <name>");
    }
    private void replay(ICommandSender sender,String[] args){
        if(args.length==2&&"start".equalsIgnoreCase(args[1])){tell(sender,dev.goldforge.module.MovementReplay.startRecording()?"Movement recording started; previous samples cleared.":"Join a world before recording.");return;}
        if(args.length==2&&"stop".equalsIgnoreCase(args[1])){dev.goldforge.module.MovementReplay.stopRecording();tell(sender,dev.goldforge.module.MovementReplay.status());return;}
        if(args.length==2&&"play".equalsIgnoreCase(args[1])){tell(sender,dev.goldforge.module.MovementReplay.play()?"Local ghost-path playback started.":"Record at least two samples first.");return;}
        if(args.length==2&&"cancel".equalsIgnoreCase(args[1])){dev.goldforge.module.MovementReplay.stopPlayback();tell(sender,"Replay playback stopped.");return;}
        if(args.length==2&&"clear".equalsIgnoreCase(args[1])){dev.goldforge.module.MovementReplay.clear();tell(sender,"Replay samples cleared.");return;}
        if(args.length==2&&"status".equalsIgnoreCase(args[1])){tell(sender,dev.goldforge.module.MovementReplay.status());return;}
        if(args.length==3&&"export".equalsIgnoreCase(args[1])){tell(sender,dev.goldforge.module.MovementReplay.export(args[2])?"Replay exported to Backspace/replays/"+args[2]+".csv.":"Export failed; record samples first and use a 1-32 character filename.");return;}
        tell(sender,"Usage: /backspace replay start|stop|play|cancel|clear|status|export <name>");
    }
    private void packetlog(ICommandSender sender,String[] args){
        if(args.length==2&&("on".equalsIgnoreCase(args[1])||"off".equalsIgnoreCase(args[1]))){dev.goldforge.network.PacketManager.setLogging("on".equalsIgnoreCase(args[1]));ConfigManager.save();tell(sender,"Outgoing module packet log "+(dev.goldforge.network.PacketManager.isLogging()?"enabled":"disabled")+".");return;}
        if(args.length==2&&"recent".equalsIgnoreCase(args[1])){java.util.List<String> recent=dev.goldforge.network.PacketManager.recent();tell(sender,recent.isEmpty()?"No logged module sends. Enable logging with /backspace packetlog on.":"Recent logged module sends: "+recent.size());for(String line:recent)tell(sender,line);return;}
        if(args.length==2&&"status".equalsIgnoreCase(args[1])){tell(sender,"Outgoing module packet log is "+(dev.goldforge.network.PacketManager.isLogging()?"enabled":"disabled")+".");return;}
        tell(sender,"Usage: /backspace packetlog on|off|status|recent");
    }
    private void phase(ICommandSender sender,String[] args){
        if(args.length==2&&"model".equalsIgnoreCase(args[1])){for(String line:dev.goldforge.diagnostics.NcpPassableHarness.runFixtures())tell(sender,line);return;}
        if(args.length==2&&"clear".equalsIgnoreCase(args[1])){dev.goldforge.module.impl.Phase.clearDiagnostics();tell(sender,"Phase diagnostics cleared.");return;}
        if(args.length==2&&"status".equalsIgnoreCase(args[1])){tell(sender,dev.goldforge.module.impl.Phase.diagnosticStatus((dev.goldforge.module.impl.Phase)GoldForge.MODULES.get("Phase")));return;}
        if(args.length==2&&"recent".equalsIgnoreCase(args[1])){java.util.List<String> lines=dev.goldforge.module.impl.Phase.recentDiagnostics();if(lines.isEmpty()){tell(sender,"No Phase diagnostics yet.");return;}tell(sender,"Recent Phase diagnostics: "+lines.size());for(int i=Math.max(0,lines.size()-12);i<lines.size();i++)tell(sender,lines.get(i));return;}
        if(args.length==3&&"export".equalsIgnoreCase(args[1])){try{java.io.File file=dev.goldforge.module.impl.Phase.exportDiagnostics(args[2]);tell(sender,file==null?"Export failed; use a 1-32 character filename with letters, numbers, _ or -.":"Movement trace exported to "+file.getAbsolutePath());}catch(java.io.IOException failure){tell(sender,"Movement trace export failed: "+failure.getMessage());}return;}
        tell(sender,"Usage: /backspace phase status|recent|clear|export <name>|model");
    }
    private void packetfly(ICommandSender sender,String[] args){
        Module module=GoldForge.MODULES.get("PacketFlyLab");
        if(!(module instanceof dev.goldforge.module.impl.PacketFlyLab)){tell(sender,"PacketFlyLab module unavailable.");return;}
        dev.goldforge.module.impl.PacketFlyLab lab=(dev.goldforge.module.impl.PacketFlyLab)module;
        if(args.length==2&&"start".equalsIgnoreCase(args[1])){lab.setEnabled(true);tell(sender,"PacketFlyLab capture started; no movement packets are generated or changed.");return;}
        if(args.length==2&&"stop".equalsIgnoreCase(args[1])){lab.setEnabled(false);tell(sender,"PacketFlyLab capture stopped; trace retained for export.");return;}
        if(args.length==2&&"status".equalsIgnoreCase(args[1])){tell(sender,dev.goldforge.module.impl.PacketFlyLab.status());return;}
        if(args.length==2&&"clear".equalsIgnoreCase(args[1])){dev.goldforge.module.impl.PacketFlyLab.clearTrace();tell(sender,"PacketFlyLab trace cleared.");return;}
        if(args.length==2&&"recent".equalsIgnoreCase(args[1])){java.util.List<String> lines=dev.goldforge.module.impl.PacketFlyLab.recent(12);if(lines.isEmpty()){tell(sender,"No PacketFlyLab events recorded.");return;}for(String line:lines)tell(sender,line);return;}
        if(args.length==3&&"export".equalsIgnoreCase(args[1])){try{java.nio.file.Path path=dev.goldforge.module.impl.PacketFlyLab.export(args[2]);tell(sender,path==null?"Export failed; use a 1-32 character filename with letters, numbers, _ or -.":"PacketFlyLab trace exported to "+path.toAbsolutePath());}catch(java.io.IOException failure){tell(sender,"PacketFlyLab export failed: "+failure.getMessage());}return;}
        tell(sender,"Usage: /backspace packetfly start|stop|status|recent|clear|export <name>");
    }
    private void movementdebug(ICommandSender sender){
        MovementSnapshot snapshot=MovementSnapshot.capture();if(snapshot==null){tell(sender,"No local player is available.");return;}
        tell(sender,snapshot.describe());
        tell(sender,String.format(java.util.Locale.ROOT,"accel %.4f | yaw delta %.2f | desired %.3f / %.3f | client tick rate %.1f/s | suspected position jumps %d",dev.goldforge.module.MovementManager.getAcceleration(),dev.goldforge.module.MovementManager.getYawDelta(),dev.goldforge.module.MovementManager.getDesiredX(),dev.goldforge.module.MovementManager.getDesiredZ(),dev.goldforge.module.MovementManager.getTicksPerSecond(),dev.goldforge.module.MovementManager.getSuspectedPositionJumps()));
        tell(sender,"Backspace module sends: "+dev.goldforge.network.PacketManager.getModuleSendCount()+" (not a count of all game packets)");
    }
    private void debug(ICommandSender sender,boolean renderOnly){
        java.util.List<Map.Entry<String,Long>> values=new java.util.ArrayList<Map.Entry<String,Long>>(GoldForge.MODULES.averageTimingsNanos().entrySet());
        if(renderOnly)values.removeIf(entry->!entry.getKey().endsWith(".render"));
        java.util.Collections.sort(values,(a,b)->Long.compare(b.getValue(),a.getValue()));
        tell(sender,renderOnly?"Render hook averages (last 32 samples):":"Module hook averages (last 32 samples); timings cover tick/render/input callbacks.");
        for(int i=0;i<Math.min(8,values.size());i++){Map.Entry<String,Long> entry=values.get(i);tell(sender,entry.getKey()+": "+(entry.getValue()/1000L)+" µs avg");}
        Runtime runtime=Runtime.getRuntime();tell(sender,"Memory "+((runtime.totalMemory()-runtime.freeMemory())/1048576L)+" / "+(runtime.maxMemory()/1048576L)+" MB");
    }
    private void tell(ICommandSender sender,String message){sender.sendMessage(new TextComponentString("§e[Backspace] §f"+message));}
    @Override public int getRequiredPermissionLevel() { return 0; }
}
