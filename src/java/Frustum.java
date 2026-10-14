import javax.microedition.m3g.Transform;

/** Пирамида видимости камеры: четыре боковые плоскости и глубина вдоль взгляда (для отсечения по туману). */
final class Frustum {
    /** внутренние нормали плоскостей: право, лево, верх, низ */
    private final float[] n = new float[12];
    private final float[] m = new float[16];
    float ax, ay, az;
    /** направление взгляда */
    float fx, fy, fz;

    /** camT — положение камеры в мире (как в Graphics3D.setCamera), fovY в градусах. */
    void set(Transform camT, float fovY, float aspect) {
        camT.get(m);
        ax = m[3]; ay = m[7]; az = m[11];
        // камера смотрит по -Z
        fx = -m[2]; fy = -m[6]; fz = -m[10];
        float tv = (float) Math.tan(fovY * 0.5f * Mth.DEG), th = tv * aspect;
        plane(0, -1, 0, -th);
        plane(1, 1, 0, -th);
        plane(2, 0, -1, -tv);
        plane(3, 0, 1, -tv);
    }

    private void plane(int i, float x, float y, float z) {
        float wx = m[0] * x + m[1] * y + m[2] * z, wy = m[4] * x + m[5] * y + m[6] * z, wz = m[8] * x + m[9] * y + m[10] * z;
        n[i * 3] = wx; n[i * 3 + 1] = wy; n[i * 3 + 2] = wz;
    }

    /** Видна ли рамка: не целиком снаружи одной из плоскостей и ближе maxDepth по взгляду. */
    boolean visible(float x0, float y0, float z0, float x1, float y1, float z1, float maxDepth) {
        for (int i = 0; i < 12; i += 3) {
            float nx = n[i], ny = n[i + 1], nz = n[i + 2];
            float px = nx > 0 ? x1 : x0, py = ny > 0 ? y1 : y0, pz = nz > 0 ? z1 : z0;
            if (nx * (px - ax) + ny * (py - ay) + nz * (pz - az) < 0) return false;
        }
        // ближайшая по глубине вершина рамки
        float px = fx > 0 ? x0 : x1, py = fy > 0 ? y0 : y1, pz = fz > 0 ? z0 : z1;
        return fx * (px - ax) + fy * (py - ay) + fz * (pz - az) <= maxDepth;
    }
}
