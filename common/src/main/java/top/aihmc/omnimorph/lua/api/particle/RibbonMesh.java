package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.world.phys.Vec3;
import top.aihmc.omnimorph.model.outfit.OutfitLimits;
import top.aihmc.omnimorph.model.outfit.TrailBuffer;

import java.util.ArrayList;
import java.util.List;

/** World-space ribbon geometry. UV seams are split before allocation, including inside sprite cells. */
public final class RibbonMesh {
    private static final double MIN_LENGTH_SQUARED = 1e-24;

    public enum UvMode {
        REPEAT, STRETCH
    }

    public record Settings(double width, double length, double lifetime, double uvRepeat, double uvOffset,
                           boolean taper, boolean fade, UvMode uvMode) {
        public Settings {
            require(Double.isFinite(width) && width > 0 && Double.isFinite(length) && length > 0
                    && Double.isFinite(lifetime) && lifetime > 0 && Double.isFinite(uvRepeat) && uvRepeat > 0
                    && Double.isFinite(uvOffset) && uvMode != null, "INVALID_RIBBON_SETTINGS");
        }
    }

    public record Vertex(Vec3 position, double u, double v, float alpha) {
        public Vertex {
            finite(position);
            require(Double.isFinite(u) && u >= 0 && u <= 1 && Double.isFinite(v) && v >= 0 && v <= 1
                    && Float.isFinite(alpha) && alpha >= 0 && alpha <= 1, "INVALID_RIBBON_VERTEX");
        }
    }

    public record Quad(Vertex tailLeft, Vertex tailRight, Vertex headRight, Vertex headLeft) {
    }

    public record Mesh(List<Quad> quads, int submittedVertices, int submittedFaces) {
        public Mesh {
            quads = List.copyOf(quads);
        }
    }

    private record Node(Vec3 position, double time, double distance) {
        private Node between(Node next, double fraction) {
            return new Node(position.lerp(next.position, fraction), time + (next.time - time) * fraction,
                    distance + (next.distance - distance) * fraction);
        }
    }

    private record Section(Vec3 left, Vec3 right, float alpha, double u) {
        private Section between(Section next, double fraction, double atU) {
            return new Section(left.lerp(next.left, fraction), right.lerp(next.right, fraction),
                    (float) (alpha + (next.alpha - alpha) * fraction), atU);
        }
    }

    private RibbonMesh() {
    }

    public static Mesh build(List<TrailBuffer.PathSample> path, double now, Vec3 camera, Vec3 cameraRight,
                              Settings settings, OutfitLimits limits, int layers, int availableVertices) {
        return build(path, now, camera, cameraRight, settings, limits, layers, availableVertices, Integer.MAX_VALUE);
    }

    public static Mesh build(List<TrailBuffer.PathSample> path, double now, Vec3 camera, Vec3 cameraRight,
                              Settings settings, OutfitLimits limits, int layers, int availableVertices,
                              int availableFaces) {
        return build(path, now, camera, cameraRight, settings, limits, layers, availableVertices, availableFaces, 0);
    }

    public static Mesh build(List<TrailBuffer.PathSample> path, double now, Vec3 camera, Vec3 cameraRight,
                              Settings settings, OutfitLimits limits, int layers, int availableVertices,
                              int availableFaces, double rotationRadians) {
        require(path != null && settings != null && limits != null && Double.isFinite(now), "INVALID_RIBBON_INPUT");
        require(Double.isFinite(rotationRadians), "INVALID_RIBBON_ROTATION");
        require(availableFaces >= 0, "RIBBON_FACE_LIMIT");
        require(path.size() <= limits.maxTrailSamples() && layers >= 1 && layers <= limits.maxParticleLayers()
                && availableVertices >= 0 && availableVertices <= limits.maxRibbonVertices(), "RIBBON_VERTEX_LIMIT");
        finite(camera);
        finite(cameraRight);
        List<Node> nodes = clip(path, now, settings);
        if (nodes.size() < 2) {
            return new Mesh(List.of(), 0, 0);
        }
        double tail = nodes.getFirst().distance;
        double head = nodes.getLast().distance;
        if (head <= tail) {
            return new Mesh(List.of(), 0, 0);
        }
        double rate = settings.uvMode == UvMode.REPEAT ? settings.uvRepeat : 1 / (head - tail);
        double phase = settings.uvMode == UvMode.REPEAT ? head * rate + settings.uvOffset : settings.uvOffset;
        require(Double.isFinite(rate) && Double.isFinite(phase), "INVALID_RIBBON_UV");
        phase -= Math.floor(phase);
        double anchor = settings.uvMode == UvMode.REPEAT ? head : tail;
        double[] uv = new double[nodes.size()];
        long quadCount = 0;
        for (int index = 0; index < nodes.size(); index++) {
            uv[index] = (nodes.get(index).distance - anchor) * rate + phase;
            require(Double.isFinite(uv[index]), "INVALID_RIBBON_UV");
            if (index > 0) {
                double pieces = Math.max(1, Math.ceil(uv[index]) - Math.floor(uv[index - 1]));
                require(pieces <= availableVertices / (4L * layers), "RIBBON_VERTEX_LIMIT");
                quadCount += (long) pieces;
                require(quadCount <= availableVertices / (4L * layers), "RIBBON_VERTEX_LIMIT");
                require(quadCount <= availableFaces / (long) layers, "RIBBON_FACE_LIMIT");
            }
        }
        // No vertex/quad allocation occurs before the full, layered submission cost is accepted.
        var sections = new ArrayList<Section>(nodes.size());
        Vec3 previousSide = null;
        for (int index = 0; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            Vec3 before = nodes.get(Math.max(0, index - 1)).position;
            Vec3 after = nodes.get(Math.min(nodes.size() - 1, index + 1)).position;
            Vec3 tangent = after.subtract(before);
            if (tangent.lengthSqr() <= MIN_LENGTH_SQUARED) {
                tangent = index + 1 < nodes.size() ? after.subtract(node.position) : node.position.subtract(before);
            }
            tangent = unit(tangent);
            Vec3 side = tangent.cross(camera.subtract(node.position));
            if (side.lengthSqr() <= MIN_LENGTH_SQUARED) {
                side = cameraRight.subtract(tangent.scale(cameraRight.dot(tangent)));
            }
            if (side.lengthSqr() <= MIN_LENGTH_SQUARED) {
                Vec3 axis = Math.abs(tangent.x) < .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                side = tangent.cross(axis);
            }
            side = unit(side);
            if (previousSide != null && previousSide.dot(side) < 0) {
                side = side.scale(-1);
            }
            previousSide = side;
            if (rotationRadians != 0) {
                side = side.scale(Math.cos(rotationRadians)).add(tangent.cross(side).scale(Math.sin(rotationRadians)));
            }
            double taper = settings.taper ? (node.distance - tail) / (head - tail) : 1;
            Vec3 halfWidth = side.scale(settings.width * .5 * taper);
            float alpha = settings.fade ? (float) Math.clamp(1 - (now - node.time) / settings.lifetime, 0, 1) : 1;
            sections.add(new Section(node.position.add(halfWidth), node.position.subtract(halfWidth), alpha, uv[index]));
        }
        var quads = new ArrayList<Quad>((int) quadCount);
        for (int index = 1; index < sections.size(); index++) {
            Section start = sections.get(index - 1);
            Section end = sections.get(index);
            Section cursor = start;
            double seam = Math.floor(start.u) + 1;
            while (seam < end.u) {
                double fraction = (seam - start.u) / (end.u - start.u);
                Section boundary = start.between(end, fraction, seam);
                quads.add(quad(cursor, boundary, fraction(cursor.u), 1));
                cursor = boundary;
                seam++;
            }
            double endUv = end.u == Math.floor(end.u) && end.u > cursor.u ? 1 : fraction(end.u);
            quads.add(quad(cursor, end, fraction(cursor.u), endUv));
        }
        return new Mesh(quads, quads.size() * 4 * layers, quads.size() * layers);
    }

    private static Quad quad(Section tail, Section head, double u0, double u1) {
        return new Quad(new Vertex(tail.left, u0, 0, tail.alpha), new Vertex(tail.right, u0, 1, tail.alpha),
                new Vertex(head.right, u1, 1, head.alpha), new Vertex(head.left, u1, 0, head.alpha));
    }

    private static double fraction(double value) {
        return value - Math.floor(value);
    }

    private static List<Node> clip(List<TrailBuffer.PathSample> path, double now, Settings settings) {
        var source = new ArrayList<Node>(path.size());
        Node previous = null;
        for (var item : path) {
            require(item != null, "INVALID_RIBBON_PATH");
            var sample = item.sample();
            var node = new Node(new Vec3(sample.x(), sample.y(), sample.z()), sample.timeTicks(), item.distance());
            finite(node.position);
            require(Double.isFinite(node.time) && (previous == null
                    || node.time >= previous.time && node.distance >= previous.distance), "INVALID_RIBBON_PATH");
            source.add(node);
            previous = node;
        }
        if (source.size() < 2 || source.getFirst().time > now) {
            return List.of();
        }
        double head = source.getFirst().distance;
        for (int index = 1; index < source.size(); index++) {
            Node a = source.get(index - 1);
            Node b = source.get(index);
            if (b.time <= now) {
                head = b.distance;
            } else {
                head = a.between(b, (now - a.time) / (b.time - a.time)).distance;
                break;
            }
        }
        double oldestDistance = head - settings.length;
        double oldestTime = now - settings.lifetime;
        var clipped = new ArrayList<Node>(source.size() + 2);
        for (int index = 1; index < source.size(); index++) {
            Node a = source.get(index - 1);
            Node b = source.get(index);
            if (b.time < oldestTime || a.time > now || b.distance < oldestDistance) {
                continue;
            }
            double start = 0;
            double end = 1;
            if (b.time > a.time) {
                start = Math.max(start, (oldestTime - a.time) / (b.time - a.time));
                end = Math.min(end, (now - a.time) / (b.time - a.time));
            }
            if (b.distance > a.distance) {
                start = Math.max(start, (oldestDistance - a.distance) / (b.distance - a.distance));
            }
            if (start <= end && start <= 1 && end >= 0) {
                append(clipped, a.between(b, Math.max(0, start)));
                append(clipped, a.between(b, Math.min(1, end)));
            }
        }
        return clipped;
    }

    private static void append(List<Node> nodes, Node node) {
        if (!nodes.isEmpty() && nodes.getLast().position.distanceToSqr(node.position) <= MIN_LENGTH_SQUARED) {
            nodes.set(nodes.size() - 1, node);
        } else {
            nodes.add(node);
        }
    }

    private static Vec3 unit(Vec3 vector) {
        double squared = vector.lengthSqr();
        require(Double.isFinite(squared) && squared > MIN_LENGTH_SQUARED, "INVALID_RIBBON_DIRECTION");
        return vector.scale(1 / Math.sqrt(squared));
    }

    private static void finite(Vec3 vector) {
        require(vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y)
                && Double.isFinite(vector.z), "INVALID_RIBBON_POSITION");
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            throw new IllegalArgumentException(reason);
        }
    }
}
