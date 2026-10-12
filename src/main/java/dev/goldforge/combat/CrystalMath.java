package dev.goldforge.combat;

/** Pure 1.12 crystal damage and selection rules, shared with the diagnostic harness. */
public final class CrystalMath {
    private CrystalMath() {}

    public static float rawDamage(double distance, double exposure, int difficulty) {
        if (distance > 12D || difficulty == 0) return 0F;
        double impact = (1D - Math.max(0D, distance) / 12D) * Math.max(0D, Math.min(1D, exposure));
        // Explosion#doExplosionA truncates to an integer before difficulty scaling.
        float raw = (int) ((impact * impact + impact) * 42D + 1D);
        if (difficulty == 1) return Math.min(raw * .5F + 1F, raw);
        return difficulty == 3 ? raw * 1.5F : raw;
    }

    public static float reduce(float raw, float armor, float toughness, int protection, int resistanceLevel) {
        float effectiveArmor = Math.max(armor * .2F, Math.min(20F, armor - raw / (2F + toughness / 4F)));
        float result = raw * (1F - effectiveArmor / 25F);
        result *= Math.max(0F, 1F - Math.max(0, resistanceLevel) * .2F);
        result *= 1F - Math.max(0, Math.min(20, protection)) / 25F;
        return Math.max(0F, result);
    }

    public static boolean safe(double selfDamage, double health, double maximum, double healthReserve) {
        return Double.isFinite(selfDamage) && selfDamage <= maximum && selfDamage < health - healthReserve;
    }

    public static boolean qualifies(double damage, double selfDamage, double health, double minimum, double ratio) {
        return Double.isFinite(damage) && damage > 0D && (damage >= minimum || damage >= health)
                && (damage >= health || damage >= selfDamage * ratio);
    }

    public static double score(double damage, double selfDamage, double health, double distance) {
        return (damage >= health ? 1000D : 0D) + Math.min(damage, health + 2D) - selfDamage * .65D - distance * .02D;
    }

    public static int confirmationTicks(int minimumTicks, int pingMillis) {
        return Math.max(Math.max(2, Math.min(40, minimumTicks)),
                (int) Math.min(40L, (Math.max(0L, pingMillis) + 49L) / 50L + 3L));
    }
}
