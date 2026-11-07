package top.aihmc.omm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shared explicit view and projection inputs for reproducible backend comparisons. */
record PreviewCamera(Vector3f eye, Vector3f target, float fovDegrees, float near, float far) {
    static PreviewCamera configured() throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(
            Path.of(System.getProperty("omm.preview.output"), "effective-config.json"))).getAsJsonObject();
        if (!root.has("camera")) {
            return null;
        }
        JsonObject input = root.getAsJsonObject("camera");
        Vector3f eye = vector(input, "eye");
        Vector3f target = vector(input, "target");
        float fov = input.get("fovDegrees").getAsFloat();
        float near = input.has("near") ? input.get("near").getAsFloat() : 0.01f;
        float far = input.has("far") ? input.get("far").getAsFloat() : 1000;
        if (!Float.isFinite(fov) || fov <= 0 || fov >= 180
            || !Float.isFinite(near) || !Float.isFinite(far) || near <= 0 || far <= near
            || new Vector3f(target).sub(eye).cross(0, 1, 0).lengthSquared() == 0) {
            throw new IllegalArgumentException("Invalid camera view or projection range");
        }
        System.out.println("CONFIGURED CAMERA eye=" + eye + " target=" + target + " fov=" + fov);
        return new PreviewCamera(eye, target, fov, near, far);
    }

    Matrix4f view() {
        return new Matrix4f().lookAt(eye, target, new Vector3f(0, 1, 0));
    }

    Matrix4f projection(float aspect) {
        return new Matrix4f().perspective((float) Math.toRadians(fovDegrees), aspect, near, far);
    }

    private static Vector3f vector(JsonObject input, String name) {
        var values = input.getAsJsonArray(name);
        if (values.size() != 3) {
            throw new IllegalArgumentException("camera." + name + " requires three coordinates");
        }
        var result = new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
        if (!result.isFinite()) {
            throw new IllegalArgumentException("camera." + name + " must be finite");
        }
        return result;
    }
}
