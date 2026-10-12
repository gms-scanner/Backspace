package dev.goldforge.diagnostics;

import dev.goldforge.combat.CrystalActionTracker;
import dev.goldforge.combat.CrystalMath;
import java.util.Collections;

/** Deterministic checks of production math and retry state; no running Minecraft required. */
public final class CrystalAuraHarness {
    private static int checks;

    public static void main(String[] args) {
        damage();
        selection();
        acknowledgements();
        cancellation();
        System.out.println("CrystalAura harness: " + checks + " checks passed.");
    }

    private static void damage() {
        near(85, CrystalMath.rawDamage(0, 1, 2), "Point blank normal explosion");
        near(32, CrystalMath.rawDamage(6, 1, 2), "Half-radius integer truncation");
        near(32, CrystalMath.rawDamage(0, .5, 2), "Half exposure");
        near(43.5, CrystalMath.rawDamage(0, 1, 1), "Easy difficulty");
        near(127.5, CrystalMath.rawDamage(0, 1, 3), "Hard difficulty");
        near(0, CrystalMath.rawDamage(0, 1, 0), "Peaceful difficulty");
        near(1, CrystalMath.rawDamage(12, 1, 2), "Radius boundary");
        near(0, CrystalMath.rawDamage(12.001, 1, 2), "Outside blast radius");
        near(1, CrystalMath.rawDamage(0, 0, 2), "Vanilla zero-exposure floor");
        near(85, CrystalMath.reduce(85, 0, 0, 0, 0), "Unarmored");
        near(71.4, CrystalMath.reduce(85, 20, 8, 0, 0), "Diamond armor and toughness");
        near(14.28, CrystalMath.reduce(85, 20, 8, 20, 0), "Maximum explosion protection");
        near(11.424, CrystalMath.reduce(85, 20, 8, 20, 1), "Resistance I reduces 20 percent");
        near(3.328, CrystalMath.reduce(32, 20, 8, 20, 0), "Armor uses incoming damage");
        near(14.28, CrystalMath.reduce(85, 20, 8, 100, 0), "Protection capped at 20");
        near(0, CrystalMath.reduce(85, 20, 8, 20, 5), "Resistance V");
    }

    private static void selection() {
        check(CrystalMath.safe(4, 20, 8, 2), "Ordinary safe action");
        check(!CrystalMath.safe(8, 6, 20, 0), "Maximum-self limit cannot allow lethal self damage");
        check(!CrystalMath.safe(8, 10, 20, 2), "Health reserve boundary");
        check(!CrystalMath.safe(9, 20, 8, 2), "Maximum self damage");
        check(!CrystalMath.safe(Double.NaN, 20, 8, 2), "Reject NaN self damage");
        check(!CrystalMath.safe(Double.POSITIVE_INFINITY, 20, 8, 2), "Reject infinite damage");
        check(!CrystalMath.safe(4 + 4, 9, 20, 2), "Aggregate actions respect remaining health");
        check(CrystalMath.qualifies(3, 1, 3, 6, 2), "Lethal target bypasses minimum");
        check(!CrystalMath.qualifies(3, 1, 20, 6, 0), "Healthy target requires minimum");
        check(!CrystalMath.qualifies(5, 4, 20, 4, 2), "Damage ratio rejects bad trade");
        check(CrystalMath.qualifies(8, 4, 20, 4, 2), "Damage ratio boundary");
        check(!CrystalMath.qualifies(0, 0, 20, 0, 0), "Never select zero damage");
        check(!CrystalMath.qualifies(Double.NaN, 0, 20, 0, 0), "Reject invalid target damage");
        check(CrystalMath.score(4, 1, 4, 4) > CrystalMath.score(20, 1, 30, 1), "Prioritize lethal damage");
        check(CrystalMath.score(8, 1, 20, 4) > CrystalMath.score(8, 4, 20, 4), "Prefer lower self damage");
    }

    private static void acknowledgements() {
        near(4, CrystalMath.confirmationTicks(4, 0), "Minimum acknowledgement window");
        near(8, CrystalMath.confirmationTicks(4, 250), "Ping-aware acknowledgement");
        near(40, CrystalMath.confirmationTicks(4, Integer.MAX_VALUE), "Ping overflow guard");
        near(4, CrystalMath.confirmationTicks(4, -100), "Negative ping guard");
        CrystalActionTracker<String> tracker = new CrystalActionTracker<String>();
        check(tracker.ready("base", 0), "New base available");
        tracker.sent("base", 0, 4, 3, 14);
        check(!tracker.ready("base", 3), "No duplicate during spawn wait");
        near(1, tracker.pending(3), "Sent placement remains pending");
        check(tracker.ready("base", 4), "Missing spawn becomes retryable");
        near(0, tracker.pending(4), "Expired attempt frees pipeline capacity");
        tracker.sent("base", 4, 4, 3, 14);
        tracker.sent("base", 8, 4, 3, 14);
        check(!tracker.ready("base", 12), "Third failure enters backoff");
        check(tracker.ready("base", 22), "Backoff eventually permits recovery");
        tracker.sent("base", 22, 4, 3, 14);
        check(tracker.ready("base", 26), "Attempt budget resets after backoff");
        tracker.confirm("base");
        check(tracker.ready("base", 23), "Observed spawn clears stale pending record");
        near(0, tracker.pending(23), "Confirmation frees capacity");

        tracker.sent("crystal", 30, 2, 3, 14);
        check(!tracker.ready("crystal", 31), "Break retries paced independently");
        check(tracker.ready("crystal", 32), "Unremoved crystal retryable");
        tracker.sent("other-base", 30, 8, 3, 18);
        near(2, tracker.pending(31), "Independent tracked keys");
        tracker.confirm("other-base");
        check(!tracker.ready("crystal", 31), "Placement confirmation preserves break retry");
        tracker.prune(100);
        check(tracker.ready("crystal", 0), "Stale records pruned");
    }

    private static void cancellation() {
        CrystalActionTracker<Integer> tracker = new CrystalActionTracker<Integer>();
        tracker.sent(1, 0, 4, 3, 14);
        tracker.sent(2, 0, 4, 3, 14);
        tracker.retain(Collections.singleton(2));
        check(tracker.ready(1, 1), "Removed crystal releases entity ID");
        check(!tracker.ready(2, 1), "Live crystal retains retry timer");
        tracker.clear();
        near(0, tracker.pending(1), "Disable/world change clears pending actions");
        check(tracker.ready(2, 0), "Reused entity ID after world change is fresh");
    }

    private static void near(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < .0001, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
