package top.aihmc.omnimorph.ducks;

import top.aihmc.omnimorph.avatar.Avatar;

public interface CameraRenderStateExtension {
    void figura$setAvatar(Avatar avatar);

    Avatar figura$getAvatar();

    boolean figura$isRenderingNameTag();

    void figura$setRenderingNameTag(boolean rendering);
}
