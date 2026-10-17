package top.aihmc.omnimorph.model.ysm;

public final class YsmBoneClassifier {
    private YsmBoneClassifier() {
    }

    public static boolean isNonBodyBranch(YsmGeometry.Bone bone) {
        return !YsmBoneMapper.isBodyVisibleByDefault(bone);
    }
}
