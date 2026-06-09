package org.figuramc.figura.ducks;

import org.figuramc.figura.avatar.Avatar;

public interface FiguraSkullRenderStateExtension {
    void figura$setAvatar(Avatar avatar);
    Avatar figura$getAvatar();
}
