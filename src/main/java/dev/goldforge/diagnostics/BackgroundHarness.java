package dev.goldforge.diagnostics;
import dev.goldforge.core.BackgroundTransformer;
import org.objectweb.asm.*;
import java.io.*;
public final class BackgroundHarness {
 public static void main(String[] args)throws Exception{
  InputStream in=BackgroundHarness.class.getResourceAsStream("/net/minecraft/client/Minecraft.class");
  if(in==null)throw new AssertionError("Minecraft class missing");
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
  while((n=in.read(b))!=-1)out.write(b,0,n);in.close();
  byte[] original=out.toByteArray();int before=count(original);
  if(before<1)throw new AssertionError("No pause decision found");
  byte[] patched=new BackgroundTransformer().transform("net.minecraft.client.Minecraft","net.minecraft.client.Minecraft",original);
  if(count(patched)!=0)throw new AssertionError("Pause decision still present");
  if(new BackgroundTransformer().transform("Other","Other",original)!=original)throw new AssertionError("Unrelated class changed");
  System.out.println("Background harness: PASS ("+before+" real Minecraft pause checks removed)");
 }
 private static int count(byte[] bytes){
  final int[] count={0};
  new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5){
   public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){return new MethodVisitor(Opcodes.ASM5){
    public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){
     if(name.equals("doesGuiPauseGame")||name.equals("func_73868_f"))count[0]++;
    }
   };}
  },0);return count[0];
 }
}
