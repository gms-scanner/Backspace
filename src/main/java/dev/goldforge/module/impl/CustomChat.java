package dev.goldforge.module.impl;

import dev.goldforge.FriendManager;
import dev.goldforge.gui.UiFont;
import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.gui.ChatLine;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional chat overlay with imported TrueType fonts, player heads, and friend tinting. */
public final class CustomChat extends Module {
    public int playerHeads=1,friendHighlight=1,useTtf=1;
    public String fontPath="";
    private UiFont font;
    private String loadedPath;
    private static final Pattern CHAT_NAME=Pattern.compile("^(?:<([A-Za-z0-9_]{1,16})>|([A-Za-z0-9_]{1,16})[ ]*:)");
    private static final Field DRAWN_LINES=field("drawnChatLines","field_146253_i");
    private static final Field SCROLL_POS=field("scrollPos","field_146250_j");
    private static final int[] COLORS={0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,
            0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF};

    public CustomChat(){
        super("CustomChat","TrueType chat font, player heads, and saved friend-name highlighting.",Category.RENDER,0);
        addIntSetting("Player heads (0/1)",0,1,1,()->playerHeads,v->playerHeads=v);
        addIntSetting("Friend highlighting (0/1)",0,1,1,()->friendHighlight,v->friendHighlight=v);
        addIntSetting("Use TTF font (0/1)",0,1,1,()->useTtf,v->useTtf=v);
    }

    @Override protected void onEnable(){loadFont();}
    @Override protected void onDisable(){if(font!=null)font.dispose();font=null;loadedPath=null;}

    /** Opens the native file picker; the selected path is stored in Backspace/config.properties. */
    public void chooseFont(){
        JFileChooser chooser=new JFileChooser();
        chooser.setDialogTitle("Choose a TrueType font for Backspace chat");
        chooser.setFileFilter(new FileNameExtensionFilter("TrueType fonts (*.ttf)","ttf"));
        if(!fontPath.isEmpty()){
            File current=new File(fontPath);
            if(current.getParentFile()!=null&&current.getParentFile().isDirectory())chooser.setCurrentDirectory(current.getParentFile());
        }
        if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION){
            File selected=chooser.getSelectedFile();
            if(selected!=null&&selected.isFile()&&selected.getName().toLowerCase().endsWith(".ttf")){
                fontPath=selected.getAbsolutePath();
                if(font!=null)font.dispose();
                font=null;loadedPath=null;loadFont();
                dev.goldforge.ConfigManager.save();
            }
        }
    }

    public String fontLabel(){return fontPath.isEmpty()?"Bundled Inter":new File(fontPath).getName();}

    @Override public void onOverlayPre(RenderGameOverlayEvent.Pre event){
        if(event.getType()!=RenderGameOverlayEvent.ElementType.CHAT||mc.player==null||mc.ingameGUI==null)return;
        ensureFont();
        if(useTtf!=0&&font==null)return;
        renderChat((RenderGameOverlayEvent.Chat)event);
        event.setCanceled(true);
    }

    private void renderChat(RenderGameOverlayEvent.Chat event){
        GuiNewChat chat=mc.ingameGUI.getChatGUI();
        List<?> lines;
        try{lines=(List<?>)DRAWN_LINES.get(chat);}catch(Exception e){return;}
        if(lines==null||lines.isEmpty())return;
        ensureFont();
        if(useTtf!=0&&font==null)return;
        boolean open=chat.getChatOpen();
        int scroll=0;
        try{scroll=SCROLL_POS.getInt(chat);}catch(Exception ignored){}
        int rowHeight=useTtf!=0&&font!=null?Math.max(9,font.lineHeight()+1):9;
        int maxLines=Math.min(chat.getLineCount(),chat.getChatHeight()/rowHeight);
        int width=chat.getChatWidth(),update=mc.ingameGUI.getUpdateCounter();
        float scale=chat.getChatScale();
        float opacity=mc.gameSettings.chatOpacity*.9F+.1F;
        GL11.glPushMatrix();
        try {
        GL11.glTranslatef(event.getPosX(),event.getPosY(),0F);
        GL11.glScalef(scale,scale,1F);
        for(int i=0;i<maxLines&&i+scroll<lines.size();i++){
            Object value=lines.get(i+scroll);
            if(!(value instanceof ChatLine))continue;
            ChatLine line=(ChatLine)value;
            int age=update-line.getUpdatedCounter();
            if(!open&&age>=200)continue;
            float fade=open?1F:Math.max(0F,1F-age/200F);
            int alpha=(int)(255F*opacity*fade);
            int y=-i*rowHeight;
            Gui.drawRect(0,y-rowHeight,width+4,y,((alpha/2)&255)<<24);
            String text=line.getChatComponent().getFormattedText();
            String name=findName(text);
            int textX=0;
            if(name!=null){
                if(friendHighlight!=0&&FriendManager.isFriend(name))text=highlightName(text,name);
                if(playerHeads!=0&&drawHead(name,1,y-rowHeight+1,alpha/255.0F))textX=11;
            }
            if(useTtf!=0)drawFormatted(text,textX,y-rowHeight+1,width-textX,alpha);
            else mc.fontRenderer.drawStringWithShadow(text,textX,y-9,alpha<<24|0xFFFFFF);
        }
        if(open){
            int total=lines.size(),shown=Math.max(1,maxLines),bar=total>shown?Math.max(1,shown*shown/total):0;
            if(bar>0){Gui.drawRect(-2,-maxLines*9-1,-1,-maxLines*9+bar,0xCCFFFFFF);if(scroll>0)Gui.drawRect(-2,-maxLines*9+bar,-1,-maxLines*9+bar+1,0xFFFFD700);}
        }
        }finally{
            GL11.glPopMatrix();
            net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770,771,1,0);
            net.minecraft.client.renderer.GlStateManager.enableTexture2D();
            net.minecraft.client.renderer.GlStateManager.disableBlend();
            net.minecraft.client.renderer.GlStateManager.color(1F,1F,1F,1F);
        }
    }

    private boolean drawHead(String name,int x,int y,float alpha){
        if(mc.getConnection()==null)return false;
        net.minecraft.entity.player.EntityPlayer player=mc.world==null?null:mc.world.getPlayerEntityByName(name);
        NetworkPlayerInfo info=mc.getConnection().getPlayerInfo(name);
        if(info==null&&player!=null)info=mc.getConnection().getPlayerInfo(player.getUniqueID());
        if(info==null&&player==null){for(NetworkPlayerInfo candidate:mc.getConnection().getPlayerInfoMap())if(candidate.getGameProfile()!=null&&candidate.getGameProfile().getName().equalsIgnoreCase(name)){info=candidate;break;}}
        if(info==null&&player==null)return false;
        java.util.UUID uuid=info!=null&&info.getGameProfile()!=null?info.getGameProfile().getId():player.getUniqueID();
        ResourceLocation skin=info==null?net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(uuid):info.getLocationSkin();
        if(skin==null||mc.getTextureManager().getTexture(skin)==null)skin=net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(uuid);
        mc.getTextureManager().bindTexture(skin);
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        net.minecraft.client.renderer.GlStateManager.color(1F,1F,1F,alpha);
        Gui.drawScaledCustomSizeModalRect(x,y,8,8,8,8,8,8,64,64);
        Gui.drawScaledCustomSizeModalRect(x,y,40,8,8,8,8,8,64,64);
        net.minecraft.client.renderer.GlStateManager.color(1F,1F,1F,1F);
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        return true;
    }

    private String findName(String text){
        Matcher matcher=CHAT_NAME.matcher(stripFormatting(text));
        if(!matcher.find())return null;
        return matcher.group(1)!=null?matcher.group(1):matcher.group(2);
    }

    private String highlightName(String formatted,String name){
        int index=formatted.indexOf(name);
        String section=String.valueOf((char)167);
        return index<0?formatted:formatted.substring(0,index)+section+"a"+name+section+"r"+formatted.substring(index+name.length());
    }

    private String stripFormatting(String value){return value.replaceAll("(?i)"+Pattern.quote(String.valueOf((char)167))+"[0-9A-FK-OR]","");}

    private void drawFormatted(String value,int x,int y,int maxWidth,int alpha){
        float current=0F,at=x;int color=0xFFFFFF;
        StringBuilder run=new StringBuilder();
        for(int i=0;i<value.length();){
            char ch=value.charAt(i);
            if(ch==(char)167&&i+1<value.length()){
                at=flush(run,at,y,color,alpha);char code=Character.toLowerCase(value.charAt(i+1));int index="0123456789abcdef".indexOf(code);
                if(index>=0)color=COLORS[index];else if(code=='r')color=0xFFFFFF;
                i+=2;continue;
            }
            int cp=value.codePointAt(i);String glyph=new String(Character.toChars(cp));float glyphWidth=font.widthPrecise(glyph);
            if(current+glyphWidth>maxWidth)break;
            current+=glyphWidth;run.append(glyph);i+=Character.charCount(cp);
        }
        flush(run,at,y,color,alpha);
    }

    private float flush(StringBuilder run,float x,int y,int color,int alpha){
        if(run.length()>0){font.draw(run.toString(),x,y,(alpha<<24)|(color&0xFFFFFF));x+=font.widthPrecise(run.toString());run.setLength(0);}
        return x;
    }

    private void ensureFont(){if(font==null||!java.util.Objects.equals(loadedPath,fontPath))loadFont();}
    private void loadFont(){
        try{font=new UiFont(fontPath.isEmpty()?null:new File(fontPath),8.0F);loadedPath=fontPath;}
        catch(Exception ignored){font=null;loadedPath=fontPath;}
    }

    private static Field field(String name,String srgName){
        try{return ReflectionHelper.findField(GuiNewChat.class,name,srgName);}
        catch(Exception ignored){return null;}
    }
}
