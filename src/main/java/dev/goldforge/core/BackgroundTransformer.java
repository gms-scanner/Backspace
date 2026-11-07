package dev.goldforge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

/** Keep client and integrated-server ticks running behind every in-world menu. */
public final class BackgroundTransformer implements IClassTransformer {
    public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes==null || !"net.minecraft.client.Minecraft".equals(transformedName))return bytes;
        ClassReader reader=new ClassReader(bytes);
        ClassWriter writer=new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM5,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                return new MethodVisitor(Opcodes.ASM5,super.visitMethod(access,name,desc,signature,exceptions)){
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf){
                        if("()Z".equals(desc) && ("doesGuiPauseGame".equals(method)||"func_73868_f".equals(method))){
                            // Replace the receiver-consuming boolean call with false;
                            // stack size and stack-map frames remain unchanged.
                            super.visitInsn(Opcodes.POP);
                            super.visitInsn(Opcodes.ICONST_0);
                        }else super.visitMethodInsn(opcode,owner,method,desc,itf);
                    }
                };
            }
        },0);
        return writer.toByteArray();
    }
}
