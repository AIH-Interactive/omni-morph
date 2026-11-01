package top.aihmc.omnimorph.render.acceleration;

import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/** One isolated driver draw before activation, using the same mesh, pose, shader wrapper and draw code. */
final class GpuDriverProbe {
    static final String VERTEX = """
            #version 150
            in vec3 Position;
            in vec3 Normal;
            in vec2 UV0;
            in vec4 Color;
            in ivec2 UV1;
            in ivec2 UV2;
            uniform float ProbeScale;
            out vec4 probeColor;
            void main() {
                gl_Position = vec4(Position * ProbeScale, 1.0);
                probeColor = Color;
            }
            """;
    static final String FRAGMENT = """
            #version 150
            in vec4 probeColor;
            out vec4 fragColor;
            void main() { fragColor = probeColor; }
            """;

    private GpuDriverProbe() {}

    static int program(String vertexSource, String fragmentSource) {
        int vertex = compile(GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = 0;
        int program = 0;
        try {
            fragment = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource);
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
            }
            return program;
        } catch (RuntimeException failure) {
            GL20.glDeleteProgram(program);
            throw failure;
        } finally {
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String error = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(error);
        }
        return shader;
    }

    static void verify() {
        int drawTarget = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readTarget = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int renderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int[] capabilities = {GL11.GL_BLEND, GL11.GL_DEPTH_TEST, GL11.GL_CULL_FACE, GL11.GL_SCISSOR_TEST,
                GL30.GL_RASTERIZER_DISCARD, GL11.GL_STENCIL_TEST, GL11.GL_COLOR_LOGIC_OP,
                GL30.GL_FRAMEBUFFER_SRGB};
        ByteBuffer colorMask = org.lwjgl.BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
        int[] packParameters = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH,
                GL11.GL_PACK_SKIP_ROWS, GL11.GL_PACK_SKIP_PIXELS};
        int[] packValues = new int[packParameters.length];
        for (int index = 0; index < packParameters.length; index++) {
            packValues[index] = GL11.glGetInteger(packParameters[index]);
        }
        boolean[] enabled = new boolean[capabilities.length];
        int framebuffer = GL30.glGenFramebuffers();
        int color = GL30.glGenRenderbuffers();
        int program = 0;
        try (OpenGlAccelerationBackend backend = new OpenGlAccelerationBackend()) {
            for (int index = 0; index < capabilities.length; index++) {
                enabled[index] = GL11.glIsEnabled(capabilities[index]);
                GL11.glDisable(capabilities[index]);
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, color);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_RGBA8, 8, 8);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL30.GL_RENDERBUFFER, color);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("GPU probe framebuffer incomplete");
            }
            GL11.glViewport(0, 0, 8, 8);
            GL11.glColorMask(true, true, true, true);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            for (int index = 0; index < packParameters.length; index++) {
                GL11.glPixelStorei(packParameters[index], index == 0 ? 1 : 0);
            }
            program = program(VERTEX, FRAGMENT);
            GL20.glUseProgram(program);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "ProbeScale"), 1);
            DrawBatch batch = batch(new PoseSnapshot(FiguraMat4.of(), FiguraMat3.of()));
            if (!backend.drawActive(batch, program)) {
                throw new IllegalStateException("GPU driver rejected probe shader");
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer pixel = stack.malloc(4);
                GL11.glReadPixels(4, 4, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
                if ((pixel.get(0) & 255) < 250 || (pixel.get(1) & 255) != 0 || (pixel.get(2) & 255) != 0) {
                    throw new IllegalStateException("GPU driver probe pixel differs from expected red");
                }
            }
        } finally {
            GL20.glUseProgram(previousProgram);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            GL20.glDeleteProgram(program);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawTarget);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readTarget);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, renderbuffer);
            GL30.glDeleteRenderbuffers(color);
            GL30.glDeleteFramebuffers(framebuffer);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                    colorMask.get(2) != 0, colorMask.get(3) != 0);
            for (int index = 0; index < packParameters.length; index++) {
                GL11.glPixelStorei(packParameters[index], packValues[index]);
            }
            for (int index = 0; index < capabilities.length; index++) {
                if (enabled[index]) {
                    GL11.glEnable(capabilities[index]);
                }
            }
        }
    }

    static DrawBatch batch(PoseSnapshot pose) {
        double[] vertices = new double[4 * MeshHandle.STRIDE];
        float[] x = {-1, 1, 1, -1};
        float[] y = {-1, -1, 1, 1};
        for (int vertex = 0; vertex < 4; vertex++) {
            vertices[vertex * MeshHandle.STRIDE] = x[vertex];
            vertices[vertex * MeshHandle.STRIDE + 1] = y[vertex];
            vertices[vertex * MeshHandle.STRIDE + 3] = 1;
            vertices[vertex * MeshHandle.STRIDE + 6] = 1;
        }
        var lease = AccelerationService.INSTANCE.capture(new AccelerationOwner(), "driver-probe");
        var semantics = new DrawSemantics(null, null, DrawSemantics.Layer.PRIMARY, "driver-probe", 0, 1,
                0, 0, 1, 0, 0, 1, 0, true);
        return new DrawBatch(lease, new MeshHandle(vertices, 4), pose, semantics);
    }
}
