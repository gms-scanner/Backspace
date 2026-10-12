package dev.goldforge.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, source-informed geometry harness for NCP Passable axis-order
 * questions. This is intentionally a reduced model: it uses continuous swept
 * AABBs against supplied full-cube collision boxes. It does not emulate NCP's
 * block-shape adapters, block-change tracker, step cap, or SurvivalFly state.
 *
 * The fixtures isolate axis-order and boundary behavior without sending game
 * packets or changing player/world state. Run with the Gradle task
 * {@code ncpPassableHarness}, or invoke {@code /backspace phase model} in game.
 */
public final class NcpPassableHarness {
    private static final Axis[][] ORDERS = {
            {Axis.Y, Axis.X, Axis.Z},
            {Axis.Y, Axis.Z, Axis.X},
            {Axis.X, Axis.Z, Axis.Y},
            {Axis.Z, Axis.X, Axis.Y}
    };
    private static final String[] ORDER_NAMES = {"YXZ", "YZX", "XZY", "ZXY"};

    private NcpPassableHarness() {}

    public static List<String> runFixtures() {
        List<String> lines = new ArrayList<String>();
        int assertions = 0;

        Aabb player = new Aabb(.2D, 0D, .2D, .8D, 1.8D, .8D);
        Vec diagonal = new Vec(1D, 0D, 1D);
        List<Aabb> cornerBlocks = Collections.singletonList(new Aabb(1D, 0D, 0D, 2D, 2D, 1D));
        boolean[] cornerResults = evaluateAll(player, diagonal, cornerBlocks);
        assertResult(!cornerResults[0] && cornerResults[1] && !cornerResults[2] && cornerResults[3],
                "axis-order fixture", cornerResults);
        assertions++;
        lines.add("FIXTURE axis-order delta=1,0,1 block=[1,0,0..2,2,1] " + summarize(cornerResults));

        boolean[] clearResults = evaluateAll(player, diagonal, Collections.<Aabb>emptyList());
        assertResult(all(clearResults), "clear-path fixture", clearResults);
        assertions++;
        lines.add("FIXTURE clear-path delta=1,0,1 blocks=0 " + summarize(clearResults));

        Aabb touchingPlayer = new Aabb(0D, 0D, 0D, .6D, 1.8D, .6D);
        List<Aabb> touchingBlock = Collections.singletonList(new Aabb(.6D, 0D, 0D, 1.6D, 2D, 1D));
        boolean touching = touchingPlayer.intersects(touchingBlock.get(0));
        assertResult(!touching, "face-boundary fixture", new boolean[]{!touching});
        assertions++;
        lines.add("FIXTURE face-boundary sharedFaceX=0.6 intersects=" + touching + " expected=false");

        boolean[] wallResults = evaluateAll(player, new Vec(1D, 0D, 0D),
                Collections.singletonList(new Aabb(1D, 0D, 0D, 2D, 2D, 1D)));
        assertResult(!any(wallResults), "solid-wall fixture", wallResults);
        assertions++;
        lines.add("FIXTURE solid-wall delta=1,0,0 " + summarize(wallResults));

        lines.add("PASS assertions=" + assertions + " model=swept-AABB/full-cube axis orders=YXZ,YZX,XZY,ZXY");
        lines.add("LIMITATION not an exact NCP build: no NCP block-shape adapter, tracker, tracing cap, or SurvivalFly state");
        return Collections.unmodifiableList(lines);
    }

    /** Evaluate one movement without mutating state. A true value means this model found no collision. */
    public static boolean trace(Aabb start, Vec delta, List<Aabb> solids, Axis[] order) {
        Aabb current = start;
        for (Axis axis : order) {
            double amount = axis == Axis.X ? delta.x : axis == Axis.Y ? delta.y : delta.z;
            if (amount == 0D) continue;
            Aabb next = current.offset(axis == Axis.X ? amount : 0D,
                    axis == Axis.Y ? amount : 0D, axis == Axis.Z ? amount : 0D);
            Aabb swept = current.expandTo(next);
            for (Aabb solid : solids) if (swept.intersects(solid)) return false;
            current = next;
        }
        return true;
    }

    private static boolean[] evaluateAll(Aabb start, Vec delta, List<Aabb> solids) {
        boolean[] results = new boolean[ORDERS.length];
        for (int i = 0; i < ORDERS.length; i++) results[i] = trace(start, delta, solids, ORDERS[i]);
        return results;
    }

    private static boolean all(boolean[] values) { for (boolean value : values) if (!value) return false; return true; }
    private static boolean any(boolean[] values) { for (boolean value : values) if (value) return true; return false; }

    private static void assertResult(boolean condition, String fixture, boolean[] results) {
        if (!condition) throw new IllegalStateException(fixture + " failed: " + summarize(results));
    }

    private static String summarize(boolean[] results) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < results.length; i++) {
            if (i > 0) out.append(' ');
            out.append(ORDER_NAMES[i]).append('=').append(results[i] ? "PASSABLE" : "COLLISION");
        }
        return out.toString();
    }

    public static void main(String[] args) {
        for (String line : runFixtures()) System.out.println(line);
    }

    public enum Axis { X, Y, Z }

    public static final class Vec {
        public final double x, y, z;
        public Vec(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    public static final class Aabb {
        public final double minX, minY, minZ, maxX, maxY, maxZ;
        public Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Invalid AABB");
            this.minX=minX; this.minY=minY; this.minZ=minZ; this.maxX=maxX; this.maxY=maxY; this.maxZ=maxZ;
        }
        public Aabb offset(double x, double y, double z) {
            return new Aabb(minX+x,minY+y,minZ+z,maxX+x,maxY+y,maxZ+z);
        }
        public Aabb expandTo(Aabb other) {
            return new Aabb(Math.min(minX,other.minX),Math.min(minY,other.minY),Math.min(minZ,other.minZ),
                    Math.max(maxX,other.maxX),Math.max(maxY,other.maxY),Math.max(maxZ,other.maxZ));
        }
        public boolean intersects(Aabb other) {
            return maxX > other.minX && minX < other.maxX
                    && maxY > other.minY && minY < other.maxY
                    && maxZ > other.minZ && minZ < other.maxZ;
        }
        @Override public String toString() {
            return String.format(Locale.ROOT,"[%.3f,%.3f,%.3f..%.3f,%.3f,%.3f]",minX,minY,minZ,maxX,maxY,maxZ);
        }
    }
}
