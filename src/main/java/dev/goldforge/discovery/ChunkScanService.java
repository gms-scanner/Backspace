package dev.goldforge.discovery;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded client-thread chunk sampler. No worker thread touches world or chunk state. */
public final class ChunkScanService {
    public interface Listener { void scanned(Snapshot snapshot); }
    public static final class Snapshot {
        public final int chunkX, chunkZ, dimension;
        public final long time;
        public final Map<String,Integer> blocks;
        public final Map<String,Integer> tiles;
        public final List<BlockPos> storage, portals;
        public final int undergroundAir, undergroundSolid, surfaceArtificial;
        public final int roadEastWest, roadNorthSouth;
        Snapshot(Job job) {
            chunkX=job.x;chunkZ=job.z;dimension=job.dimension;time=System.currentTimeMillis();
            blocks=Collections.unmodifiableMap(new HashMap<String,Integer>(job.blocks));
            tiles=Collections.unmodifiableMap(new HashMap<String,Integer>(job.tiles));
            storage=Collections.unmodifiableList(new ArrayList<BlockPos>(job.storage));
            portals=Collections.unmodifiableList(new ArrayList<BlockPos>(job.portals));
            undergroundAir=job.undergroundAir;undergroundSolid=job.undergroundSolid;surfaceArtificial=job.surfaceArtificial;
            int horizontal=0,vertical=0;for(int n:job.roadRows)horizontal=Math.max(horizontal,Integer.bitCount(n));for(int n:job.roadCols)vertical=Math.max(vertical,Integer.bitCount(n));
            roadEastWest=horizontal;roadNorthSouth=vertical;
        }
        public int count(String name) { Integer n=blocks.get(name);return n==null?0:n; }
        public int tileCount(String name) { Integer n=tiles.get(name);return n==null?0:n; }
    }
    private static final class Job {
        final int x,z,dimension; int cursor; boolean tilesDone;
        final Map<String,Integer> blocks=new HashMap<String,Integer>(),tiles=new HashMap<String,Integer>();
        final List<BlockPos> storage=new ArrayList<BlockPos>(),portals=new ArrayList<BlockPos>();
        int undergroundAir,undergroundSolid,surfaceArtificial;
        final int[] roadRows=new int[4096],roadCols=new int[4096];
        Job(int x,int z,int dimension){this.x=x;this.z=z;this.dimension=dimension;}
    }
    private final ArrayDeque<Job> queue=new ArrayDeque<Job>();
    private final Set<Long> queued=new HashSet<Long>();
    private final List<Listener> listeners=new ArrayList<Listener>();
    private Job active;
    private int dimension=Integer.MIN_VALUE;
    private boolean seeded;
    private long scannedChunks,scannedBlocks,lastNanos;
    public int blockBudget=4096,queueLimit=512;
    public void addListener(Listener listener){if(!listeners.contains(listener))listeners.add(listener);}
    public void clear(){queue.clear();queued.clear();active=null;dimension=Integer.MIN_VALUE;seeded=false;}
    public int pending(){return queue.size()+(active==null?0:1);}
    public long scannedChunks(){return scannedChunks;}
    public long scannedBlocks(){return scannedBlocks;}
    public long lastNanos(){return lastNanos;}
    public void enqueue(Chunk chunk){
        if(chunk==null||Minecraft.getMinecraft().world==null)return;
        int dim=Minecraft.getMinecraft().world.provider.getDimension();
        if(dimension!=dim){clear();dimension=dim;}
        long key=key(chunk.x,chunk.z);
        if(queued.add(key)){
            if(queue.size()>=queueLimit){Job removed=queue.removeFirst();queued.remove(key(removed.x,removed.z));}
            queue.addLast(new Job(chunk.x,chunk.z,dim));
        }
    }
    public void tick(){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.world==null||mc.player==null){clear();return;}
        if(dimension!=mc.world.provider.getDimension()){clear();dimension=mc.world.provider.getDimension();}
        if(!(mc.world.getChunkProvider() instanceof ChunkProviderClient))return;
        ChunkProviderClient provider=(ChunkProviderClient)mc.world.getChunkProvider();
        if(!seeded){
            seeded=true;
            int radius=Math.min(8,Math.max(2,mc.gameSettings.renderDistanceChunks));
            for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                Chunk chunk=provider.getLoadedChunk(mc.player.chunkCoordX+dx,mc.player.chunkCoordZ+dz);
                if(chunk!=null)enqueue(chunk);
            }
        }
        long start=System.nanoTime();int remaining=Math.max(256,Math.min(16384,blockBudget));
        while(remaining>0){
            if(active==null){active=queue.pollFirst();if(active==null)break;}
            Chunk chunk=provider.getLoadedChunk(active.x,active.z);
            if(chunk==null){queued.remove(key(active.x,active.z));active=null;continue;}
            if(!active.tilesDone){
                for(TileEntity tile:chunk.getTileEntityMap().values()){
                    if(tile==null||tile.isInvalid())continue;
                    String name=tile.getClass().getSimpleName();add(active.tiles,name);
                    BlockPos pos=tile.getPos();
                    if(name.contains("Chest")||name.contains("Hopper")||name.contains("Shulker")||name.contains("Furnace"))active.storage.add(pos.toImmutable());
                }
                active.tilesDone=true;
            }
            while(remaining>0&&active.cursor<65536){
                int index=active.cursor++,x=index&15,z=(index>>4)&15,y=index>>8;
                BlockPos pos=new BlockPos(active.x*16+x,y,active.z*16+z);
                IBlockState state=chunk.getBlockState(pos);Block block=state.getBlock();
                if(block==Blocks.AIR){if(y<60)active.undergroundAir++;}
                else {
                    String id=String.valueOf(Block.REGISTRY.getNameForObject(block));
                    if(isEvidence(block)){
                        add(active.blocks,id);
                        if(y>=60)active.surfaceArtificial++;
                        if(block==Blocks.PORTAL&&active.portals.size()<64)active.portals.add(pos);
                        if(y>=32&&y<=128&&isRoad(block)){active.roadRows[y*16+z]|=1<<x;active.roadCols[y*16+x]|=1<<z;}
                    }
                    if(y<60)active.undergroundSolid++;
                }
                remaining--;scannedBlocks++;
            }
            if(active.cursor==65536){
                Snapshot snapshot=new Snapshot(active);
                queued.remove(key(active.x,active.z));active=null;scannedChunks++;
                for(Listener listener:new ArrayList<Listener>(listeners))listener.scanned(snapshot);
            }
        }
        lastNanos=System.nanoTime()-start;
    }
    private static boolean isEvidence(Block block){return block==Blocks.CHEST||block==Blocks.TRAPPED_CHEST||block==Blocks.ENDER_CHEST
            ||block==Blocks.HOPPER||block==Blocks.FURNACE||block==Blocks.LIT_FURNACE||block==Blocks.DISPENSER
            ||block==Blocks.DROPPER||block==Blocks.OBSIDIAN||block==Blocks.CRAFTING_TABLE||block==Blocks.ANVIL
            ||block==Blocks.FARMLAND||block==Blocks.WHEAT||block==Blocks.CARROTS||block==Blocks.POTATOES
            ||block==Blocks.NETHER_WART||block==Blocks.PORTAL||block==Blocks.NETHER_BRICK
            ||block==Blocks.COBBLESTONE||block==Blocks.STONEBRICK||block==Blocks.PLANKS
            ||block==Blocks.TORCH||block==Blocks.RAIL||block==Blocks.GOLDEN_RAIL||block==Blocks.LADDER
            ||block==Blocks.BED||block==Blocks.BREWING_STAND||block==Blocks.ENCHANTING_TABLE
            ||block==Blocks.WHITE_SHULKER_BOX||block==Blocks.ORANGE_SHULKER_BOX||block==Blocks.MAGENTA_SHULKER_BOX
            ||block==Blocks.LIGHT_BLUE_SHULKER_BOX||block==Blocks.YELLOW_SHULKER_BOX||block==Blocks.LIME_SHULKER_BOX
            ||block==Blocks.PINK_SHULKER_BOX||block==Blocks.GRAY_SHULKER_BOX||block==Blocks.SILVER_SHULKER_BOX
            ||block==Blocks.CYAN_SHULKER_BOX||block==Blocks.PURPLE_SHULKER_BOX||block==Blocks.BLUE_SHULKER_BOX
            ||block==Blocks.BROWN_SHULKER_BOX||block==Blocks.GREEN_SHULKER_BOX||block==Blocks.RED_SHULKER_BOX
            ||block==Blocks.BLACK_SHULKER_BOX;}
    private static boolean isRoad(Block block){return block==Blocks.COBBLESTONE||block==Blocks.STONEBRICK
            ||block==Blocks.NETHER_BRICK||block==Blocks.PLANKS||block==Blocks.OBSIDIAN
            ||block==Blocks.RAIL||block==Blocks.GOLDEN_RAIL||block==Blocks.TORCH;}
    private static void add(Map<String,Integer> map,String key){Integer n=map.get(key);map.put(key,n==null?1:n+1);}
    private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
}
