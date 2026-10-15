package org.figuramc.figura.model.ysm;

/**
 * Seeded STB Perlin noise without a native runtime dependency.
 * Adapted from Sean Barrett's stb_perlin.h v0.5 under its public-domain option:
 * https://github.com/nothings/stb/blob/master/stb_perlin.h
 */
public final class YsmPerlinNoise {
    private static final int[] PERMUTATION = {
            23, 125, 161, 52, 103, 117, 70, 37, 247, 101, 203, 169, 124, 126, 44, 123,
            152, 238, 145, 45, 171, 114, 253, 10, 192, 136, 4, 157, 249, 30, 35, 72,
            175, 63, 77, 90, 181, 16, 96, 111, 133, 104, 75, 162, 93, 56, 66, 240,
            8, 50, 84, 229, 49, 210, 173, 239, 141, 1, 87, 18, 2, 198, 143, 57,
            225, 160, 58, 217, 168, 206, 245, 204, 199, 6, 73, 60, 20, 230, 211, 233,
            94, 200, 88, 9, 74, 155, 33, 15, 219, 130, 226, 202, 83, 236, 42, 172,
            165, 218, 55, 222, 46, 107, 98, 154, 109, 67, 196, 178, 127, 158, 13, 243,
            65, 79, 166, 248, 25, 224, 115, 80, 68, 51, 184, 128, 232, 208, 151, 122,
            26, 212, 105, 43, 179, 213, 235, 148, 146, 89, 14, 195, 28, 78, 112, 76,
            250, 47, 24, 251, 140, 108, 186, 190, 228, 170, 183, 139, 39, 188, 244, 246,
            132, 48, 119, 144, 180, 138, 134, 193, 82, 182, 120, 121, 86, 220, 209, 3,
            91, 241, 149, 85, 205, 150, 113, 216, 31, 100, 41, 164, 177, 214, 153, 231,
            38, 71, 185, 174, 97, 201, 29, 95, 7, 92, 54, 254, 191, 118, 34, 221,
            131, 11, 163, 99, 234, 81, 227, 147, 156, 176, 17, 142, 69, 12, 110, 62,
            27, 255, 0, 194, 59, 116, 242, 252, 19, 21, 187, 53, 207, 129, 64, 135,
            61, 40, 167, 237, 102, 223, 106, 159, 197, 189, 215, 137, 36, 32, 22, 5
    };

    private static final int[] GRADIENT_INDEX = {
            7, 9, 5, 0, 11, 1, 6, 9, 3, 9, 11, 1, 8, 10, 4, 7,
            8, 6, 1, 5, 3, 10, 9, 10, 0, 8, 4, 1, 5, 2, 7, 8,
            7, 11, 9, 10, 1, 0, 4, 7, 5, 0, 11, 6, 1, 4, 2, 8,
            8, 10, 4, 9, 9, 2, 5, 7, 9, 1, 7, 2, 2, 6, 11, 5,
            5, 4, 6, 9, 0, 1, 1, 0, 7, 6, 9, 8, 4, 10, 3, 1,
            2, 8, 8, 9, 10, 11, 5, 11, 11, 2, 6, 10, 3, 4, 2, 4,
            9, 10, 3, 2, 6, 3, 6, 10, 5, 3, 4, 10, 11, 2, 9, 11,
            1, 11, 10, 4, 9, 4, 11, 0, 4, 11, 4, 0, 0, 0, 7, 6,
            10, 4, 1, 3, 11, 5, 3, 4, 2, 9, 1, 3, 0, 1, 8, 0,
            6, 7, 8, 7, 0, 4, 6, 10, 8, 2, 3, 11, 11, 8, 0, 2,
            4, 8, 3, 0, 0, 10, 6, 1, 2, 2, 4, 5, 6, 0, 1, 3,
            11, 9, 5, 5, 9, 6, 9, 8, 3, 8, 1, 8, 9, 6, 9, 11,
            10, 7, 5, 6, 5, 9, 1, 3, 7, 0, 2, 10, 11, 2, 6, 1,
            3, 11, 7, 7, 2, 1, 7, 3, 0, 8, 1, 1, 5, 0, 6, 10,
            11, 11, 0, 2, 7, 0, 10, 8, 3, 5, 7, 1, 11, 1, 0, 7,
            9, 0, 11, 5, 10, 3, 2, 3, 5, 9, 7, 9, 8, 4, 6, 5
    };

    private static final float[][] GRADIENTS = {
            {1f, 1f, 0f}, {-1f, 1f, 0f}, {1f, -1f, 0f}, {-1f, -1f, 0f},
            {1f, 0f, 1f}, {-1f, 0f, 1f}, {1f, 0f, -1f}, {-1f, 0f, -1f},
            {0f, 1f, 1f}, {0f, -1f, 1f}, {0f, 1f, -1f}, {0f, -1f, -1f}
    };

    private YsmPerlinNoise() {
    }

    public static float sample(int seed, float x, float y, float z) {
        // STB casts coordinates to int; reject its undefined nonfinite/out-of-range inputs.
        if (!validCoordinate(x) || !validCoordinate(y) || !validCoordinate(z)) {
            return 0f;
        }
        int floorX = floor(x);
        int floorY = floor(y);
        int floorZ = floor(z);
        int x0 = floorX & 255;
        int x1 = (floorX + 1) & 255;
        int y0 = floorY & 255;
        int y1 = (floorY + 1) & 255;
        int z0 = floorZ & 255;
        int z1 = (floorZ + 1) & 255;
        x -= floorX;
        y -= floorY;
        z -= floorZ;
        float u = ease(x);
        float v = ease(y);
        float w = ease(z);

        int r0 = permutation(x0 + (seed & 255));
        int r1 = permutation(x1 + (seed & 255));
        int r00 = permutation(r0 + y0);
        int r01 = permutation(r0 + y1);
        int r10 = permutation(r1 + y0);
        int r11 = permutation(r1 + y1);

        float n00 = lerp(gradient(r00 + z0, x, y, z), gradient(r00 + z1, x, y, z - 1f), w);
        float n01 = lerp(gradient(r01 + z0, x, y - 1f, z), gradient(r01 + z1, x, y - 1f, z - 1f), w);
        float n10 = lerp(gradient(r10 + z0, x - 1f, y, z), gradient(r10 + z1, x - 1f, y, z - 1f), w);
        float n11 = lerp(gradient(r11 + z0, x - 1f, y - 1f, z), gradient(r11 + z1, x - 1f, y - 1f, z - 1f), w);
        return lerp(lerp(n00, n01, v), lerp(n10, n11, v), u);
    }

    private static boolean validCoordinate(float value) {
        return Float.isFinite(value) && value >= -2147483648f && value < 2147483648f;
    }

    private static int floor(float value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static int permutation(int index) {
        return PERMUTATION[index & 255];
    }

    private static float gradient(int index, float x, float y, float z) {
        float[] basis = GRADIENTS[GRADIENT_INDEX[index & 255]];
        return basis[0] * x + basis[1] * y + basis[2] * z;
    }

    private static float ease(float value) {
        return (((value * 6f - 15f) * value + 10f) * value * value * value);
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }
}
