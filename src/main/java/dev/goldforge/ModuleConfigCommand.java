package dev.goldforge;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
public final class ModuleConfigCommand extends CommandBase {
    public String getName(){return "moduleconfig";}
    public String getUsage(ICommandSender sender){return "/moduleconfig <module>";}
    public int getRequiredPermissionLevel(){return 0;}
    public void execute(MinecraftServer server,ICommandSender sender,String[] args){
        dev.goldforge.module.Module module=args.length==1?GoldForge.MODULES.get(args[0]):null;
        if(module==null){sender.sendMessage(new TextComponentString(getUsage(sender)));return;}
        Minecraft.getMinecraft().addScheduledTask(()->Minecraft.getMinecraft().displayGuiScreen(
                new dev.goldforge.gui.ModuleTextEditor(module)));
    }
}
