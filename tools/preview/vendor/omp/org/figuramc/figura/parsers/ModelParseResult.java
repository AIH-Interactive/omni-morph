package org.figuramc.figura.parsers;

import top.aihmc.omm.preview.data.PreviewTags.CompoundTag;

import java.util.List;

//dummy class containing the return object of the parser
public record ModelParseResult(CompoundTag textures, List<CompoundTag> animationList, CompoundTag modelNbt) {
}
