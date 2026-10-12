package dev.goldforge.gui;

import dev.goldforge.ConfigManager;
import dev.goldforge.module.impl.AutoSign;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/** Four-line editor for the native 1.12.2 sign text components. */
public final class SignTextGui extends GuiScreen {
    private final GuiScreen parent;
    private final AutoSign module;
    private final GuiTextField[] fields=new GuiTextField[4];
    public SignTextGui(GuiScreen parent,AutoSign module){this.parent=parent;this.module=module;}
    @Override public void initGui(){
        Keyboard.enableRepeatEvents(true);int x=width/2-130,y=height/2-86;
        String[] lines={module.line1,module.line2,module.line3,module.line4};
        for(int i=0;i<4;i++){fields[i]=new GuiTextField(i,fontRenderer,x,y+i*28,260,20);fields[i].setMaxStringLength(15);fields[i].setText(lines[i]==null?"":lines[i]);}
        buttonList.clear();buttonList.add(new GuiButton(0,x,y+124,126,20,"SAVE TEXT"));
        buttonList.add(new GuiButton(1,x+134,y+124,126,20,"AUTO SUBMIT: "+(module.autoSubmit==0?"OFF":"ON")));
        buttonList.add(new GuiButton(2,x,y+151,260,20,"BACK"));
    }
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);}
    @Override protected void actionPerformed(GuiButton button)throws IOException{
        if(button.id==1){saveFields();module.autoSubmit=module.autoSubmit==0?1:0;button.displayString="AUTO SUBMIT: "+(module.autoSubmit==0?"OFF":"ON");ConfigManager.save();return;}
        if(button.id==0){saveFields();mc.displayGuiScreen(parent);return;}
        if(button.id==2){mc.displayGuiScreen(parent);}
    }
    private void saveFields(){module.line1=fields[0].getText();module.line2=fields[1].getText();module.line3=fields[2].getText();module.line4=fields[3].getText();ConfigManager.save();}
    @Override protected void keyTyped(char typedChar,int keyCode)throws IOException{
        if(keyCode==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}
        for(GuiTextField field:fields)field.textboxKeyTyped(typedChar,keyCode);
        super.keyTyped(typedChar,keyCode);
    }
    @Override protected void mouseClicked(int mouseX,int mouseY,int button)throws IOException{super.mouseClicked(mouseX,mouseY,button);for(GuiTextField field:fields)field.mouseClicked(mouseX,mouseY,button);}
    @Override public void updateScreen(){for(GuiTextField field:fields)field.updateCursorCounter();}
    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){
        drawDefaultBackground();int x=width/2-150,y=height/2-116;drawRect(x,y,x+300,y+217,0xF20B1117);drawRect(x,y,x+300,y+2,0xFFFFC928);
        drawString(fontRenderer,"AUTOSIGN TEXT",x+18,y+12,0xFFFFD34D);drawString(fontRenderer,"Four lines · formatting codes supported · 15 chars each",x+18,y+29,0xFF85959C);
        for(int i=0;i<4;i++){drawString(fontRenderer,"LINE "+(i+1),x+18,y+49+i*28,0xFF85959C);fields[i].drawTextBox();}
        super.drawScreen(mouseX,mouseY,partialTicks);
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
