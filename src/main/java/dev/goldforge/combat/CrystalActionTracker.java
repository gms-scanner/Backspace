package dev.goldforge.combat;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Attempts are pending until a world observation confirms them; timeouts permit bounded retries. */
public final class CrystalActionTracker<K> {
    private final Map<K, Attempt> attempts = new HashMap<K, Attempt>();

    public boolean ready(K key, long tick) {
        Attempt attempt = attempts.get(key);
        return attempt == null || tick >= attempt.retryAt;
    }

    public void sent(K key, long tick, int retryTicks, int retryLimit, int backoffTicks) {
        Attempt previous = attempts.get(key);
        int count = previous == null || tick >= previous.resetAt ? 1 : previous.count + 1;
        int delay = count >= retryLimit ? Math.max(retryTicks, backoffTicks) : retryTicks;
        attempts.put(key, new Attempt(count, tick + Math.max(1, delay),
                tick + Math.max(1, count >= retryLimit ? delay : backoffTicks)));
    }

    public boolean confirm(K key) { return attempts.remove(key) != null; }

    public int pending(long tick) {
        int result = 0;
        for (Attempt attempt : attempts.values()) if (tick < attempt.retryAt) result++;
        return result;
    }

    public void retain(Set<K> liveKeys) { attempts.keySet().retainAll(liveKeys); }

    public void prune(long tick) {
        Iterator<Attempt> iterator = attempts.values().iterator();
        while (iterator.hasNext()) if (tick > iterator.next().resetAt + 40L) iterator.remove();
    }

    public void clear() { attempts.clear(); }

    private static final class Attempt {
        final int count;
        final long retryAt, resetAt;
        Attempt(int count, long retryAt, long resetAt) {
            this.count = count; this.retryAt = retryAt; this.resetAt = resetAt;
        }
    }
}
