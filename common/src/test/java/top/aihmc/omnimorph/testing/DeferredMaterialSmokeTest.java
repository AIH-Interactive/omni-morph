package top.aihmc.omnimorph.testing;

import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import top.aihmc.omnimorph.ducks.DeferredMaterialDrawExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class DeferredMaterialSmokeTest {
    public static void main(String[] args) {
        PreparedRenderType material = new PreparedRenderType(null, null, null, null, List.of());
        if (!((Object) material instanceof DeferredMaterialDrawExtension extension)) {
            throw new AssertionError("Production PreparedRenderType Mixin was not applied");
        }
        AtomicInteger draws = new AtomicInteger();
        extension.figura$setDeferredDraw(draws::incrementAndGet);
        material.drawFromBuffer((StagedVertexBuffer.ExecuteInfo) null);
        material.drawFromBuffer((StagedVertexBuffer.ExecuteInfo) null);
        if (draws.get() != 1) {
            throw new AssertionError("Native deferred material must execute exactly once");
        }
        extension.figura$setDeferredDraw(() -> {
            draws.incrementAndGet();
            throw new IllegalStateException("injected deferred draw failure");
        });
        try {
            material.drawFromBuffer((StagedVertexBuffer.ExecuteInfo) null);
            throw new AssertionError("Deferred draw exception was swallowed");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().equals("injected deferred draw failure")) {
                throw expected;
            }
        }
        material.drawFromBuffer((StagedVertexBuffer.ExecuteInfo) null);
        if (draws.get() != 2) {
            throw new AssertionError("Failed deferred draw must not replay or expose placeholder geometry");
        }
        System.out.println("PASS real PreparedRenderType + production Mixin: exactly once, failure propagation, no placeholder replay");
    }
}
