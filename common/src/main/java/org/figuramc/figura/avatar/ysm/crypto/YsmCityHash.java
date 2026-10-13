package org.figuramc.figura.avatar.ysm.crypto;

/** YSM CityHash64 variant, adapted from Sparkle-Morpher. See LICENSE-Sparkle-Morpher.txt. */
final class YsmCityHash {
    private static final long k0 = 0xE4986A230E5AAA17L;
    private static final long k1 = 0x91AF10802CAB25A5L;
    private static final long k2 = 0xAF29CE778879D9C7L;
    private static final long kMul = 0xDE0F6EE09BDBAB91L;
    public long hash64(byte[] byteArray) {
        return hash64(byteArray, 0, byteArray.length);
    }

    public long hash64(byte[] byteArray, int base, int len) {
        if (len <= 32) {
            if (len <= 16) {
                return hashLen0to16(byteArray, base, len);
            } else {
                return hashLen17to32(byteArray, base, len);
            }
        } else if (len <= 64) {
            return hashLen33to64(byteArray, base, len);
        }

        // For strings over 64 bytes we hash the end first, and then as we
        // loop we keep 56 bytes of state: v, w, x, y, and z.
        long x = fetch64(byteArray, base + len - 40);
        long y = fetch64(byteArray, base + len - 16) + fetch64(byteArray, base + len - 56);
        long z = hashLen16(fetch64(byteArray, base + len - 48) + len, fetch64(byteArray, base + len - 24));

        long vLow, vHi;
        {
            long w0 = fetch64(byteArray, base + len - 64);
            long x0 = fetch64(byteArray, base + len - 56);
            long y0 = fetch64(byteArray, base + len - 48);
            long z0 = fetch64(byteArray, base + len - 40);
            long a = (long) len + w0;
            long b = rotate(z + a + z0, 21);
            long c = a;
            a += x0;
            a += y0;
            b += rotate(a, 44);
            vLow = a + z0;
            vHi = b + c;
        }
        long wLow, wHi;
        {
            long w0 = fetch64(byteArray, base + len - 32);
            long x0 = fetch64(byteArray, base + len - 24);
            long y0 = fetch64(byteArray, base + len - 16);
            long z0 = fetch64(byteArray, base + len - 8);
            long a = (y + k1) + w0;
            long b = rotate(x + a + z0, 21);
            long c = a;
            a += x0;
            a += y0;
            b += rotate(a, 44);
            wLow = a + z0;
            wHi = b + c;
        }
        x = x * k1 + fetch64(byteArray, base);

        // Decrease len to the nearest multiple of 64, and operate on 64-byte chunks.
        int remaining = (len - 1) & ~63;
        int pos = base;
        do {
            x = rotate(x + y + vLow + fetch64(byteArray, pos + 8), 37) * k1;
            y = rotate(y + vHi + fetch64(byteArray, pos + 48), 42) * k1;
            x ^= wHi;
            y += vLow + fetch64(byteArray, pos + 40);
            z = rotate(z + wLow, 33) * k1;
            // v = weakHashLen32WithSeeds(byteArray, pos, vHi * k1, x + wLow);
            {
                long w0 = fetch64(byteArray, pos);
                long x0 = fetch64(byteArray, pos + 8);
                long y0 = fetch64(byteArray, pos + 16);
                long z0 = fetch64(byteArray, pos + 24);
                long a = (vHi * k1) + w0;
                long b = rotate((x + wLow) + a + z0, 21);
                long c = a;
                a += x0;
                a += y0;
                b += rotate(a, 44);
                vLow = a + z0;
                vHi = b + c;
            }
            // w = weakHashLen32WithSeeds(byteArray, pos + 32, z + wHi, y + fetch64(byteArray, pos + 16));
            {
                long w0 = fetch64(byteArray, pos + 32);
                long x0 = fetch64(byteArray, pos + 40);
                long y0 = fetch64(byteArray, pos + 48);
                long z0 = fetch64(byteArray, pos + 56);
                long a = (z + wHi) + w0;
                long b = rotate((y + fetch64(byteArray, pos + 16)) + a + z0, 21);
                long c = a;
                a += x0;
                a += y0;
                b += rotate(a, 44);
                wLow = a + z0;
                wHi = b + c;
            }
            // swap z,x value
            long swapValue = x;
            x = z;
            z = swapValue;
            pos += 64;
            remaining -= 64;
        } while (remaining != 0);
        return hashLen16(hashLen16(vLow, wLow) + shiftMix(y) * k1 + z,
                hashLen16(vHi, wHi) + x);
    }

    public long hash64WithSeeds(byte[] raw, long seed0, long seed1) {
        return hashLen16(hash64(raw) - seed0, seed1);
    }

    public long hash64WithSeeds(byte[] raw, int base, int len, long seed0, long seed1) {
        return hashLen16(hash64(raw, base, len) - seed0, seed1);
    }


    public long hash64WithSeed(byte[] raw, long seed) {
        return hash64WithSeeds(raw, k2, seed);
    }

    public long hash64WithSeed(byte[] raw, int base, int len, long seed) {
        return hash64WithSeeds(raw, base, len, k2, seed);
    }

    private long hashLen0to16(byte[] byteArray, int base, int len) {
        if (len >= 8) {
            long mul = k2 + len * 2;
            long a = fetch64(byteArray, base) + k2;
            long b = fetch64(byteArray, base + len - 8);
            long c = rotate(b, 37) * mul + a;
            long d = (rotate(a, 25) + b) * mul;
            return hashLen16(c, d, mul);
        }
        if (len >= 4) {
            long mul = k2 + len * 2;
            long a = fetch32(byteArray, base) & 0xffffffffL;
            return hashLen16(len + (a << 3), fetch32(byteArray, base + len - 4) & 0xffffffffL, mul);
        }
        if (len > 0) {
            int a = byteArray[base] & 0xff;
            int b = byteArray[base + (len >>> 1)] & 0xff;
            int c = byteArray[base + len - 1] & 0xff;
            int y = a + (b << 8);
            int z = len + (c << 2);
            return shiftMix(y * k2 ^ z * k0) * k2;
        }
        return k2;
    }

    private long hashLen17to32(byte[] byteArray, int base, int len) {
        long mul = k2 + len * 2;
        long a = fetch64(byteArray, base) * k1;
        long b = fetch64(byteArray, base + 8);
        long c = fetch64(byteArray, base + len - 8) * mul;
        long d = fetch64(byteArray, base + len - 16) * k2;
        return hashLen16(rotate(a + b, 43) + rotate(c, 30) + d,
                a + rotate(b + k2, 18) + c, mul);
    }

    private long hashLen33to64(byte[] byteArray, int base, int len) {
        long mul = k2 + len * 2;
        long a = fetch64(byteArray, base) * k2;
        long b = fetch64(byteArray, base + 8);
        long c = fetch64(byteArray, base + len - 24);
        long d = fetch64(byteArray, base + len - 32);
        long e = fetch64(byteArray, base + 16) * k2;
        long f = fetch64(byteArray, base + 24) * 9;
        long g = fetch64(byteArray, base + len - 8);
        long h = fetch64(byteArray, base + len - 16) * mul;
        long u = rotate(a + g, 43) + (rotate(b, 30) + c) * 9;
        long v = ((a + g) ^ d) + f + 1;
        long w = Long.reverseBytes((u + v) * mul) + h;
        long x = rotate(e + f, 42) + c;
        long y = (Long.reverseBytes((v + w) * mul) + g) * mul;
        long z = e + f + c;
        a = Long.reverseBytes((x + z) * mul + y) + b;
        b = shiftMix((z + a) * mul + d + h) * mul;
        return b + x;
    }

    private long loadUnaligned64(final byte[] byteArray, final int start) {
        return  ((long) (byteArray[start]     & 0xFF))
             | (((long) (byteArray[start + 1] & 0xFF)) << 8)
             | (((long) (byteArray[start + 2] & 0xFF)) << 16)
             | (((long) (byteArray[start + 3] & 0xFF)) << 24)
             | (((long) (byteArray[start + 4] & 0xFF)) << 32)
             | (((long) (byteArray[start + 5] & 0xFF)) << 40)
             | (((long) (byteArray[start + 6] & 0xFF)) << 48)
             | (((long) (byteArray[start + 7] & 0xFF)) << 56);
    }

    private int loadUnaligned32(final byte[] byteArray, final int start) {
        return  (byteArray[start]     & 0xFF)
             | ((byteArray[start + 1] & 0xFF) << 8)
             | ((byteArray[start + 2] & 0xFF) << 16)
             | ((byteArray[start + 3] & 0xFF) << 24);
    }

    private long fetch64(byte[] byteArray, final int start) {
        return loadUnaligned64(byteArray, start);
    }

    private int fetch32(byte[] byteArray, final int start) {
        return loadUnaligned32(byteArray, start);
    }

    private long rotate(long val, int shift) {
        return shift == 0 ? val : ((val >>> shift) | (val << (64 - shift)));
    }


    private long hashLen16(long u, long v, long mul) {
        long a = (u ^ v) * mul;
        a ^= (a >>> 47);
        long b = (v ^ a) * mul;
        b ^= (b >>> 47);
        b *= mul;
        return b;
    }

    private long hashLen16(long u, long v) {
        return kMul * shiftMix((shiftMix((u ^ v) * kMul) ^ u) * kMul);
    }

    private long shiftMix(long val) {
        return val ^ (val >>> 47);
    }

}
