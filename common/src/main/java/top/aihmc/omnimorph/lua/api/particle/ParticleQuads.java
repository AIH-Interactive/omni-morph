package top.aihmc.omnimorph.lua.api.particle;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec2;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.math.vector.FiguraVec4;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Copied author vertices, in world-unit offsets from a custom particle's position. */
final class ParticleQuads {
    private static final Set<String> FIELDS = Set.of("position", "uv", "color");
    private final List<RibbonMesh.Vertex> vertices;
    private final AABB bounds;

    private ParticleQuads(List<RibbonMesh.Vertex> vertices, AABB bounds) {
        this.vertices = List.copyOf(vertices);
        this.bounds = bounds;
    }

    static ParticleQuads read(LuaTable declaration, int maximumVertices) {
        int count = CustomParticleLayer.arrayLength(declaration, maximumVertices);
        if (count % 4 != 0) {
            throw new LuaError("Particle quads require groups of four vertices");
        }
        var vertices = new ArrayList<RibbonMesh.Vertex>(count);
        AABB bounds = null;
        for (int index = 1; index <= count; index++) {
            var row = declaration.rawget(index).checktable();
            LuaValue key = LuaValue.NIL;
            while (true) {
                key = row.next(key).arg1();
                if (key.isnil()) {
                    break;
                }
                if (key.type() != LuaValue.TSTRING || !FIELDS.contains(key.tojstring())) {
                    throw new LuaError("Unknown particle quad vertex field");
                }
            }
            var position = (FiguraVec3) row.rawget("position").checkuserdata(FiguraVec3.class);
            var uv = (FiguraVec2) row.rawget("uv").checkuserdata(FiguraVec2.class);
            var tint = row.rawget("color");
            var color = tint.isnil() ? FiguraVec4.of(1, 1, 1, 1)
                    : (FiguraVec4) tint.checkuserdata(FiguraVec4.class);
            for (double component : new double[]{position.x, position.y, position.z}) {
                if (!Float.isFinite((float) component)) {
                    throw new LuaError("Particle quad positions require finite float components");
                }
            }
            for (double component : new double[]{uv.x, uv.y, color.x, color.y, color.z, color.w}) {
                if (!Double.isFinite(component) || component < 0 || component > 1) {
                    throw new LuaError("Particle quad UV and color components must be finite and between zero and one");
                }
            }
            var point = new Vec3(position.x, position.y, position.z);
            vertices.add(new RibbonMesh.Vertex(point, uv.x, uv.y,
                    (float) color.x, (float) color.y, (float) color.z, (float) color.w));
            var pointBounds = new AABB(point, point);
            bounds = bounds == null ? pointBounds : bounds.minmax(pointBounds);
        }
        return new ParticleQuads(vertices, bounds);
    }

    ParticleQuads transformed(FiguraMat4 position, FiguraMat3 texture) {
        Matrix4f geometry = position == null ? new Matrix4f() : position.toMatrix4f();
        Matrix3f uv = texture == null ? new Matrix3f() : texture.toMatrix3f();
        for (float component : geometry.get(new float[16])) {
            if (!Float.isFinite(component)) {
                throw new LuaError("Particle quad transform requires finite float components");
            }
        }
        for (float component : uv.get(new float[9])) {
            if (!Float.isFinite(component)) {
                throw new LuaError("Particle quad UV transform requires finite float components");
            }
        }
        if (geometry.m03() != 0 || geometry.m13() != 0 || geometry.m23() != 0 || geometry.m33() != 1
                || uv.m02() != 0 || uv.m12() != 0 || uv.m22() != 1) {
            throw new LuaError("Particle quad transforms must be affine");
        }
        var transformed = new ArrayList<RibbonMesh.Vertex>(vertices.size());
        AABB transformedBounds = null;
        for (var vertex : vertices) {
            var source = vertex.position();
            var point = geometry.transformPosition(new Vector3f((float) source.x,
                    (float) source.y, (float) source.z));
            var coordinate = uv.transform(new Vector3f((float) vertex.u(), (float) vertex.v(), 1));
            if (!Float.isFinite(point.x) || !Float.isFinite(point.y) || !Float.isFinite(point.z)) {
                throw new LuaError("Transformed particle positions require finite float components");
            }
            if (!Float.isFinite(coordinate.x) || !Float.isFinite(coordinate.y)
                    || coordinate.x < 0 || coordinate.x > 1 || coordinate.y < 0 || coordinate.y > 1) {
                throw new LuaError("Transformed particle UVs must remain between zero and one");
            }
            var offset = new Vec3(point.x, point.y, point.z);
            transformed.add(new RibbonMesh.Vertex(offset, coordinate.x, coordinate.y,
                    vertex.red(), vertex.green(), vertex.blue(), vertex.alpha()));
            var pointBounds = new AABB(offset, offset);
            transformedBounds = transformedBounds == null ? pointBounds : transformedBounds.minmax(pointBounds);
        }
        return new ParticleQuads(transformed, transformedBounds);
    }

    int vertexCount() {
        return vertices.size();
    }

    AABB bounds(Vec3 center) {
        return bounds.move(center);
    }

    RibbonMesh.Mesh at(Vec3 center, int layers) {
        var quads = new ArrayList<RibbonMesh.Quad>(vertices.size() / 4);
        for (int index = 0; index < vertices.size(); index += 4) {
            quads.add(new RibbonMesh.Quad(at(index, center), at(index + 1, center),
                    at(index + 2, center), at(index + 3, center)));
        }
        return new RibbonMesh.Mesh(quads, vertices.size() * layers, quads.size() * layers);
    }

    private RibbonMesh.Vertex at(int index, Vec3 center) {
        var vertex = vertices.get(index);
        return new RibbonMesh.Vertex(vertex.position().add(center), vertex.u(), vertex.v(),
                vertex.red(), vertex.green(), vertex.blue(), vertex.alpha());
    }
}
