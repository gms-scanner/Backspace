package dev.goldforge.module.impl;

import dev.goldforge.module.Category;
import dev.goldforge.module.Module;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.play.client.CPacketPlayer;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Local-file schematic printer with placement confirmation waits and optional direct walking. */
public final class Printer extends Module {
    public int placeDelay=2,reach=4,autoWalk=0,silentAim=1;
    private final List<Placement> placements=new ArrayList<Placement>();
    private BlockPos origin,pendingPos;
    private IBlockState pendingState;
    private int pendingTicks,cooldown,cursor;
    private boolean walking;

    public Printer(){
        super("Printer","Prints a loaded Litematica or legacy schematic. Loads via /printer load <file>.",Category.MISC,0);
        addIntSetting("Place delay (ticks)",0,10,1,()->placeDelay,v->placeDelay=v);
        addIntSetting("Reach",2,5,1,()->reach,v->reach=v);
        addIntSetting("Auto walk (0/1)",0,1,1,()->autoWalk,v->autoWalk=v);
        addIntSetting("Silent aim (0/1)",0,1,1,()->silentAim,v->silentAim=v);
    }

    public Path schematicDirectory(){return Paths.get(mc.mcDataDir.getAbsolutePath(),"schematics");}

    public void unload(){setEnabled(false);placements.clear();origin=null;pendingPos=null;pendingState=null;cursor=0;}

    public boolean load(String fileName){
        if(mc.player==null||mc.world==null)return false;
        if(fileName==null||!fileName.matches("[A-Za-z0-9_ .()\\-]+\\.(litematic|schematic)"))return false;
        Path directory=schematicDirectory().normalize();
        Path file=directory.resolve(fileName).normalize();
        if(!file.startsWith(directory)||!Files.isRegularFile(file))return false;
        try(InputStream input=Files.newInputStream(file)){
            NBTTagCompound root=CompressedStreamTools.readCompressed(input);
            List<Placement> loaded=fileName.toLowerCase().endsWith(".litematic")?readLitematic(root):readLegacySchematic(root);
            if(loaded.isEmpty())return false;
            Collections.sort(loaded,new Comparator<Placement>(){@Override public int compare(Placement a,Placement b){int y=Integer.compare(a.relative.getY(),b.relative.getY());if(y!=0)return y;int x=Integer.compare(a.relative.getX(),b.relative.getX());return x!=0?x:Integer.compare(a.relative.getZ(),b.relative.getZ());}});
            placements.clear();placements.addAll(loaded);
            origin=new BlockPos(mc.player.posX,mc.player.getEntityBoundingBox().minY,mc.player.posZ);
            cursor=0;pendingPos=null;pendingState=null;pendingTicks=0;cooldown=0;
            return true;
        }catch(Exception ignored){return false;}
    }

    private List<Placement> readLegacySchematic(NBTTagCompound root){
        NBTTagCompound data=root.hasKey("Schematic",10)?root.getCompoundTag("Schematic"):root;
        int width=data.getShort("Width")&0xFFFF,height=data.getShort("Height")&0xFFFF,length=data.getShort("Length")&0xFFFF;
        byte[] blocks=data.getByteArray("Blocks"),metadata=data.getByteArray("Data"),add=data.getByteArray("AddBlocks");
        long count=(long)width*height*length;
        if(width<1||height<1||length<1||count>250000L||blocks.length<count||metadata.length<count)return Collections.emptyList();
        List<Placement> result=new ArrayList<Placement>();
        for(int i=0;i<(int)count;i++){
            int id=blocks[i]&255;
            if(add.length>(i>>1)){int nibble=add[i>>1]&255;id|=((i&1)==0?nibble&15:nibble>>4)<<8;}
            Block block=Block.getBlockById(id);if(block==null||block==Blocks.AIR)continue;
            IBlockState state;
            try{state=block.getStateFromMeta(metadata[i]&15);}catch(Exception ignored){continue;}
            int x=i%width,z=(i/width)%length,y=i/(width*length);
            if(state.getBlock()!=Blocks.AIR)result.add(new Placement(new BlockPos(x,y,z),state));
        }
        return result;
    }

    private List<Placement> readLitematic(NBTTagCompound root){
        if(!root.hasKey("Regions",10))return Collections.emptyList();
        NBTTagCompound regions=root.getCompoundTag("Regions");
        List<Placement> result=new ArrayList<Placement>();
        for(String regionName:regions.getKeySet()){
            NBTTagCompound region=regions.getCompoundTag(regionName);
            NBTTagCompound sizeTag=region.getCompoundTag("Size"),position=region.getCompoundTag("Position");
            int sx=sizeTag.getInteger("x"),sy=sizeTag.getInteger("y"),sz=sizeTag.getInteger("z");
            int width=Math.abs(sx),height=Math.abs(sy),length=Math.abs(sz);
            long count=(long)width*height*length;
            if(width<1||height<1||length<1||count>250000L-result.size())continue;
            NBTTagList palette=region.getTagList("BlockStatePalette",10);
            if(!(region.getTag("BlockStates") instanceof NBTTagLongArray))continue;
            long[] packed=readLongArray((NBTTagLongArray)region.getTag("BlockStates"));
            if(palette.tagCount()<1||packed.length==0)continue;
            int bits=Math.max(2,32-Integer.numberOfLeadingZeros(palette.tagCount()-1));
            int px=position.getInteger("x")+Math.min(0,sx),py=position.getInteger("y")+Math.min(0,sy),pz=position.getInteger("z")+Math.min(0,sz);
            for(int i=0;i<(int)count;i++){
                int paletteIndex=packedValue(packed,i,bits);
                if(paletteIndex<0||paletteIndex>=palette.tagCount())continue;
                IBlockState state=readPaletteState(palette.getCompoundTagAt(paletteIndex));
                if(state==null||state.getBlock()==Blocks.AIR)continue;
                int x=i%width,z=(i/width)%length,y=i/(width*length);
                if(sx<0)x=width-1-x;if(sy<0)y=height-1-y;if(sz<0)z=length-1-z;
                result.add(new Placement(new BlockPos(px+x,py+y,pz+z),state));
            }
        }
        return result;
    }

    private long[] readLongArray(NBTTagLongArray tag){
        // 1.12.2 has no public array accessor; avoid depending on a mapping-specific field name.
        try{
            for(Field field:NBTTagLongArray.class.getDeclaredFields())if(field.getType()==long[].class){
                field.setAccessible(true);
                return ((long[])field.get(tag)).clone();
            }
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Could not read schematic block states",e);}
        throw new IllegalStateException("Unsupported NBTTagLongArray layout");
    }

    private int packedValue(long[] data,int index,int bits){
        long bit=(long)index*bits;int word=(int)(bit>>>6),offset=(int)(bit&63);
        if(word>=data.length)return -1;
        long value=data[word]>>>offset;
        if(offset+bits>64&&word+1<data.length)value|=data[word+1]<<(64-offset);
        return (int)(value&((1L<<bits)-1L));
    }

    private IBlockState readPaletteState(NBTTagCompound entry){
        String name=entry.getString("Name");if(name.isEmpty())return null;
        Block block=Block.REGISTRY.getObject(new ResourceLocation(name));
        if(block==null||block==Blocks.AIR)return null;
        IBlockState state=block.getDefaultState();
        NBTTagCompound properties=entry.getCompoundTag("Properties");
        for(String key:properties.getKeySet()){
            IProperty<?> property=property(block,key);
            if(property!=null)state=applyProperty(state,property,properties.getString(key));
        }
        return state;
    }

    private IProperty<?> property(Block block,String name){for(IProperty<?> value:block.getBlockState().getProperties())if(value.getName().equals(name))return value;return null;}
    @SuppressWarnings({"rawtypes","unchecked"}) private IBlockState applyProperty(IBlockState state,IProperty property,String value){
        com.google.common.base.Optional parsed=property.parseValue(value);
        return parsed.isPresent()?state.withProperty(property,(Comparable)parsed.get()):state;
    }

    @Override protected void onEnable(){cursor=0;pendingPos=null;cooldown=0;}

    @Override public void tick(){
        if(mc.player==null||mc.world==null||mc.playerController==null||origin==null||placements.isEmpty())return;
        if(cooldown>0)cooldown--;
        if(pendingPos!=null){
            IBlockState current=mc.world.getBlockState(pendingPos);
            if(current.getBlock()==pendingState.getBlock()){
                if(++pendingTicks>=4){pendingPos=null;pendingState=null;pendingTicks=0;}
            }else if(++pendingTicks>10){pendingPos=null;pendingState=null;pendingTicks=0;cooldown=3;}
            else return;
            if(pendingPos!=null||cooldown>0)return;
        }
        if(cooldown>0)return;
        Placement task=nextReachable();
        if(task==null){if(autoWalk!=0)walkToNext();else stopWalking();return;}
        stopWalking();
        BlockPos target=origin.add(task.relative);
        if(!mc.world.isAirBlock(target)&&mc.world.getBlockState(target).getBlock()!=task.state.getBlock())return;
        int slot=findItem(task.state);
        if(slot<0)return;
        for(EnumFacing side:EnumFacing.values()){
            BlockPos support=target.offset(side);
            if(mc.world.isAirBlock(support)||!mc.world.getBlockState(support).isSideSolid(mc.world,support,side.getOpposite()))continue;
            EnumFacing face=side.getOpposite();Vec3d hit=InventoryBlocks.hitVec(support,face);
            if(mc.player.getPositionEyes(1F).squareDistanceTo(hit)>(double)reach*reach)continue;
            mc.player.inventory.currentItem=slot;
            float oldYaw=mc.player.rotationYaw,oldPitch=mc.player.rotationPitch;
            float[] look=rotation(hit);
            if(silentAim!=0&&mc.getConnection()!=null)dev.goldforge.network.PacketManager.send(new CPacketPlayer.Rotation(look[0],look[1],mc.player.onGround),name);
            EnumActionResult result=mc.playerController.processRightClickBlock(mc.player,mc.world,support,face,hit,EnumHand.MAIN_HAND);
            if(silentAim!=0&&mc.getConnection()!=null)dev.goldforge.network.PacketManager.send(new CPacketPlayer.Rotation(oldYaw,oldPitch,mc.player.onGround),name);
            if(result==EnumActionResult.SUCCESS){
                mc.player.swingArm(EnumHand.MAIN_HAND);pendingPos=target.toImmutable();pendingState=task.state;pendingTicks=0;cooldown=placeDelay;cursor=(placements.indexOf(task)+1)%placements.size();return;
            }
        }
        cursor=(cursor+1)%placements.size();
    }

    private Placement nextReachable(){
        double max=(double)reach*reach;
        for(int offset=0;offset<placements.size();offset++){
            int index=(cursor+offset)%placements.size();Placement task=placements.get(index);BlockPos pos=origin.add(task.relative);
            if(mc.world.getBlockState(pos).getBlock()==task.state.getBlock())continue;
            if(!mc.world.getBlockState(pos).getMaterial().isReplaceable())continue;
            if(mc.player.getDistanceSqToCenter(pos)>max)continue;
            cursor=index;return task;
        }
        return null;
    }

    private int findItem(IBlockState state){
        int metadata=state.getBlock().getMetaFromState(state),preferred=mc.player.inventory.currentItem;
        for(int i=0;i<36;i++){
            ItemStack stack=mc.player.inventory.getStackInSlot(i);
            if(stack.isEmpty()||!(stack.getItem() instanceof ItemBlock)||((ItemBlock)stack.getItem()).getBlock()!=state.getBlock())continue;
            int hotbar=i<9?i:preferred;
            if(i>=9)mc.playerController.windowClick(mc.player.inventoryContainer.windowId,i,hotbar,ClickType.SWAP,mc.player);
            return hotbar;
        }
        return -1;
    }

    private void walkToNext(){
        Placement next=null;double best=Double.MAX_VALUE;
        for(Placement p:placements){BlockPos pos=origin.add(p.relative);if(mc.world.getBlockState(pos).getBlock()==p.state.getBlock())continue;double d=mc.player.getDistanceSqToCenter(pos);if(d<best){best=d;next=p;}}
        if(next==null){stopWalking();setEnabled(false);return;}
        BlockPos pos=origin.add(next.relative);double dx=pos.getX()+.5D-mc.player.posX,dz=pos.getZ()+.5D-mc.player.posZ;
        float yaw=(float)(Math.atan2(dz,dx)*180D/Math.PI)-90F;
        mc.player.rotationYaw=yaw;mc.player.rotationYawHead=yaw;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),true);walking=true;
        if(mc.player.collidedHorizontally&&mc.player.onGround)KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),true);
        else KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),false);
    }

    private void stopWalking(){
        if(!walking)return;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),false);walking=false;
    }

    private float[] rotation(Vec3d hit){
        Vec3d eyes=mc.player.getPositionEyes(1F);double dx=hit.x-eyes.x,dy=hit.y-eyes.y,dz=hit.z-eyes.z;
        return new float[]{(float)(Math.atan2(dz,dx)*180D/Math.PI)-90F,(float)(-Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))*180D/Math.PI)};
    }

    @Override protected void onDisable(){stopWalking();pendingPos=null;pendingState=null;pendingTicks=0;cooldown=0;}

    private static final class Placement{final BlockPos relative;final IBlockState state;Placement(BlockPos p,IBlockState s){relative=p;state=s;}}
}
