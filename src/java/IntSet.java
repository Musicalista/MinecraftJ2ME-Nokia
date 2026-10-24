/**
 * Легковесное множество int без автобоксинга Integer.
 */
public final class IntSet {
    private int[] keys;
    private boolean[] occupied;
    private int size;
    private int threshold;

    public IntSet() {
        this(16);
    }

    public IntSet(int initialCapacity) {
        int cap = 16;
        while (cap < initialCapacity) cap <<= 1;
        keys = new int[cap];
        occupied = new boolean[cap];
        threshold = (int) (cap * 0.75f);
    }

    public int size() {
        return size;
    }

    public void clear() {
        size = 0;
        for (int i = 0; i < occupied.length; i++) occupied[i] = false;
    }

    public boolean contains(int key) {
        int mask = keys.length - 1;
        int idx = hash(key) & mask;
        while (occupied[idx]) {
            if (keys[idx] == key) return true;
            idx = (idx + 1) & mask;
        }
        return false;
    }

    public boolean add(int key) {
        if (size >= threshold) rehash();
        int mask = keys.length - 1;
        int idx = hash(key) & mask;
        while (occupied[idx]) {
            if (keys[idx] == key) return false;
            idx = (idx + 1) & mask;
        }
        occupied[idx] = true;
        keys[idx] = key;
        size++;
        return true;
    }

    private void rehash() {
        int oldCap = keys.length;
        int newCap = oldCap << 1;
        int[] oldKeys = keys;
        boolean[] oldOcc = occupied;

        keys = new int[newCap];
        occupied = new boolean[newCap];
        threshold = (int) (newCap * 0.75f);
        size = 0;

        for (int i = 0; i < oldCap; i++) {
            if (oldOcc[i]) add(oldKeys[i]);
        }
    }

    private static int hash(int h) {
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }
}
