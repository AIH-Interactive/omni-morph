package top.aihmc.omm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Local failure-path regression; never included in production packaging or Actions. */
public final class ResourceCleanupProbe {
    public static void main(String[] args) throws Exception {
        var order = new ArrayList<String>();
        var renderFailure = new IOException("render");
        var meshFailure = new IOException("mesh");
        var textureFailure = new IllegalStateException("texture");
        try (var cleanup = new ResourceCleanup()) {
            cleanup.add(() -> {
                order.add("mesh");
                throw meshFailure;
            });
            cleanup.add(() -> {
                order.add("texture");
                throw textureFailure;
            });
            cleanup.add(() -> order.add("window"));
            cleanup.add(() -> order.add("terminate"));
            throw renderFailure;
        } catch (IOException failure) {
            if (failure != renderFailure || failure.getSuppressed().length != 1
                || failure.getSuppressed()[0] != meshFailure
                || meshFailure.getSuppressed().length != 1
                || meshFailure.getSuppressed()[0] != textureFailure) {
                throw new AssertionError("Original or cleanup failures were lost", failure);
            }
        }
        if (!order.equals(List.of("mesh", "texture", "window", "terminate"))) {
            throw new AssertionError("Cleanup stopped before window termination: " + order);
        }
        try (var cleanup = new ResourceCleanup()) {
            cleanup.add(() -> order.add("success"));
        }
        if (!order.getLast().equals("success")) {
            throw new AssertionError("Successful cleanup did not execute");
        }
        System.out.println("RESOURCE CLEANUP PASS order=" + order + " originalAndSuppressedPreserved=true");
    }
}
