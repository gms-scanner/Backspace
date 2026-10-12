package dev.goldforge.module.impl;

import dev.goldforge.module.*;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

public final class WitherPreview extends Module {
    public WitherPreview() { super("Wither Preview", "Shows the soul sand and skull positions at the aimed block.", Category.RENDER, 0); }
    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        if(!enabled||mc.player==null||mc.objectMouseOver==null||mc.objectMouseOver.getBlockPos()==null)return;
        BlockPos b=mc.objectMouseOver.getBlockPos().up();
        BlockPos[] positions={b,b.up(),b.up().north(),b.up().south(),b.up(2),b.up(2).north(),b.up(2).south()};
        double px=mc.getRenderManager().viewerPosX, py=mc.getRenderManager().viewerPosY, pz=mc.getRenderManager().viewerPosZ;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT); GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA); GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glLineWidth(2.0F);
        for(int i=0;i<positions.length;i++){BlockPos p=positions[i];AxisAlignedBB box=new AxisAlignedBB(p.getX()-px,p.getY()-py,p.getZ()-pz,p.getX()+1-px,p.getY()+1-py,p.getZ()+1-pz);RenderGlobal.drawSelectionBoundingBox(box,1.0F,.84F,0.0F,.95F);}
        GL11.glPopAttrib();
    }
}
