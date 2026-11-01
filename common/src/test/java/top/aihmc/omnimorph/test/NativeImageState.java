package top.aihmc.omnimorph.test;

import com.mojang.blaze3d.platform.NativeImage;

import java.lang.reflect.Field;

/** Reads the native allocation state without touching pixels after their memory has been freed. */
public final class NativeImageState {
    private static final Field PIXELS = pixelsField();

    private NativeImageState() {
    }

    public static boolean isClosed(NativeImage image) {
        try {
            return PIXELS.getLong(image) == 0L;
        } catch (IllegalAccessException failure) {
            throw new AssertionError("Cannot inspect NativeImage allocation", failure);
        }
    }

    private static Field pixelsField() {
        try {
            Field field = NativeImage.class.getDeclaredField("pixels");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
