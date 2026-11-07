package top.aihmc.omnimorph.avatar.ysm.binary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory binary DTO; adapted from Sparkle-Morpher (bundled MIT license). */
public class YsmBinaryModel implements AutoCloseable {
    private final List<byte[]> ownedBuffers = new ArrayList<>();
    public String modelId;
    public int formatVersion;
    public RawMetadata metadata = new RawMetadata();
    public RawProperties properties = new RawProperties();
    public RawMainEntity mainEntity = new RawMainEntity();
    public Map<String, RawSubEntity> vehicles = new LinkedHashMap<>();
    public Map<String, RawSubEntity> projectiles = new LinkedHashMap<>();
    public Map<String, RawDataFile> soundFiles = new LinkedHashMap<>();
    public Map<String, RawDataFile> functionFiles = new LinkedHashMap<>();
    public Map<String, RawLanguageFile> languageFiles = new LinkedHashMap<>(); // locale -> key/value
    public RawFooter footer = new RawFooter();

    void ownBuffer(byte[] bytes) {
        ownedBuffers.add(bytes);
    }

    @Override
    public void close() {
        // Includes buffers read before their containing DTO was fully assembled.
        for (byte[] bytes : ownedBuffers) {
            clearBytes(bytes);
        }
        ownedBuffers.clear();
        clearGeometry(mainEntity.mainModel);
        clearGeometry(mainEntity.armModel);
        clearTextures(mainEntity.textures);
        mainEntity.animationFiles.clear();
        mainEntity.animationControllerFiles.clear();
        for (RawSubEntity entity : vehicles.values()) {
            clearEntity(entity);
        }
        for (RawSubEntity entity : projectiles.values()) {
            clearEntity(entity);
        }
        vehicles.clear();
        projectiles.clear();
        for (RawDataFile file : soundFiles.values()) {
            clearBytes(file.data);
        }
        for (RawDataFile file : functionFiles.values()) {
            clearBytes(file.data);
        }
        soundFiles.clear();
        functionFiles.clear();
        languageFiles.clear();
        for (RawMetadata.Author author : metadata.authors) {
            if (author.avatarImage != null) {
                clearBytes(author.avatarImage.data);
            }
            author.contacts.clear();
        }
        for (RawImage image : metadata.extraAvatars) {
            clearBytes(image.data);
        }
        for (RawImage image : properties.backgroundImages) {
            clearBytes(image.data);
        }
        metadata.authors.clear();
        metadata.extraAvatars.clear();
        metadata.links.clear();
        metadata.name = "";
        metadata.tips = "";
        properties.backgroundImages.clear();
        properties.extraAnimations.clear();
        properties.extraAnimationButtons.clear();
        properties.extraAnimationClassifies.clear();
        footer.rand = "";
        footer.extra = "";
    }

    private static void clearEntity(RawSubEntity entity) {
        clearGeometry(entity.model);
        clearTextures(entity.textures);
        entity.animationFiles.clear();
        entity.animationControllerFiles.clear();
        entity.matchIds = null;
    }

    private static void clearTextures(Map<String, RawTexture> textures) {
        for (RawTexture texture : textures.values()) {
            clearBytes(texture.data);
            for (RawTexture.SubTexture sub : texture.subTextures) {
                clearBytes(sub.data);
            }
            texture.subTextures.clear();
        }
        textures.clear();
    }

    private static void clearGeometry(RawGeometry geometry) {
        if (geometry == null) {
            return;
        }
        for (RawBone bone : geometry.bones) {
            Arrays.fill(bone.pivot, 0);
            Arrays.fill(bone.rotation, 0);
            for (RawCube cube : bone.cubes) {
                for (RawFace face : cube.faces) {
                    Arrays.fill(face.normal, 0);
                    Arrays.fill(face.u, 0);
                    Arrays.fill(face.v, 0);
                    for (float[] position : face.positions) {
                        Arrays.fill(position, 0);
                    }
                }
                cube.faces.clear();
            }
            bone.cubes.clear();
        }
        geometry.bones.clear();
    }

    private static void clearBytes(byte[] bytes) {
        if (bytes != null) {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public static class RawMainEntity {
        public RawGeometry mainModel;
        public RawGeometry armModel;
        public Map<String, RawTexture> textures = new LinkedHashMap<>();
        public Map<String, RawAnimationFile> animationFiles = new LinkedHashMap<>();
        public List<RawAnimationControllerFile> animationControllerFiles = new ArrayList<>();
    }

    public static class RawAnimationControllerFile {
        public String name;
        public String hash;
        public int legacyUnknownInt;
        public Map<String, RawAnimationController> controllers = new LinkedHashMap<>();
    }

    public static class RawSubEntity {
        public String identifier;
        public String[] matchIds;
        public RawGeometry model;
        public Map<String, RawTexture> textures = new LinkedHashMap<>();
        public Map<String, RawAnimationFile> animationFiles = new LinkedHashMap<>();
        public List<RawAnimationControllerFile> animationControllerFiles = new ArrayList<>();
    }

    public static class RawGeometry {
        public int modelType; // 1=main, 2=arm, 3=arrow
        public String identifier = "";
        public String sha256 = "";
        public float textureWidth = 64f;
        public float textureHeight = 64f;
        public float visibleBoundsWidth;
        public float visibleBoundsHeight;
        public float[] visibleBoundsOffset;
        public float unkFloat1, unkFloat2;

        // 這三個值在解析後直接讀取但未使用，且通常為0。
        // 作用暫時不明確
        public int footerPad1, footerPad2, footerPad3;

        public List<RawBone> bones = new ArrayList<>();
    }

    public static class RawBone {
        public String name;
        public String parentName;
        public float[] pivot = new float[3];
        public float[] rotation = new float[3];
        public int unkPad1, unkPad2, unkPad3, unkPad4, unkPad5;
        public List<RawCube> cubes = new ArrayList<>();
    }

    public static class RawCube {
        public List<RawFace> faces = new ArrayList<>();
        // 作用暫時不明確，且通常為0
        public int unkInt1, unkInt2, unkInt3;
    }

    public static class RawFace {
        public float[] normal = new float[3];
        public float[][] positions = new float[4][3]; //4 個頂點的 xyz 座標
        public float[] u = new float[4];
        public float[] v = new float[4];
    }

    public static class RawAnimationFile {
        // 1 - main -> animation-main
        // 2 - arm -> animation-arm
        // 3 - extra -> animation-extra
        // 4 - tac -> animation-tac
        // 5 - arrow -> animation-arrow
        // 6 - carryon -> animation-carryon
        // 7 - parcool -> animation-parcool
        // 8 - swem -> animation-swem
        // 9 - slashblade -> animation-slashblade
        // 10 - tlm -> animation-tlm
        // 11 - fp_arm -> animation-fp_arm
        // 12 - immersive_melodies -> animation-immersive_melodies
        // 13 - irons_spell_books -> animation-irons_spell_books
        public int animType;
        public String fileHash;
        public Map<String, RawAnimation> animations = new LinkedHashMap<>();
    }

    public static class RawAnimation {
        public String name;
        public float length;
        public int loopMode;
        // Float or String
        public Object blendWeight;

        // 作用暫時不明確，且通常為0
        public int unkInt1, unkInt2, unkInt4;

        public List<RawBoneAnimation> boneAnimations = new ArrayList<>();
        public List<RawTimelineEvent> timelineEvents = new ArrayList<>();
        public List<RawSoundEffect> soundEffects = new ArrayList<>();
    }

    public static class RawBoneAnimation {
        public String boneName;
        public List<RawKeyframe> rotation = new ArrayList<>();
        public List<RawKeyframe> position = new ArrayList<>();
        public List<RawKeyframe> scale = new ArrayList<>();
    }

    public static class RawKeyframe {
        public static final int INTERPOLATION_LINEAR = 0;
        public static final int INTERPOLATION_STEP = 1;
        public static final int INTERPOLATION_CATMULLROM = 2;
        public static final int INTERPOLATION_BEZIER = 3;

        public float timestamp;
        public int interpolationMode = INTERPOLATION_LINEAR;
        public Object[] postData = new Object[3];
        public Object[] preData = new Object[3];
        public boolean hasPreData;
        public float[] bezierLeftValue;
        public float[] bezierRightValue;
        public float[] bezierLeftTime;
        public float[] bezierRightTime;
        public boolean bezierLinked;
    }

    public static class RawTimelineEvent {
        public float timestamp;
        public List<String> events = new ArrayList<>();
    }

    public static class RawSoundEffect {
        public String effectName;
        public float timestamp;
    }

    public static class RawTexture {
        public String name;
        public String hash;
        public int width;
        public int height;
        public int imageFormat;
        public byte[] data;

        /**
         * 缺省值為1（表示正常紋理）
         * 在舊格式模型（≤15）中常出現0
         * 現代格式（≥26）幾乎全為1
         */
        public int unknownFlag;
        public List<SubTexture> subTextures = new ArrayList<>();

        public static class SubTexture {
            public String hash;
            // 1=法線貼圖，2=高光貼圖
            public int specularType;
            public int width;
            public int height;

            // 子紋理圖像格式
            public int imageFormat;
            public byte[] data;

            // 作用暫時不明確，且通常為1
            public int unknownFlag;
        }
    }

    public static class RawAnimationController {
        public String animationName;
        public String initialState;

        public List<RawControllerState> states = new ArrayList<>();
    }

    public static class RawControllerState {
        public String name;
        public Map<String, String> animations = new LinkedHashMap<>();
        public Map<String, String> transitions = new LinkedHashMap<>();
        public List<String> onEntry = new ArrayList<>();
        public List<String> onExit = new ArrayList<>();
        public List<String> soundEffects = new ArrayList<>();
        public float blendTransitionValue;
        public boolean blendViaShortestPath;
        public Map<Float, Float> blendTransitions = new LinkedHashMap<>();
    }

    public static class RawMetadata {
        public String name = "";
        public String tips = "";
        public String licenseType = "";
        public String licenseDescription = "";
        public List<Author> authors = new ArrayList<>();
        public Map<String, String> links = new LinkedHashMap<>();
        public List<RawImage> extraAvatars = new ArrayList<>();

        public static class Author {
            public String name = "";
            public String role = "";
            public String comment = "";
            public Map<String, String> contacts = new LinkedHashMap<>();
            public String avatar = "";
            public RawImage avatarImage = null;
        }
    }

    public static class RawImage {
        public String name;
        public byte[] data;
        public int width;
        public int height;
        public int format;

        // 作用暫時不明確，且通常為1
        public int unknownFlag;
    }

    public static class RawProperties {
        public String sha256 = "";
        public float widthScale = 0.7f;
        public float heightScale = 0.7f;
        public String defaultTexture = "default";
        public String previewAnimation = "";

        public boolean isFree = false;
        public boolean renderLayersFirst = false;
        public boolean allCutout = false;
        public boolean disablePreviewRotation = false;
        public boolean guiNoLighting = false;
        public boolean mergeMultilineExpr = false; // TODO:什么时候默认为true

        public String guiForeground = "";
        public String guiBackground = "";

        public List<RawImage> backgroundImages = new ArrayList<>();

        public Map<String, String> extraAnimations = new LinkedHashMap<>();
        public List<ExtraAnimationClassify> extraAnimationClassifies = new ArrayList<>();
        public List<ExtraAnimationButton> extraAnimationButtons = new ArrayList<>();
    }

    public static class ExtraAnimationClassify {
        public String id;
        public Map<String, String> extras = new LinkedHashMap<>();
    }

    public static class ExtraAnimationButton {
        public String id;
        public String name;
        public String description;
        public List<ConfigForm> forms = new ArrayList<>();
    }

    public static class ConfigForm {
        public String type; // checkbox, radio, range
        public String title;
        public String description;
        public String defaultValue;
        public float step, min, max;
        public Map<String, String> labels = new LinkedHashMap<>();
    }


    public static class RawDataFile {
        public String hash = "";
        public byte[] data;
        public RawDataFile() {}
        public RawDataFile(String hash, byte[] data) {
            this.hash = hash;
            this.data = data;
        }
    }

    public static class RawLanguageFile {
        public String hash = "";
        public Map<String, String> data = new LinkedHashMap<>();
        public RawLanguageFile() {}
        public RawLanguageFile(String hash, Map<String, String> data) {
            this.hash = hash;
            this.data = data;
        }
    }

    public static class RawFooter {
        public int version = 65535;
        public int unkInt1 = 1;// 通常为1
        public String rand = "";
        public long time = 0;
        public String extra = "";
        public int unkInt2 = 0; // format >= 24
    }
}
