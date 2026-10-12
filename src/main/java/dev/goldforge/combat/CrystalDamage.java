package dev.goldforge.combat;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

/** Main-thread, per-plan snapshot. Armor and motion are sampled once, exposure at each candidate. */
public final class CrystalDamage {
    private CrystalDamage() {}

    public static final class Snapshot {
        public final EntityPlayer player;
        public final Vec3d position, predictedPosition;
        public final AxisAlignedBB bounds, predictedBounds;
        public final double health;
        private final float armor, toughness;
        private final int protection, resistance, difficulty;

        public Snapshot(EntityPlayer player, int predictionTicks) {
            this.player = player;
            position = player.getPositionVector();
            bounds = player.getEntityBoundingBox();
            health = player.getHealth() + player.getAbsorptionAmount();
            armor = player.getTotalArmorValue();
            toughness = (float) player.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue();
            protection = EnchantmentHelper.getEnchantmentModifierDamage(player.getArmorInventoryList(),
                    DamageSource.causeExplosionDamage(new Explosion(player.world, null, position.x, position.y, position.z, 6F, false, true)));
            PotionEffect effect = player.getActivePotionEffect(MobEffects.RESISTANCE);
            resistance = effect == null ? 0 : effect.getAmplifier() + 1;
            difficulty = player.world.getDifficulty().getDifficultyId();
            // Stop at collisions/unloaded terrain, and ignore teleports as motion samples.
            double dx = player.posX - player.prevPosX, dz = player.posZ - player.prevPosZ;
            double dy = player.onGround ? 0D : player.posY - player.prevPosY;
            if (dx * dx + dy * dy + dz * dz > 2.25D) dx = dy = dz = 0D;
            AxisAlignedBB predicted = bounds;
            for (int i = 0; i < Math.max(0, Math.min(5, predictionTicks)); i++) {
                AxisAlignedBB next = predicted.offset(dx, dy, dz);
                if (!player.world.isAreaLoaded(new net.minecraft.util.math.BlockPos(next.minX, next.minY, next.minZ),
                        new net.minecraft.util.math.BlockPos(next.maxX, next.maxY, next.maxZ))) break;
                if (!player.world.getCollisionBoxes(player, predicted.expand(dx, dy, dz)).isEmpty()) break;
                predicted = next;
            }
            predictedBounds = predicted;
            predictedPosition = position.addVector(predicted.minX - bounds.minX, predicted.minY - bounds.minY, predicted.minZ - bounds.minZ);
        }

        public double damage(World world, Vec3d explosion, boolean predicted) {
            if (player.isCreative() || player.isSpectator()) return 0D;
            Vec3d pos = predicted ? predictedPosition : position;
            double distance = pos.distanceTo(explosion);
            if (distance > 12D || difficulty == 0) return 0D;
            float density = world.getBlockDensity(explosion, predicted ? predictedBounds : bounds);
            return CrystalMath.reduce(CrystalMath.rawDamage(distance, density, difficulty), armor, toughness, protection, resistance);
        }

        public double maximumPossible(Vec3d explosion) {
            return CrystalMath.reduce(CrystalMath.rawDamage(predictedPosition.distanceTo(explosion), 1D, difficulty),
                    armor, toughness, protection, resistance);
        }
    }
}
