package top.aihmc.omm;

import java.util.List;

/** Common output of the original OMP YSM and Figura runtime adapters. */
interface OriginalScene extends AutoCloseable {
    List<Object> sample(int frame) throws Exception;

    List<int[]> triangleIndices() throws Exception;

    List<byte[]> textureBytes() throws Exception;

    @Override
    void close() throws Exception;
}
