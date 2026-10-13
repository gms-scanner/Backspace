package dev.goldforge.gui;

import dev.goldforge.module.impl.ShaderFX;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** In-game shader-overlay path editor; avoids opening Swing over Minecraft's GL window. */
public final class ShaderOverlayGui extends GuiScreen {
    private final GuiScreen parent;
    private final ShaderFX shader;
    private GuiTextField path;
    private String error="";

    public ShaderOverlayGui(GuiScreen parent,ShaderFX shader){this.parent=parent;this.shader=shader;}

    @Override public void initGui(){
        Keyboard.enableRepeatEvents(true);
        int x=width/2-180,y=height/2-72;
        path=new GuiTextField(0,fontRenderer,x+16,y+49,328,20);
        path.setMaxStringLength(1024);
        path.setText(shader.texturePath==null?"":shader.texturePath);
        buttonList.clear();
        buttonList.add(new GuiButton(0,x+16,y+82,156,20,"IMPORT PNG / JPG"));
        buttonList.add(new GuiButton(1,x+188,y+82,156,20,"CLEAR OVERLAY"));
        buttonList.add(new GuiButton(2,x+16,y+108,328,20,"BACK"));
    }

    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);}

    @Override protected void actionPerformed(GuiButton button)throws IOException{
        if(button.id==2){mc.displayGuiScreen(parent);return;}
        if(button.id==1){shader.importTexturePath("");mc.displayGuiScreen(parent);return;}
        if(button.id==0){
            if(shader.importTexturePath(path.getText()))mc.displayGuiScreen(parent);
            else error=shader.textureStatus();
        }
    }

    @Override protected void keyTyped(char typedChar,int keyCode)throws IOException{
        if(keyCode==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}
        path.textboxKeyTyped(typedChar,keyCode);
        super.keyTyped(typedChar,keyCode);
    }

    @Override protected void mouseClicked(int mouseX,int mouseY,int button)throws IOException{
        super.mouseClicked(mouseX,mouseY,button);path.mouseClicked(mouseX,mouseY,button);
    }

    @Override public void updateScreen(){path.updateCursorCounter();}

    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){
        drawDefaultBackground();
        int x=width/2-180,y=height/2-72;
        drawRect(x,y,x+360,y+138,0xF20B1117);
        drawRect(x,y,x+360,y+2,0xFFFFC928);
        drawString(fontRenderer,"SHADER OVERLAY",x+16,y+14,0xFFFFD34D);
        drawString(fontRenderer,"Paste an image path. It is validated and copied into Backspace.",x+16,y+30,0xFF9AA3AF);
        drawString(fontRenderer,"IMAGE PATH",x+16,y+40,0xFF9AA3AF);
        path.drawTextBox();
        if(!error.isEmpty())drawString(fontRenderer,error,x+16,y+133,0xFFFF7272);
        super.drawScreen(mouseX,mouseY,partialTicks);
    }

    @Override public boolean doesGuiPauseGame(){return false;}
}
