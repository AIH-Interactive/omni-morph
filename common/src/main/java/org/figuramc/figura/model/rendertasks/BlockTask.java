package org.figuramc.figura.model.rendertasks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.api.world.BlockStateAPI;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.model.FiguraModelPart;
import org.figuramc.figura.utils.LuaUtils;

import java.util.ArrayList;
import java.util.List;

@LuaWhitelist
@LuaTypeDoc(
        name = "BlockTask",
        value = "block_task"
)
public class BlockTask extends RenderTask {

    private BlockState block;
    private int cachedComplexity;

    public BlockTask(String name, Avatar owner, FiguraModelPart parent) {
        super(name, owner, parent);
    }

    private final QuadInstance quadInstance = new QuadInstance();

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        poseStack.scale(16, 16, 16);

        int newLight = this.customization.light != null ? this.customization.light : light;
        int newOverlay = this.customization.overlay != null ? this.customization.overlay : overlay;

        quadInstance.setLightCoords(newLight);
        quadInstance.setOverlayCoords(newOverlay);
        quadInstance.setColor(-1);

        BlockStateModelSet modelSet = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        BlockStateModel model = modelSet.get(block);
        RandomSource random = RandomSource.create(42L);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);

        for (BlockStateModelPart part : parts) {
            RenderType renderType = part.getQuads(null).isEmpty() ? null :
                    part.getQuads(null).getFirst().materialInfo().itemRenderType();
            if (renderType == null) {
                for (Direction dir : Direction.values()) {
                    List<BakedQuad> quads = part.getQuads(dir);
                    if (!quads.isEmpty()) {
                        renderType = quads.getFirst().materialInfo().itemRenderType();
                        break;
                    }
                }
            }
            if (renderType == null) continue;

            VertexConsumer consumer = buffer.getBuffer(renderType);
            PoseStack.Pose pose = poseStack.last();

            for (BakedQuad quad : part.getQuads(null)) {
                consumer.putBakedQuad(pose, quad, quadInstance);
            }
            for (Direction dir : Direction.values()) {
                for (BakedQuad quad : part.getQuads(dir)) {
                    consumer.putBakedQuad(pose, quad, quadInstance);
                }
            }
        }
    }

    @Override
    public int getComplexity() {
        return cachedComplexity;
    }

    @Override
    public boolean shouldRender() {
        return super.shouldRender() && block != null && !block.isAir();
    }

    // -- lua -- // 


    @LuaWhitelist
    @LuaMethodDoc(
            overloads = {
                    @LuaMethodOverload(
                            argumentTypes = String.class,
                            argumentNames = "block"
                    ),
                    @LuaMethodOverload(
                            argumentTypes = BlockStateAPI.class,
                            argumentNames = "block"
                    )
            },
            aliases = "block",
            value = "block_task.set_block"
    )
    public BlockTask setBlock(Object block) {
        this.block = LuaUtils.parseBlockState("block", block);
        Minecraft client = Minecraft.getInstance();
        RandomSource random = client.level != null ? client.level.getRandom() : RandomSource.create();

        BlockStateModel blockModel = client.getModelManager().getBlockStateModelSet().get(this.block);
        List<BlockStateModelPart> parts = new ArrayList<>();
        blockModel.collectParts(random, parts);
        cachedComplexity = parts.stream().mapToInt(p -> p.getQuads(null).size()).sum();
        for (Direction dir : Direction.values()) {
            parts.clear();
            blockModel.collectParts(random, parts);
            cachedComplexity += parts.stream().mapToInt(p -> p.getQuads(dir).size()).sum();
        }

        return this;
    }

    @LuaWhitelist
    public BlockTask block(Object block) {
        return setBlock(block);
    }

    @Override
    public String toString() {
        return name + " (Block Render Task)";
    }
}
