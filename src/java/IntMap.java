import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * Высокопроизводительная хэш-таблица без создания объектов Integer на ключах.
 * Открытая адресация (open-addressing) с линейным пробингом.
 */
public final class IntMap {
    private int[] keys;
    private Object[] values;
    private boolean[] occupied;
    private int size;
    private int threshold;

    public IntMap() {
        this(16);
    }

    public IntMap(int initialCapacity) {
        int cap = 16;
        while (cap < initialCapacity) cap <<= 1;
        keys = new int[cap];
        values = new Object[cap];
        occupied = new boolean[cap];
        threshold = (int) (cap * 0.75f);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        size = 0;
        for (int i = 0; i < occupied.length; i++) {
            occupied[i] = false;
            values[i] = null;
        }
    }

    public Object get(int key) {
        int mask = keys.length - 1;
        int idx = hash(key) & mask;
        while (occupied[idx]) {
            if (keys[idx] == key) return values[idx];
            idx = (idx + 1) & mask;
        }
        return null;
    }

    public boolean containsKey(int key) {
        int mask = keys.length - 1;
        int idx = hash(key) & mask;
        while (occupied[idx]) {
            if (keys[idx] == key) return true;
            idx = (idx + 1) & mask;
        }
        return false;
    }

    public boolean contains(Object value) {
        if (value == null) return false;
        for (int i = 0; i < occupied.length; i++) {
            if (occupied[i] && value.equals(values[i])) return true;
        }
        return false;
    }

    public Object put(int key, Object value) {
        if (size >= threshold) rehash();
        int mask = keys.length - 1;
        int idx = hash(key) & mask;
        while (occupied[idx]) {
            if (keys[idx] == key) {
                Object old = values[idx];
                values[idx] = value;
                return old;
            }
            idx = (idx + 1) & mask;
        }
        occupied[idx] = true;
        keys[idx] = key;
        values[idx] = value;
        size++;
        return null;
    }

    public Object remove(int key) {
        int mask = keys.length - 1;
        int idx = hash(key) & mask;
        while (occupied[idx]) {
            if (keys[idx] == key) {
                Object old = values[idx];
                occupied[idx] = false;
                values[idx] = null;
                size--;
                // Rehash cluster
                idx = (idx + 1) & mask;
                while (occupied[idx]) {
                    int k = keys[idx];
                    Object v = values[idx];
                    occupied[idx] = false;
                    values[idx] = null;
                    size--;
                    put(k, v);
                    idx = (idx + 1) & mask;
                }
                return old;
            }
            idx = (idx + 1) & mask;
        }
        return null;
    }

    private void rehash() {
        int oldCap = keys.length;
        int newCap = oldCap << 1;
        int[] oldKeys = keys;
        Object[] oldValues = values;
        boolean[] oldOcc = occupied;

        keys = new int[newCap];
        values = new Object[newCap];
        occupied = new boolean[newCap];
        threshold = (int) (newCap * 0.75f);
        size = 0;

        for (int i = 0; i < oldCap; i++) {
            if (oldOcc[i]) {
                put(oldKeys[i], oldValues[i]);
            }
        }
    }

    private static int hash(int h) {
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    public Enumeration elements() {
        return new Enumeration() {
            int cur = 0;
            int found = -1;

            private boolean advance() {
                if (found >= 0) return true;
                while (cur < occupied.length) {
                    if (occupied[cur]) {
                        found = cur++;
                        return true;
                    }
                    cur++;
                }
                return false;
            }

            public boolean hasMoreElements() {
                return advance();
            }

            public Object nextElement() {
                if (!advance()) throw new NoSuchElementException();
                Object val = values[found];
                found = -1;
                return val;
            }
        };
    }
}
