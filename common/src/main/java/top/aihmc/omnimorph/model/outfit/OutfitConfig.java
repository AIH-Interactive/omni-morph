package top.aihmc.omnimorph.model.outfit;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable outfit JSON v1. UI snapshot integers are deliberately absent. */
public record OutfitConfig(int schemaVersion, long revision, ResourceRef base,
                           List<Instance> instances, List<VirtualAnchor> virtualAnchors,
                           Map<String, Integer> displayOrdinals, boolean showEquipment) {
    public static final int MAX_INSTANCES = 32;
    public static final int MAX_ANCHORS = 128;
    public static final int MAX_DEPTH = 64;
    public static final int MAX_JSON_BYTES = 65536;
    public static final Set<String> STANDARD_ANCHORS = Set.of(
            "head_anchor", "face_anchor", "hair_anchor", "back_anchor", "wing_anchor",
            "hip", "tailtip", "left_leg_anchor", "right_leg_anchor",
            "left_foot_anchor", "right_foot_anchor");

    public OutfitConfig {
        instances = List.copyOf(instances);
        virtualAnchors = List.copyOf(virtualAnchors);
        displayOrdinals = Map.copyOf(displayOrdinals);
        require(schemaVersion == 1 && revision >= 0);
        require(instances.size() <= MAX_INSTANCES && virtualAnchors.size() <= MAX_ANCHORS);
        require(displayOrdinals.size() <= 256);
        for (var entry : displayOrdinals.entrySet()) {
            text(entry.getKey(), 512, false);
            require(entry.getValue() > 0);
        }
        validateGraph(instances, virtualAnchors, displayOrdinals);
    }

    public static OutfitConfig empty() {
        return new OutfitConfig(1, 0, null, List.of(), List.of(), Map.of(), false);
    }

    public enum ResourceKind { AVATAR, ACCESSORY, OUTFIT }

    public enum Source { LOCAL, CLOUD }

    public enum Format { FIGURA, YSM_PLAIN, YSM_ENCRYPTED }

    public record ResourceRef(Source source, UUID localId, String origin, UUID resourceId,
                              Long revision, String contentHash, Format format) {
        public ResourceRef {
            Objects.requireNonNull(source);
            Objects.requireNonNull(format);
            hash(contentHash);
            if (source == Source.LOCAL) {
                require(localId != null && origin == null && resourceId == null && revision == null);
            } else {
                require(localId == null && resourceId != null && revision != null && revision > 0);
                URI uri = URI.create(Objects.requireNonNull(origin));
                require(Set.of("https", "http").contains(uri.getScheme()) && uri.getHost() != null);
                require(uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null);
                require(uri.getRawPath().isEmpty() && !origin.endsWith("/"));
                require(origin.equals(uri.getScheme().toLowerCase(java.util.Locale.ROOT)
                        + "://" + uri.getRawAuthority().toLowerCase(java.util.Locale.ROOT)));
                require(!((uri.getScheme().equals("http") && uri.getPort() == 80)
                        || (uri.getScheme().equals("https") && uri.getPort() == 443)));
            }
        }

        public String identity() {
            return source == Source.LOCAL ? "local:" + localId : origin + ":" + resourceId;
        }
    }

    public record Vector(double x, double y, double z) {
        public Vector {
            require(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z));
        }

        boolean within(double minimum, double maximum) {
            return x >= minimum && x <= maximum && y >= minimum && y <= maximum
                    && z >= minimum && z <= maximum;
        }
    }

    /** Model units (16/block), degrees, column vectors, T * Rx * Ry * Rz * S. */
    public record Transform(Vector position, Vector rotation, Vector scale) {
        public Transform {
            require(position.within(-4096, 4096) && rotation.within(-36000, 36000)
                    && scale.within(0.001, 64));
        }

        public static Transform identity() {
            return new Transform(new Vector(0, 0, 0), new Vector(0, 0, 0), new Vector(1, 1, 1));
        }

        public org.joml.Matrix4f modelMatrix() {
            return new org.joml.Matrix4f()
                    .translate((float) position.x(), (float) position.y(), (float) position.z())
                    .rotateXYZ((float) Math.toRadians(rotation.x()), (float) Math.toRadians(rotation.y()),
                            (float) Math.toRadians(rotation.z()))
                    .scale((float) scale.x(), (float) scale.y(), (float) scale.z());
        }
    }

    public record BoneSegment(String name, int siblingOrdinal) {
        public BoneSegment {
            text(name, 128, false);
            require(siblingOrdinal >= 0 && siblingOrdinal < 8192);
        }
    }

    public record BoneRef(UUID instanceId, List<BoneSegment> path, String skeletonHash) {
        public BoneRef {
            path = List.copyOf(path);
            require(!path.isEmpty() && path.size() <= MAX_DEPTH);
            hash(skeletonHash);
        }
    }

    public enum AnchorType { STANDARD, BASE_BONE, VIRTUAL, ACCESSORY_BONE, AUTO_TRAIL, UNRESOLVED }

    /** Tagged union: factories make ownership explicit; decoding checks the same invariant. */
    public record AnchorRef(AnchorType type, String name, BoneRef bone, UUID anchorId,
                            UUID preferredInstanceId, String symbol) {
        public AnchorRef {
            Objects.requireNonNull(type);
            require((name != null) == (type == AnchorType.STANDARD));
            require((bone != null) == (type == AnchorType.BASE_BONE || type == AnchorType.ACCESSORY_BONE));
            require((anchorId != null) == (type == AnchorType.VIRTUAL));
            require(preferredInstanceId == null || type == AnchorType.AUTO_TRAIL);
            require((symbol != null) == (type == AnchorType.UNRESOLVED));
            if (type == AnchorType.STANDARD) {
                require(STANDARD_ANCHORS.contains(name));
            } else if (type == AnchorType.BASE_BONE) {
                require(bone.instanceId() == null);
            } else if (type == AnchorType.ACCESSORY_BONE) {
                require(bone.instanceId() != null);
            } else if (type == AnchorType.UNRESOLVED) {
                text(symbol, 128, false);
            }
        }

        public static AnchorRef authorDefault(String symbol) {
            if (symbol == null || symbol.isBlank()) {
                return new AnchorRef(AnchorType.UNRESOLVED, null, null, null, null, "unspecified");
            }
            return STANDARD_ANCHORS.contains(symbol)
                    ? new AnchorRef(AnchorType.STANDARD, symbol, null, null, null, null)
                    : new AnchorRef(AnchorType.UNRESOLVED, null, null, null, null, symbol);
        }
    }

    public record VirtualAnchor(UUID id, String name, BoneRef parent, Transform transform) {
        public VirtualAnchor {
            Objects.requireNonNull(id);
            text(name, 128, false);
            Objects.requireNonNull(parent);
            Objects.requireNonNull(transform);
        }
    }

    public record BoneOverride(BoneRef bone, Transform transform) {
        public BoneOverride {
            Objects.requireNonNull(bone);
            Objects.requireNonNull(transform);
        }
    }

    public record Instance(UUID instanceId, ResourceRef resource, int displayOrdinal, boolean enabled,
                           AnchorRef anchor, Transform transform, List<BoneOverride> boneOverrides,
                           Map<String, Object> controls, Map<String, Object> luaState) {
        public Instance {
            Objects.requireNonNull(instanceId);
            Objects.requireNonNull(resource);
            Objects.requireNonNull(anchor);
            Objects.requireNonNull(transform);
            require(displayOrdinal > 0);
            boneOverrides = List.copyOf(boneOverrides);
            controls = scalarState(controls);
            luaState = scalarState(luaState);
            var paths = new HashSet<BoneRef>();
            for (BoneOverride override : boneOverrides) {
                require(instanceId.equals(override.bone().instanceId()) && paths.add(override.bone()));
            }
        }
    }

    private static Map<String, Object> scalarState(Map<String, Object> values) {
        require(values.size() <= 128);
        for (var entry : values.entrySet()) {
            text(entry.getKey(), 128, false);
            Object value = entry.getValue();
            if (value instanceof String string) {
                text(string, 512, true);
            } else if (value instanceof Number number) {
                require(Double.isFinite(number.doubleValue()));
            } else {
                require(value instanceof Boolean);
            }
        }
        return Map.copyOf(values);
    }

    private static void validateGraph(List<Instance> instances, List<VirtualAnchor> virtualAnchors,
                                      Map<String, Integer> watermarks) {
        var ids = new HashSet<UUID>();
        for (Instance instance : instances) {
            require(ids.add(instance.instanceId()));
        }
        var virtuals = new HashMap<UUID, VirtualAnchor>();
        for (VirtualAnchor anchor : virtualAnchors) {
            require(!ids.contains(anchor.id()) && virtuals.put(anchor.id(), anchor) == null);
            require(anchor.parent().instanceId() == null || ids.contains(anchor.parent().instanceId()));
        }
        var ordinals = new HashSet<String>();
        var edges = new HashMap<UUID, UUID>();
        int overrideCount = 0;
        for (Instance instance : instances) {
            String identity = instance.resource().identity();
            require(watermarks.getOrDefault(identity, 0) >= instance.displayOrdinal());
            require(ordinals.add(identity + "#" + instance.displayOrdinal()));
            overrideCount += instance.boneOverrides().size();
            require(overrideCount <= 1024);
            AnchorRef anchor = instance.anchor();
            UUID parent = switch (anchor.type()) {
                case ACCESSORY_BONE -> anchor.bone().instanceId();
                case VIRTUAL -> {
                    VirtualAnchor target = virtuals.get(anchor.anchorId());
                    require(target != null);
                    yield target.parent().instanceId();
                }
                case AUTO_TRAIL -> anchor.preferredInstanceId();
                default -> null;
            };
            if (parent != null) {
                require(ids.contains(parent) && !parent.equals(instance.instanceId()));
                edges.put(instance.instanceId(), parent);
            }
        }
        for (UUID id : ids) {
            var visited = new HashSet<UUID>();
            for (UUID next = id; next != null; next = edges.get(next)) {
                require(visited.add(next) && visited.size() <= MAX_DEPTH);
            }
        }
    }

    static void hash(String value) {
        require(value != null && value.matches("[0-9a-f]{64}"));
    }

    static void text(String value, int maximumBytes, boolean empty) {
        require(value != null && (empty || !value.isBlank()));
        require(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maximumBytes);
        require(value.codePoints().noneMatch(c -> c == 0 || c == '§'
                || (Character.isISOControl(c) && c != '\n' && c != '\t')));
    }

    static void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("INVALID_OUTFIT_CONFIGURATION");
        }
    }
}
