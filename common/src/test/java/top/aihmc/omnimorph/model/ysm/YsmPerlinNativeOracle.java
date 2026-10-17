package top.aihmc.omnimorph.model.ysm;

import org.lwjgl.stb.STBPerlin;

import java.util.Random;

/** Optional numerical oracle using Minecraft's existing LWJGL/STB, never production code. */
public final class YsmPerlinNativeOracle {
    public static void main(String[] args) {
        int[] seeds = {0, 1, 42, 255, 256, -1, -257, Integer.MIN_VALUE, Integer.MAX_VALUE};
        float[][] positions = {
                {0.25f, 0f, 0f}, {0.25f, 0.5f, 0f}, {0.25f, 0.5f, 0.75f},
                {-0.25f, -1.5f, 2.75f}, {255.75f, 256.5f, -256.25f}
        };
        for (int seed : seeds) {
            for (float[] position : positions) {
                float expected = noise(seed, position[0], position[1], position[2]);
                compare(expected, YsmPerlinNoise.sample(seed, position[0], position[1], position[2]));
                System.out.printf(java.util.Locale.ROOT, "{%d, %sf, %sf, %sf, %sf},%n",
                        seed, position[0], position[1], position[2], Float.toString(expected));
            }
        }
        Random random = new Random(20261013L);
        for (int sample = 0; sample < 20_000; sample++) {
            int seed = random.nextInt();
            float x = random.nextFloat() * 1024f - 512f;
            float y = random.nextFloat() * 1024f - 512f;
            float z = random.nextFloat() * 1024f - 512f;
            compare(noise(seed, x, y, z), YsmPerlinNoise.sample(seed, x, y, z));
        }
        System.out.println("M4 native STB/Java Perlin differential passed: 45 fixed and 20000 seeded random points.");
    }

    private static float noise(int seed, float x, float y, float z) {
        return STBPerlin.stb_perlin_noise3_seed(x, y, z, 0, 0, 0, seed);
    }

    private static void compare(float expected, float actual) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > 0.000002f) {
            throw new AssertionError("Native STB/Java Perlin differs: " + expected + " / " + actual);
        }
    }
}
