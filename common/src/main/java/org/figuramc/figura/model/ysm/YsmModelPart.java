package org.figuramc.figura.model.ysm;

import org.figuramc.figura.lua.LuaWhitelist;
import org.figuramc.figura.lua.docs.LuaMethodDoc;
import org.figuramc.figura.lua.docs.LuaMethodOverload;
import org.figuramc.figura.lua.docs.LuaTypeDoc;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.utils.LuaUtils;

import java.util.ArrayList;
import java.util.List;

@LuaWhitelist
@LuaTypeDoc(name = "YsmModelPart", value = "ysm_model_part")
public class YsmModelPart {
    private final String name;
    private final YsmModelPart parent;
    private final List<YsmModelPart> children = new ArrayList<>();
    private final FiguraVec3 originPos;
    private final FiguraVec3 originRot;
    private final FiguraVec3 originScale = FiguraVec3.of(1, 1, 1);
    private FiguraVec3 pos = FiguraVec3.of();
    private FiguraVec3 rot = FiguraVec3.of();
    private FiguraVec3 scale = FiguraVec3.of(1, 1, 1);
    private FiguraVec3 animPos = FiguraVec3.of();
    private FiguraVec3 animRot = FiguraVec3.of();
    private FiguraVec3 animScale = FiguraVec3.of(1, 1, 1);
    private FiguraMat4 worldMatrix = FiguraMat4.of();
    private boolean visible = true;
    private boolean defaultVisible = true;

    public YsmModelPart(String name, YsmModelPart parent, float[] pivot, float[] rotation) {
        this.name = name;
        this.parent = parent;
        this.originPos = FiguraVec3.of(pivot[0], pivot[1], pivot[2]);
        this.originRot = FiguraVec3.of(rotation[0], rotation[1], rotation[2]);
        if (parent != null)
            parent.children.add(this);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model_part.get_name")
    public String getName() {
        return name;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model_part.get_parent")
    public YsmModelPart getParent() {
        return parent;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model_part.get_children")
    public List<YsmModelPart> getChildren() {
        return children;
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model_part.get_pos")
    public FiguraVec3 getPos() {
        return pos.copy();
    }

    @LuaWhitelist
    @LuaMethodDoc(overloads = {
            @LuaMethodOverload(argumentTypes = FiguraVec3.class, argumentNames = "pos"),
            @LuaMethodOverload(argumentTypes = {Double.class, Double.class, Double.class}, argumentNames = {"x", "y", "z"})
    }, aliases = "pos", value = "ysm_model_part.set_pos")
    public YsmModelPart setPos(Object x, Double y, Double z) {
        this.pos = LuaUtils.parseVec3("setPos", x, y, z);
        return this;
    }

    @LuaWhitelist
    public YsmModelPart pos(Object x, Double y, Double z) {
        return setPos(x, y, z);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model_part.get_rot")
    public FiguraVec3 getRot() {
        return rot.copy();
    }

    @LuaWhitelist
    @LuaMethodDoc(overloads = {
            @LuaMethodOverload(argumentTypes = FiguraVec3.class, argumentNames = "rot"),
            @LuaMethodOverload(argumentTypes = {Double.class, Double.class, Double.class}, argumentNames = {"x", "y", "z"})
    }, aliases = "rot", value = "ysm_model_part.set_rot")
    public YsmModelPart setRot(Object x, Double y, Double z) {
        this.rot = LuaUtils.parseVec3("setRot", x, y, z);
        return this;
    }

    @LuaWhitelist
    public YsmModelPart rot(Object x, Double y, Double z) {
        return setRot(x, y, z);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model_part.get_scale")
    public FiguraVec3 getScale() {
        return scale.copy();
    }

    @LuaWhitelist
    @LuaMethodDoc(overloads = {
            @LuaMethodOverload(argumentTypes = FiguraVec3.class, argumentNames = "scale"),
            @LuaMethodOverload(argumentTypes = {Double.class, Double.class, Double.class}, argumentNames = {"x", "y", "z"})
    }, aliases = "scale", value = "ysm_model_part.set_scale")
    public YsmModelPart setScale(Object x, Double y, Double z) {
        this.scale = LuaUtils.parseVec3("setScale", x, y, z);
        return this;
    }

    @LuaWhitelist
    public YsmModelPart scale(Object x, Double y, Double z) {
        return setScale(x, y, z);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model_part.get_visible")
    public boolean getVisible() {
        return visible;
    }

    @LuaWhitelist
    @LuaMethodDoc(overloads = @LuaMethodOverload(argumentTypes = Boolean.class, argumentNames = "visible"), aliases = "visible", value = "ysm_model_part.set_visible")
    public YsmModelPart setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public YsmModelPart setDefaultVisible(boolean visible) {
        this.defaultVisible = visible;
        this.visible = visible;
        return this;
    }

    @LuaWhitelist
    public YsmModelPart visible(boolean visible) {
        return setVisible(visible);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model_part.get_origin_pos")
    public FiguraVec3 getOriginPos() {
        return originPos.copy();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model_part.get_origin_rot")
    public FiguraVec3 getOriginRot() {
        return originRot.copy();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model_part.get_origin_scale")
    public FiguraVec3 getOriginScale() {
        return originScale.copy();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model_part.get_world_matrix")
    public FiguraMat4 getWorldMatrix() {
        return worldMatrix.copy();
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model_part.get_world_pos")
    public FiguraVec3 getWorldPos() {
        return worldMatrix.apply(0d, 0d, 0d);
    }

    @LuaWhitelist
    @LuaMethodDoc("ysm_model_part.reset_pose")
    public YsmModelPart resetPose() {
        this.pos = FiguraVec3.of();
        this.rot = FiguraVec3.of();
        this.scale = FiguraVec3.of(1, 1, 1);
        resetAnimPose();
        return this;
    }

    public void resetAnimPose() {
        animPos = FiguraVec3.of();
        animRot = FiguraVec3.of();
        animScale = FiguraVec3.of(1, 1, 1);
    }

    public void addAnimPos(double x, double y, double z) {
        animPos = animPos.copy().add(x, y, z);
    }

    public void addAnimRot(double x, double y, double z) {
        animRot = animRot.copy().add(x, y, z);
    }

    public void mulAnimScale(double x, double y, double z) {
        animScale = FiguraVec3.of(animScale.x * x, animScale.y * y, animScale.z * z);
    }

    public FiguraVec3 posRaw() {
        return pos;
    }

    public FiguraVec3 animPosRaw() {
        return animPos;
    }

    public FiguraVec3 rotRaw() {
        return rot;
    }

    public FiguraVec3 animRotRaw() {
        return animRot;
    }

    public FiguraVec3 scaleRaw() {
        return scale;
    }

    public FiguraVec3 animScaleRaw() {
        return animScale;
    }

    public boolean visibleRaw() {
        return visible;
    }

    public boolean defaultVisibleRaw() {
        return defaultVisible;
    }

    public void setWorldMatrix(FiguraMat4 worldMatrix) {
        this.worldMatrix = worldMatrix == null ? FiguraMat4.of() : worldMatrix.copy();
    }

    @Override
    public String toString() {
        return name + " (YSM Model Part)";
    }
}
