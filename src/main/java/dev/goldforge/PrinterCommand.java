package dev.goldforge;

import dev.goldforge.module.Module;
import dev.goldforge.module.impl.Printer;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.nio.file.Files;

public final class PrinterCommand extends CommandBase {
    @Override public String getName(){return "printer";}
    @Override public String getUsage(ICommandSender sender){return "/printer load <name.litematic|name.schematic> | /printer unload";}
    @Override public int getRequiredPermissionLevel(){return 0;}

    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args){
        Module module=GoldForge.MODULES.get("Printer");
        if(!(module instanceof Printer)){tell("Printer module is unavailable.");return;}
        Printer printer=(Printer)module;
        if(args.length==1&&args[0].equalsIgnoreCase("unload")){printer.unload();tell("Schematic unloaded.");return;}
        if(args.length<2||!args[0].equalsIgnoreCase("load")){tell(getUsage(sender));tell("Place schematic files in: "+printer.schematicDirectory());return;}
        StringBuilder name=new StringBuilder();for(int i=1;i<args.length;i++){if(i>1)name.append(' ');name.append(args[i]);}
        try{Files.createDirectories(printer.schematicDirectory());}catch(Exception ignored){}
        if(!printer.load(name.toString())){tell("Could not load that schematic. Check its format, name, and file in the schematics folder.");return;}
        printer.setEnabled(true);
        tell("Loaded schematic at your current position. " + (printer.autoWalk==0?"Walk mode is off.":"Auto walk is on."));
    }

    private void tell(String text){if(Minecraft.getMinecraft().player!=null)Minecraft.getMinecraft().player.sendMessage(new TextComponentString("§e[Backspace] §f"+text));}
}
