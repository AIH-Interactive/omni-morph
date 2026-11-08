package top.aihmc.omnimorph.render;

/** Preserved validity contract, independent of any numerical or graphics backend. */
public final class RenderLifetimeSmokeTest {
    public static void main(String[] args) {
        var lifetime = new RenderLifetime();
        var owner = new RenderOwner();
        var other = new RenderOwner();
        require(owner.generation() != other.generation(), "model generations are distinct");
        var pose = lifetime.capture(owner, "world");
        require(lifetime.isCurrent(pose), "current model/frame/resource lease is drawable");
        require(pose.view().equals("world"), "view metadata is retained");
        lifetime.beginFrame();
        require(!lifetime.isCurrent(pose), "a new frame rejects old work");
        pose = lifetime.capture(owner, "gui");
        lifetime.invalidateResources();
        require(!lifetime.isCurrent(pose), "resource reload rejects old work");
        pose = lifetime.capture(owner, "world");
        var independent = new RenderLifetime();
        var independentPose = independent.capture(other, "world");
        owner.retire();
        require(!lifetime.isCurrent(pose), "retirement rejects captured work");
        require(!lifetime.isCurrent(lifetime.capture(owner, "world")), "retired owners never resurrect");
        require(independent.isCurrent(independentPose), "unrelated lifetimes remain current");
        System.out.println("PASS ordinary render lifetime: owner, frame, resource, view and isolation");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
