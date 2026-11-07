package top.aihmc.omm.preview;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.figuramc.figura.animation.Animation;
import org.figuramc.figura.animation.AnimationPlayer;
import org.figuramc.figura.animation.Keyframe;
import org.figuramc.figura.animation.TransformType;
import org.figuramc.figura.lua.LuaWhitelist;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.luaj.vm2.LuaTable;
import top.aihmc.omm.preview.data.PreviewTags.*;

/** Shared original-parser hierarchy and independently owned Lua model-part transformations. */
public final class PreviewFiguraParts {
    record Node(String name, String path, int parent, Vector3f pivot, Vector3f rotation, boolean mesh, boolean visible,
        Matrix4f bind, Matrix4f inverseBind) {}

    final List<Node> nodes = new ArrayList<>();
    final List<CompoundTag> modelRoots = new ArrayList<>();
    final List<CompoundTag> animations = new ArrayList<>();
    final Map<Integer, List<CompoundTag>> channels = new LinkedHashMap<>();

    State newState() {
        return new State();
    }

    final class State {
        final Part[] parts = new Part[nodes.size()];
        final LuaTable models = new LuaTable();
        private double lastAnimationTime;

        State() {
            for (int index = 0; index < parts.length; index++) {
                parts[index] = new Part(nodes.get(index));
                int parent = nodes.get(index).parent();
                if (parent >= 0) {
                    parts[parent].children.put(nodes.get(index).name(), parts[index]);
                }
            }
        }

        void install(org.figuramc.figura.lua.LuaTypeManager types, org.luaj.vm2.Globals globals) {
            types.generateMetatableFor(Part.class);
            for (int index = 0; index < parts.length; index++) {
                if (nodes.get(index).parent() < 0) {
                    models.set(nodes.get(index).name(), types.javaToLua(parts[index]).arg1());
                }
            }
            // OMP FiguraModelPart.getChildren exposes a fresh 1-based list of actual child objects.
            // Keep methods out of the root table so existing pairs(models) only enumerates model roots.
            var methods = new LuaTable();
            methods.set("getChildren", new org.luaj.vm2.lib.OneArgFunction() {
                @Override
                public org.luaj.vm2.LuaValue call(org.luaj.vm2.LuaValue self) {
                    var children = new LuaTable();
                    int child = 1;
                    for (int index = 0; index < parts.length; index++) {
                        if (nodes.get(index).parent() < 0) {
                            children.set(child++, types.javaToLua(parts[index]).arg1());
                        }
                    }
                    return children;
                }
            });
            methods.set("partToWorldMatrix", new org.luaj.vm2.lib.OneArgFunction() {
                @Override
                public org.luaj.vm2.LuaValue call(org.luaj.vm2.LuaValue self) {
                    // The virtual root has no pose; use the same model-unit conversion as real preview parts.
                    return types
                        .javaToLua(org.figuramc.figura.math.matrix.FiguraMat4.of().set(new Matrix4f().scaling(1f / 16)))
                        .arg1();
                }
            });
            var metatable = new LuaTable();
            metatable.set("__index", methods);
            models.setmetatable(metatable);
            globals.set("models", models);
        }

        void initializeAnimations(PreviewLua owner) {
            for (int id = 0; id < animations.size(); id++) {
                CompoundTag data = animations.get(id);
                for (String dynamic : List.of("mau", "mbw", "msd", "mld")) {
                    if (data.contains(dynamic)) {
                        throw new UnsupportedOperationException(
                            "Figura animation Molang property host not connected: " + dynamic);
                    }
                }
                var animation = new Animation(owner, data.string("mdl", ""), data.string("name", ""),
                    Animation.LoopMode.valueOf(data.string("loop", "once").toUpperCase(java.util.Locale.ROOT)),
                    data.number("ovr", 0) != 0, data.number("len", 0), data.number("off", 0), data.number("bld", 1),
                    data.number("sdel", 0), data.number("ldel", 0));
                if (data.contains("code")) {
                    for (Tag value : data.list("code")) {
                        CompoundTag code = (CompoundTag) value;
                        animation.newCode(code.number("time", 0), code.string("src", ""));
                    }
                }
                owner.animations.put(id, animation);
            }
            for (var entry : channels.entrySet()) {
                Part part = parts[entry.getKey()];
                for (CompoundTag attachment : entry.getValue()) {
                    Animation animation = owner.animations.get((int) attachment.number("id", -1));
                    if (animation == null) {
                        throw new IllegalArgumentException("Figura part references missing animation id");
                    }
                    for (var channel : attachment.compound("data").entries().entrySet()) {
                        TransformType type = switch (channel.getKey()) {
                            case "pos" -> TransformType.POSITION;
                            case "rot" -> TransformType.ROTATION;
                            case "grot" -> TransformType.GLOBAL_ROT;
                            case "scl" -> TransformType.SCALE;
                            default ->
                                throw new IllegalArgumentException(
                                    "Unknown Figura animation channel: " + channel.getKey());
                        };
                        var keyframes = new ArrayList<Keyframe>();
                        for (Tag value : (ListTag) channel.getValue()) {
                            keyframes.add(new Keyframe(owner, animation, (CompoundTag) value));
                        }
                        keyframes.sort(null);
                        animation.addAnimation(
                            part, new Animation.AnimationChannel(type, keyframes.toArray(Keyframe[] ::new)));
                    }
                }
            }
        }

        void animate(PreviewLua owner) {
            float target = owner.animationClock();
            try {
                do {
                    lastAnimationTime = Math.min(target, lastAnimationTime + 1.0 / 60);
                    owner.animationClock((float) lastAnimationTime);
                    owner.animations.values().forEach(AnimationPlayer::clear);
                    int remaining = 100000;
                    for (Animation animation : owner.animations.values()) {
                        remaining = AnimationPlayer.tick(animation, remaining);
                    }
                    if (remaining <= 0) {
                        throw new IllegalStateException("Figura animation channel budget exhausted");
                    }
                } while (lastAnimationTime < target);
            } finally {
                owner.animationClock(target);
            }
        }

        void update(Matrix4f[] world, Matrix4f[] palette) {
            update(world, palette, null, 0);
        }

        void update(Matrix4f[] world, Matrix4f[] palette, PreviewNativeVertices nativeVertices, int offset) {
            boolean[] visible = new boolean[parts.length];
            for (int index = 0; index < parts.length; index++) {
                Node node = nodes.get(index);
                Part part = parts[index];
                Matrix4f matrix = world[index];
                if (nativeVertices != null || node.parent() < 0) {
                    matrix.identity();
                } else {
                    matrix.set(world[node.parent()]);
                }
                matrix
                    .translate((part.position.x + part.animPosition.x) / 16,
                        (part.position.y + part.animPosition.y) / 16, (part.position.z + part.animPosition.z) / 16)
                    .translate(part.pivot);
                Vector3f rotation = new Vector3f(part.rotation).add(part.animRotation).mul((float) (Math.PI / 180));
                if (node.mesh()) {
                    matrix.rotateX(rotation.x).rotateY(rotation.y).rotateZ(rotation.z);
                } else {
                    matrix.rotateZ(rotation.z).rotateY(rotation.y).rotateX(rotation.x);
                }
                matrix.scale(new Vector3f(part.scale).mul(part.animScale))
                    .translate(-part.pivot.x, -part.pivot.y, -part.pivot.z);
                if (nativeVertices == null) {
                    palette[index].set(matrix).mul(node.inverseBind());
                    visible[index] = part.visible && (node.parent() < 0 || visible[node.parent()]);
                    if (!visible[index])
                        palette[index].scaling(0);
                } else {
                    visible[index] = part.visible;
                }
            }
            if (nativeVertices != null)
                nativeVertices.compose(offset, parts.length, world, palette, visible);
            for (int index = 0; index < parts.length; index++) {
                // Save the same pivot-relative matrix after either Java or native world composition.
                Part part = parts[index];
                part.savedPartToWorld.set(world[index]);
                part.savedPivot.set(part.pivot);
                part.savedPartToWorldNeedsConversion = true;
            }
        }
    }

    /** Scalar overloads match Figura's setPos/setRot/setScale/setPivot and hierarchy access. */
    @LuaWhitelist
    public static final class Part implements Comparable<Part> {
        private final String name;
        private final Map<String, Part> children = new LinkedHashMap<>();
        private final Vector3f position = new Vector3f();
        private final Vector3f rotation;
        private final Vector3f scale = new Vector3f(1);
        private final Vector3f pivot;
        private boolean visible;
        private final String path;
        private final Vector3f animPosition = new Vector3f();
        private final Vector3f animRotation = new Vector3f();
        private final Vector3f animScale = new Vector3f(1);
        private final Matrix4f savedPartToWorld = new Matrix4f().scaling(1f / 16);
        private final Vector3f savedPivot = new Vector3f();
        private boolean savedPartToWorldNeedsConversion;
        public int lastAnimationPriority = Integer.MIN_VALUE;
        public int animationOverride;
        public boolean animated;

        Part(Node node) {
            name = node.name();
            path = node.path();
            rotation = new Vector3f(node.rotation());
            pivot = new Vector3f(node.pivot());
            visible = node.visible();
        }

        public void animPosition(Vector3d value, boolean merge) {
            if (!merge)
                animPosition.zero();
            animPosition.add((float) -value.x, (float) value.y, (float) value.z);
        }

        public void animRotation(Vector3d value, boolean merge) {
            if (!merge)
                animRotation.zero();
            animRotation.add((float) -value.x, (float) -value.y, (float) value.z);
        }

        public void globalAnimRot(Vector3d value, boolean merge) {
            animRotation(value, merge);
        }

        public void animScale(Vector3d value, boolean merge) {
            if (!merge)
                animScale.set(1);
            animScale.mul((float) value.x, (float) value.y, (float) value.z);
        }

        @Override
        public int compareTo(Part other) {
            return path.compareTo(other.path);
        }

        @LuaWhitelist
        public Part setPos(Object x, Double y, Double z) {
            position.set(PreviewVectorArguments.parseVec3("setPos", x, y, z).asVec3f());
            return this;
        }

        @LuaWhitelist
        public Part setRot(Object x, Double y, Double z) {
            rotation.set(PreviewVectorArguments.parseVec3("setRot", x, y, z).asVec3f());
            return this;
        }

        @LuaWhitelist
        public Part setScale(Object x, Double y, Double z) {
            scale.set(PreviewVectorArguments.parseOneArgVec("setScale", x, y, z, 1).asVec3f());
            return this;
        }

        @LuaWhitelist
        public Part setPivot(Object x, Double y, Double z) {
            pivot.set(PreviewVectorArguments.parseVec3("setPivot", x, y, z).asVec3f()).div(16);
            return this;
        }

        @LuaWhitelist
        public Part setVisible(boolean value) {
            visible = value;
            return this;
        }

        @LuaWhitelist
        public boolean getVisible() {
            return visible;
        }

        @LuaWhitelist
        public String getName() {
            return name;
        }

        @LuaWhitelist
        public org.figuramc.figura.math.vector.FiguraVec3 getPos() {
            return org.figuramc.figura.math.vector.FiguraVec3.fromVec3f(position);
        }

        @LuaWhitelist
        public org.figuramc.figura.math.vector.FiguraVec3 getRot() {
            return org.figuramc.figura.math.vector.FiguraVec3.fromVec3f(rotation);
        }

        @LuaWhitelist
        public org.figuramc.figura.math.vector.FiguraVec3 getScale() {
            return org.figuramc.figura.math.vector.FiguraVec3.fromVec3f(scale);
        }

        @LuaWhitelist
        public org.figuramc.figura.math.vector.FiguraVec3 getPivot() {
            return org.figuramc.figura.math.vector.FiguraVec3.of(pivot.x * 16, pivot.y * 16, pivot.z * 16);
        }

        @LuaWhitelist
        public List<Part> getChildren() {
            return List.copyOf(children.values());
        }

        @LuaWhitelist
        public org.figuramc.figura.math.matrix.FiguraMat4 partToWorldMatrix() {
            if (savedPartToWorldNeedsConversion) {
                savedPartToWorld.translate(savedPivot).scale(1f / 16);
                savedPartToWorldNeedsConversion = false;
            }
            return org.figuramc.figura.math.matrix.FiguraMat4.of().set(savedPartToWorld);
        }

        @LuaWhitelist
        public Object __index(String key) {
            return children.get(key);
        }
    }
}
