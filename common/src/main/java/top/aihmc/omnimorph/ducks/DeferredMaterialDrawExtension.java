package top.aihmc.omnimorph.ducks;

/** A prepared native material may replace its placeholder draw with one deferred acceleration boundary. */
public interface DeferredMaterialDrawExtension {
    void figura$setDeferredDraw(Runnable draw);
}
