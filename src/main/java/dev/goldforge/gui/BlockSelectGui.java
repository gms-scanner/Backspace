package dev.goldforge.gui;

import dev.goldforge.module.impl.BlockESP;
import net.minecraft.block.Block;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Searchable catalog of registered blocks, each shown with its inventory icon. */
public final class BlockSelectGui extends GuiScreen {
    private static final int GOLD=0xFFFFD700;
    private final GuiScreen parent;
    private final BlockESP module;
    private final List<Block> blocks=new ArrayList<Block>();
    private String query="";
    private boolean searchFocused;
    private int scroll;
    private String cachedQuery;
    private List<Block> filteredBlocks;

    public BlockSelectGui(GuiScreen parent,BlockESP module){this.parent=parent;this.module=module;}

    @Override public void initGui(){
        if(blocks.isEmpty()){
            for(Block block:Block.REGISTRY){if(block!=null&&block!=net.minecraft.init.Blocks.AIR&&Block.REGISTRY.getNameForObject(block)!=null)blocks.add(block);}
            Collections.sort(blocks,new Comparator<Block>(){@Override public int compare(Block a,Block b){return id(a).compareToIgnoreCase(id(b));}});
        }
    }

    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){
        drawRect(0,0,width,height,0xAA050505);
        int x=width/2-270,y=height/2-190;
        Gui.drawRect(x,y,x+540,y+380,0xF20A0A0A);
        drawString(fontRenderer,"SELECT BLOCKS",x+18,y+14,GOLD);
        drawString(fontRenderer,module.selectedBlocks().size()+" selected  |  click a block to add or remove it",x+18,y+32,0xFFAAAAAA);
        Gui.drawRect(x+18,y+53,x+522,y+76,searchFocused?0xFF272727:0xFF1B1B1B);
        drawString(fontRenderer,"SEARCH",x+27,y+61,GOLD);
        drawString(fontRenderer,query.isEmpty()?"Type a block name or id":fontRenderer.trimStringToWidth(query,410),x+86,y+61,query.isEmpty()?0xFF777777:0xFFF4F4F4);

        List<Block> visible=visibleBlocks();
        int listTop=y+84,listBottom=y+354,rowHeight=27,columnWidth=250,columns=2;
        int rows=(visible.size()+columns-1)/columns,maxScroll=Math.max(0,rows*rowHeight-(listBottom-listTop));scroll=Math.min(scroll,maxScroll);
        ScaledResolution resolution=new ScaledResolution(mc);int factor=resolution.getScaleFactor();
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((x+12)*factor,mc.displayHeight-listBottom*factor,516*factor,(listBottom-listTop)*factor);
        for(int i=0;i<visible.size();i++){
            int column=i%columns,row=i/columns;
            int left=x+16+column*columnWidth,top=listTop+row*rowHeight-scroll;
            if(top+rowHeight<listTop||top>listBottom)continue;
            Block block=visible.get(i);boolean selected=module.isSelected(block),hover=mouseX>=left&&mouseX<left+columnWidth-4&&mouseY>=top&&mouseY<top+rowHeight-2;
            Gui.drawRect(left,top,left+columnWidth-5,top+rowHeight-2,selected?0xCC3A3100:(hover?0xEE282828:0xCC171717));
            if(selected)Gui.drawRect(left,top,left+2,top+rowHeight-2,GOLD);
            // Keep the readable name first; render the actual inventory item in a
            // dedicated slot at the far end so unusual item models cannot overlap it.
            drawString(fontRenderer,fontRenderer.trimStringToWidth(displayName(block),columnWidth-72),left+10,top+9,selected?GOLD:0xFFEAEAEA);
            int slotX=left+columnWidth-49,slotY=top+3;
            Gui.drawRect(slotX,slotY,slotX+20,slotY+20,0xFF101010);
            drawBlockIcon(block,slotX+2,slotY+2);
            drawString(fontRenderer,selected?"✓":"+",left+columnWidth-24,top+8,selected?GOLD:0xFFB8B8B8);
        }
        GL11.glPopAttrib();
        Gui.drawRect(x+520,y+84,x+524,y+354,0xFF292929);
        int thumbHeight=rows==0?270:Math.max(22,270*270/Math.max(270,rows*rowHeight));
        int thumbY=y+84+(maxScroll==0?0:(int)((scroll/(float)maxScroll)*(270-thumbHeight)));
        Gui.drawRect(x+519,thumbY,x+525,thumbY+thumbHeight,maxScroll==0?0xFF777777:GOLD);
        drawString(fontRenderer,visible.size()+" blocks",x+18,y+363,0xFF888888);
        drawString(fontRenderer,"ESC  BACK",x+445,y+363,0xFFAAAAAA);
        super.drawScreen(mouseX,mouseY,partialTicks);
    }

    private void drawBlockIcon(Block block,int x,int y){
        Item item=Item.getItemFromBlock(block);
        if(item==null||item==net.minecraft.init.Items.AIR){Gui.drawRect(x,y,x+16,y+16,0xFF444444);return;}
        ItemStack stack=new ItemStack(item);
        GlStateManager.enableTexture2D();GlStateManager.enableLighting();GlStateManager.color(1F,1F,1F,1F);
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack,x,y);
        GlStateManager.disableLighting();GlStateManager.color(1F,1F,1F,1F);
    }

    private String displayName(Block block){try{return block.getLocalizedName();}catch(RuntimeException ignored){return id(block);}}
    private String id(Block block){ResourceLocation location=Block.REGISTRY.getNameForObject(block);return location==null?"unknown:block":location.toString();}
    private List<Block> visibleBlocks(){
        String filter=query.trim().toLowerCase(Locale.ROOT);
        if(filter.isEmpty())return blocks;
        if(filter.equals(cachedQuery)&&filteredBlocks!=null)return filteredBlocks;
        cachedQuery=filter;
        List<Block> result=new ArrayList<Block>();
        for(Block block:blocks)if(id(block).toLowerCase(Locale.ROOT).contains(filter)||displayName(block).toLowerCase(Locale.ROOT).contains(filter))result.add(block);
        filteredBlocks=result;return filteredBlocks;
    }

    @Override protected void mouseClicked(int mouseX,int mouseY,int mouseButton)throws IOException{
        int x=width/2-270,y=height/2-190;
        if(mouseY>=y+53&&mouseY<=y+76&&mouseX>=x+18&&mouseX<=x+522){searchFocused=true;return;}
        searchFocused=false;
        if(mouseY>=y+84&&mouseY<=y+354&&mouseX>=x+16&&mouseX<x+16+2*250){int column=(mouseX-(x+16))/250;if(column>=0&&column<2){int row=(mouseY-(y+84)+scroll)/27;int index=row*2+column;List<Block> visible=visibleBlocks();if(index>=0&&index<visible.size())module.toggleTarget(visible.get(index));}}
        super.mouseClicked(mouseX,mouseY,mouseButton);
    }
    @Override protected void keyTyped(char typedChar,int keyCode)throws IOException{
        if(keyCode==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}
        if(searchFocused){if(keyCode==Keyboard.KEY_BACK){if(!query.isEmpty())query=query.substring(0,query.length()-1);scroll=0;return;}if(Character.isLetterOrDigit(typedChar)||Character.isWhitespace(typedChar)||"_:.-".indexOf(typedChar)>=0){query+=typedChar;scroll=0;return;}}
        super.keyTyped(typedChar,keyCode);
    }
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0){int count=(visibleBlocks().size()+1)/2;scroll=Math.max(0,Math.min(Math.max(0,count*27-270),scroll+(wheel>0?-54:54)));}}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void drawDefaultBackground(){}
}
