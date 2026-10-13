package org.figuramc.figura.avatar.ysm.binary;

import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeBudget;
import org.figuramc.figura.avatar.ysm.crypto.YsmDecodeException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read-only format-dispatched parser, adapted from the fixed Sparkle-Morpher snapshot. */
public final class YsmBinaryReader {
    private final YsmBinaryInput reader;
    private final int format;
    private final YsmBinaryModel model = new YsmBinaryModel();

    private YsmBinaryReader(byte[] data, Integer externalFormat, YsmDecodeBudget budget) throws IOException {
        reader = new YsmBinaryInput(data, budget, model::ownBuffer);
        format = externalFormat == null ? reader.readDword() : externalFormat;
        if (format < 1 || format > 32) {
            throw new YsmDecodeException(YsmDecodeException.Code.UNSUPPORTED_VERSION, "binary/format", 0);
        }
        model.formatVersion = format;
    }

    public static YsmBinaryModel readFilePayload(byte[] data, YsmDecodeBudget budget) throws IOException {
        return new YsmBinaryReader(data, null, budget).read();
    }

    /** Explicit non-file layout; does not imply support for another client's cache protocol. */
    public static YsmBinaryModel readWithoutFormatDword(byte[] data, int format, YsmDecodeBudget budget) throws IOException {
        return new YsmBinaryReader(data, format, budget).read();
    }

    private YsmBinaryModel read() throws IOException {
        try {
            if (format < 4) {
                deserializeLegacyV1();
            } else if (format <= 15) {
                deserializeLegacyV15();
            } else {
                deserializeModern();
            }
            if (reader.remaining() > 0 && format >= 9) {
                readFooter();
            }
            if (reader.remaining() != 0) {
                throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "binary/trailing-data", reader.getOffset());
            }
            return model;
        } catch (IOException e) {
            model.close();
            throw e;
        } catch (RuntimeException e) {
            model.close();
            throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "binary/structure", reader.getOffset());
        }
    }

    private void readFooter() throws IOException {
        model.footer.version = format > 26 ? reader.readVarInt() : format;
        model.footer.unkInt1 = reader.readVarInt();
        model.footer.rand = reader.readString();
        model.footer.time = reader.readVarLong();
        model.footer.extra = reader.readString();
        if (format >= 24) {
            model.footer.unkInt2 = reader.readVarInt();
        }
    }

    private YsmBinaryModel.RawSubEntity arrow() {
        return model.projectiles.computeIfAbsent("minecraft:arrow", key -> {
            var sub = new YsmBinaryModel.RawSubEntity();
            sub.identifier = key;
            sub.matchIds = new String[]{key};
            return sub;
        });
    }

    private static String animationKey(int type) {
        return switch (type) {
            case 1 -> "main";
            case 2 -> "arm";
            case 3 -> "extra";
            case 4 -> "tac";
            case 5 -> "arrow";
            case 6 -> "carryon";
            case 7 -> "parcool";
            case 8 -> "swem";
            case 9 -> "slashblade";
            case 10 -> "tlm";
            case 11 -> "fp_arm";
            case 12 -> "immersive_melodies";
            case 13 -> "irons_spell_books";
            default -> "type_" + type;
        };
    }
    private void deserializeLegacyV1() throws IOException {
        int unknownNeedSkipBytes = reader.readVarInt();
        reader.skipBytes(unknownNeedSkipBytes);

        List<YsmBinaryModel.RawGeometry> tempModels = new ArrayList<>();
        int modelCount = reader.readCount(4096);
        for (int i = 0; i < modelCount; ++i) {
            int modelId = reader.readVarInt(); // 棄用或僅作為內部ID
            int unknownMustBeOneFlag = reader.readVarInt();
            if (unknownMustBeOneFlag != 1) throw new RuntimeException("Expected 1");
            YsmBinaryModel.RawGeometry rawGeometry = parseModels();
            rawGeometry.modelType = modelId;
            tempModels.add(rawGeometry);
        }
        assignMainModels(tempModels);

        Map<Integer, YsmBinaryModel.RawAnimationFile> tempAnims = new HashMap<>();
        int animationBlobCount = reader.readCount(4096);
        for (int i = 0; i < animationBlobCount; ++i) {
            int animationId = reader.readVarInt();
            int unknownPadding = reader.readVarInt();
            if (unknownPadding != 1) throw new RuntimeException("Expected 1");
            YsmBinaryModel.RawAnimationFile rawAnimationFile = parseAnimations();

            String animKey = animationKey(animationId);
            if (animationId == 5) {
                YsmBinaryModel.RawSubEntity arrowEntity = model.projectiles.computeIfAbsent("minecraft:arrow", k -> {
                    YsmBinaryModel.RawSubEntity newSub = new YsmBinaryModel.RawSubEntity();
                    newSub.identifier = k;
                    return newSub;
                });
                arrowEntity.animationFiles.put(animKey, rawAnimationFile);
            } else {
                putUnique(model.mainEntity.animationFiles, animKey, rawAnimationFile);
            }

            rawAnimationFile.animType = animationId;
            tempAnims.put(animationId, rawAnimationFile);
        }

        List<YsmBinaryModel.RawTexture> tempTextures = new ArrayList<>();
        int customTextureCount = reader.readCount(4096);
        for (int i = 0; i < customTextureCount; ++i) {
            YsmBinaryModel.RawTexture tex = new YsmBinaryModel.RawTexture();
            tex.name = reader.readString();
            if (format < 4) {
                int unknownFormatFlag = reader.readVarInt();
                if (unknownFormatFlag != 0x01) throw new RuntimeException("Expected 0x01");
            }
            tex.data = reader.readByteArray();
            tex.width = reader.readVarInt();
            tex.height = reader.readVarInt();
            reader.checkImageDimensions(tex.width, tex.height);
            tex.imageFormat = -1; // RGBA


            if ("arrow.png".equals(tex.name))
                arrow().textures.put(tex.name, tex);
            else
                putUnique(model.mainEntity.textures, tex.name, tex);

            tempTextures.add(tex);
        }

        // Tables回填Hash
        int modelTableSize = reader.readCount(4096);
        for (int i = 0; i < modelTableSize; ++i) {
            int modelId = reader.readVarInt();
            String modelHash = reader.readString();
            for (YsmBinaryModel.RawGeometry tempModel : tempModels) {
                if (tempModel.modelType == modelId)
                    tempModel.sha256 = modelHash;
            }
        }

        int animationTableSize = reader.readCount(4096);
        for (int i = 0; i < animationTableSize; ++i) {
            int animationId = reader.readVarInt();
            tempAnims.get(animationId).fileHash = reader.readString();
        }

        int textureTableSize = reader.readCount(4096);
        for (int i = 0; i < textureTableSize; ++i) {
            String textureName = reader.readString();
            String textureHash = reader.readString();
            YsmBinaryModel.RawTexture tex = model.mainEntity.textures.get(textureName);
            if (tex != null) tex.hash = textureHash;
        }

        String unkString = reader.readString();
        model.properties.sha256 = unkString;
    }

    private void deserializeLegacyV15() throws IOException {
        int unknownNeedSkipBytes = reader.readVarInt();
        reader.skipBytes(unknownNeedSkipBytes);

        List<YsmBinaryModel.RawGeometry> tempModels = new ArrayList<>();
        int modelCount = reader.readCount(4096);
        for (int i = 0; i < modelCount; ++i) {
            int modelId = reader.readVarInt();
            int unknownPadding = reader.readVarInt();
            if (unknownPadding != 1) throw new RuntimeException("Expected 1");
            YsmBinaryModel.RawGeometry rawGeometry = parseModels();
            rawGeometry.modelType = modelId;
            tempModels.add(rawGeometry);
        }

        assignMainModels(tempModels); //TODO

        Map<Integer, YsmBinaryModel.RawAnimationFile> tempAnims = new HashMap<>();
        int animationBlobCount = reader.readCount(4096);
        for (int i = 0; i < animationBlobCount; ++i) {
            int animationId = reader.readVarInt();
            int unknownPadding = reader.readVarInt();
            if (unknownPadding != 1) throw new RuntimeException("Expected 1");
            YsmBinaryModel.RawAnimationFile rawAnimationFile = parseAnimations();

            String animKey = animationKey(animationId);
            if (animationId == 5) {
                YsmBinaryModel.RawSubEntity arrowEntity = model.projectiles.computeIfAbsent("minecraft:arrow", k -> {
                    YsmBinaryModel.RawSubEntity newSub = new YsmBinaryModel.RawSubEntity();
                    newSub.identifier = k;
                    return newSub;
                });
                arrowEntity.animationFiles.put(animKey, rawAnimationFile);
            } else {
                putUnique(model.mainEntity.animationFiles, animKey, rawAnimationFile);
            }

            rawAnimationFile.animType = animationId;
            tempAnims.put(animationId, rawAnimationFile);
        }

        if (format > 9) {
            parseAnimationControllers(model.mainEntity.animationControllerFiles, false);
            int animationControllerTableSize = reader.readCount(4096);
            for (int i = 0; i < animationControllerTableSize; ++i) {
                String controllerName = reader.readString();
                String controllerHash = reader.readString();
                for (YsmBinaryModel.RawAnimationControllerFile file : model.mainEntity.animationControllerFiles) {
                    if (controllerName.equals(file.name)) {
                        file.hash = controllerHash;
                        break;
                    }
                }
            }
        }

        int customTextureCount = reader.readCount(4096);
        for (int i = 0; i < customTextureCount; ++i) {
            YsmBinaryModel.RawTexture tex = new YsmBinaryModel.RawTexture();
            tex.name = reader.readString();
            tex.data = reader.readByteArray();
            tex.width = reader.readVarInt();
            tex.height = reader.readVarInt();
            reader.checkImageDimensions(tex.width, tex.height);
            tex.imageFormat = -1; // RGBA

            int subTextureSize = reader.readCount(4096);
            for (int j = 0; j < subTextureSize; ++j) {
                YsmBinaryModel.RawTexture.SubTexture sub = new YsmBinaryModel.RawTexture.SubTexture();
                sub.specularType = reader.readVarInt();
                sub.data = reader.readByteArray();
                sub.width = reader.readVarInt();
                sub.height = reader.readVarInt();
                reader.checkImageDimensions(sub.width, sub.height);
                sub.imageFormat = -1; // RGBA
                tex.subTextures.add(sub);
            }

            // 特殊處理一下
            if ("/ARROW\\".equals(tex.name))
                arrow().textures.put(tex.name, tex);
            else
                putUnique(model.mainEntity.textures, tex.name, tex);

        }

        if (format > 9) {
            parseSoundFiles();
            int soundTableCount = reader.readCount(4096);
            for (int i = 0; i < soundTableCount; ++i) {
                String soundName = reader.readString();
                String soundHash = reader.readString();
                YsmBinaryModel.RawDataFile sf = model.soundFiles.get(soundName);
                if (sf != null) sf.hash = soundHash;
            }
        }

        List<YsmBinaryModel.RawImage> tempAvatars = new ArrayList<>();
        int extraTextureCount = reader.readCount(4096);
        for (int i = 0; i < extraTextureCount; ++i) {
            YsmBinaryModel.RawImage avatar = new YsmBinaryModel.RawImage();
            avatar.name = reader.readString();
            avatar.data = reader.readByteArray();
            avatar.width = reader.readVarInt();
            avatar.height = reader.readVarInt();
            reader.checkImageDimensions(avatar.width, avatar.height);
            avatar.format = -1; // 兼容旧版默认 RGBA
            tempAvatars.add(avatar);
        }

        // Tables回填Hash
        int modelTableSize = reader.readCount(4096);
        for (int i = 0; i < modelTableSize; ++i) {
            int modelId = reader.readVarInt();
            String modelHash = reader.readString();
            for (YsmBinaryModel.RawGeometry tempModel : tempModels) {
                if (tempModel.modelType == modelId)
                    tempModel.sha256 = modelHash;
            }
        }

        int animationTableSize = reader.readCount(4096);
        for (int i = 0; i < animationTableSize; ++i) {
            int animationId = reader.readVarInt();
            String animationHash = reader.readString();
//            if (tempAnims.get(animationId) == null) {
//                "".hashCode();
//
//            }
            tempAnims.get(animationId).fileHash = animationHash;
        }

        int textureTableSize = reader.readCount(4096);
        for (int i = 0; i < textureTableSize; ++i) {
            String textureName = reader.readString();
            String textureHash = reader.readString();
            YsmBinaryModel.RawTexture tex = model.mainEntity.textures.get(textureName);
            if (tex != null) {
                tex.hash = textureHash;
            }
            int subTextureSize = reader.readCount(4096);
            for (int j = 0; j < subTextureSize; ++j) {
                int specularType = reader.readVarInt();
                String specialImageHash = reader.readString();
                // 修復舊版丟棄子紋理Hash
                if (tex != null) {
                    for (YsmBinaryModel.RawTexture.SubTexture sub : tex.subTextures) {
                        if (sub.specularType == specularType) {
                            sub.hash = specialImageHash;
                            break;
                        }
                    }
                }
            }
        }
        for (YsmBinaryModel.RawSubEntity value : model.projectiles.values()) {
            for (YsmBinaryModel.RawTexture rawTexture : value.textures.values()) {
                model.mainEntity.textures.remove(rawTexture.name);
            }
        }

        parseYSMJson();

        for (int i = 0; i < tempAvatars.size(); i++) {
            YsmBinaryModel.RawImage avatar = tempAvatars.get(i);
            if (i < model.metadata.authors.size()) {
                model.metadata.authors.get(i).avatar = avatar.name;
                model.metadata.authors.get(i).avatarImage = avatar;
            } else {
                model.metadata.extraAvatars.add(avatar);
            }
        }
    }

    private void deserializeModern() throws IOException {
        parseSoundFiles();
        parseFunctionFiles();
        parseLanguageFiles();

        if (format < 26) {
            int subEntityTotalCount = reader.readCount(4096);
            for (int i = 0; i < subEntityTotalCount; ++i) {
                parseSubEntity(model.vehicles, "SubEntity", i);
            }
            int footerFlag = reader.readVarInt(); // always 00
        } else {
            int vehiclesTotalCount = reader.readCount(4096);
            for (int i = 0; i < vehiclesTotalCount; ++i) {
                parseSubEntity(model.vehicles, "Vehicle", i);
            }

            int projectilesTotalCount = reader.readCount(4096);
            for (int i = 0; i < projectilesTotalCount; ++i) {
                parseSubEntity(model.projectiles, "Projectile", i);
            }
        }

        int unknownEntityFlag = reader.readVarInt();
        if (unknownEntityFlag != 1) {
            throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "binary/entity-flag", reader.getOffset());
        }

        int animationCount = reader.readCount(4096);
        for (int i = 0; i < animationCount; ++i) {
            int type = reader.readVarInt();
            String hash = reader.readString();

            YsmBinaryModel.RawAnimationFile animRef = parseAnimations();
            putUnique(model.mainEntity.animationFiles,
                    animationKey(type),
                    animRef
            );
            animRef.animType = type;
            animRef.fileHash = hash;
        }

        parseAnimationControllers(model.mainEntity.animationControllerFiles,true);

        parseTextureFiles(model.mainEntity.textures);

        int modelTotalCount = reader.readCount(4096);
        List<YsmBinaryModel.RawGeometry> tempMainModels = new ArrayList<>();
        for (int i = 0; i < modelTotalCount; ++i) {
            int modelType = reader.readVarInt();
            String hash = reader.readString();

            YsmBinaryModel.RawGeometry geoRef = parseModels();
            geoRef.sha256 = hash;
            geoRef.modelType = modelType;
            tempMainModels.add(geoRef);
        }
        assignMainModels(tempMainModels);

        parseYSMJson();
    }

    private void parseSubEntity(Map<String, YsmBinaryModel.RawSubEntity> targetMap, String categoryName, int index) throws IOException {
        YsmBinaryModel.RawSubEntity subEntity = new YsmBinaryModel.RawSubEntity();
        String subModuleName = "";
        if (format <= 26) {
            subModuleName = reader.readString();
            subEntity.identifier = subModuleName;
        } else {
            subEntity.identifier = categoryName + "_" + index; // >=26沒有Header Name
        }
        int animationCount = reader.readCount(4096);
        for (int i = 0; i < animationCount; ++i) {
            String hash = reader.readString();
            YsmBinaryModel.RawAnimationFile rawAnimationFile = parseAnimations();
            putUnique(subEntity.animationFiles, categoryName + "_" + i, rawAnimationFile);
            rawAnimationFile.fileHash = hash;
        }

        parseAnimationControllers(subEntity.animationControllerFiles, false);

        YsmBinaryModel.RawTexture baseTex = new YsmBinaryModel.RawTexture();
        SpecialImageResult imgRes = parseSpecialImage();
        baseTex.hash = imgRes.hash;
        baseTex.data = imgRes.data;
        baseTex.width = reader.readVarInt();
        baseTex.height = reader.readVarInt();
        reader.checkImageDimensions(baseTex.width, baseTex.height);
        baseTex.imageFormat = reader.readVarInt();
        baseTex.unknownFlag = reader.readVarInt();
        baseTex.name = "base_texture_" + index;
        subEntity.textures.put(baseTex.name, baseTex);

        // Sub Textures
        int subTextureSize = reader.readCount(4096);
        for (int i = 0; i < subTextureSize; ++i) {
            YsmBinaryModel.RawTexture.SubTexture subTex = new YsmBinaryModel.RawTexture.SubTexture();
            subTex.specularType = reader.readVarInt();
            SpecialImageResult specRes = parseSpecialImage();
            subTex.hash = specRes.hash;
            subTex.data = specRes.data;
            subTex.width = reader.readVarInt();
            subTex.height = reader.readVarInt();
            reader.checkImageDimensions(subTex.width, subTex.height);
            subTex.imageFormat = reader.readVarInt();
            subTex.unknownFlag = reader.readVarInt();
            baseTex.subTextures.add(subTex);
        }

        String modelHash = reader.readString();
        subEntity.model = parseModels();
        subEntity.model.sha256 = modelHash;


        if (format > 26) {
            int identifierCount = reader.readCount(128);
            if (identifierCount == 0) {
                throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "binary/subentity-identifiers", reader.getOffset());
            }

            List<String> identifiers = new ArrayList<>();
            for (int i = 0; i < identifierCount; i++) {
                identifiers.add(reader.readString());
            }

            subEntity.identifier = identifiers.get(0);
            subEntity.matchIds = identifiers.toArray(new String[0]);
            putUnique(targetMap, subEntity.identifier, subEntity);
        } else {
            putUnique(targetMap, subEntity.identifier, subEntity);
        }
    }

    private YsmBinaryModel.RawGeometry parseModels() throws IOException {
        YsmBinaryModel.RawGeometry geo = new YsmBinaryModel.RawGeometry();

        int boneCount = reader.readGeometryBonesCount();
        for (int i = 0; i < boneCount; i++) {
            YsmBinaryModel.RawBone bone = new YsmBinaryModel.RawBone();
            bone.parentName = reader.readString();
            int cubeCount = reader.readCount(4096);

            for (int j = 0; j < cubeCount; j++) {
                YsmBinaryModel.RawCube cube = new YsmBinaryModel.RawCube();
                int faceCount = reader.readGeometryFacesCount();
                for (int k = 0; k < faceCount; k++) {
                    YsmBinaryModel.RawFace face = new YsmBinaryModel.RawFace();
                    face.normal = readVector3D(); // normal
                    for (int v = 0; v < 4; v++) {
                        face.positions[v] = readVector3D(); // position
                        face.u[v] = reader.readFloat(); // u
                        face.v[v] = reader.readFloat(); // v
                    }
                    cube.faces.add(face);
                }
                cube.unkInt1 = reader.readVarInt();
                cube.unkInt2 = reader.readVarInt();
                cube.unkInt3 = reader.readVarInt();
                bone.cubes.add(cube);
            }

            bone.name = reader.readString();
            bone.unkPad1 = reader.readVarInt();
            bone.unkPad2 = reader.readVarInt();
            bone.unkPad3 = reader.readVarInt();
            bone.unkPad4 = reader.readVarInt();
            bone.unkPad5 = reader.readVarInt();

            bone.pivot = readVector3D();
            bone.rotation = readVector3D();
            geo.bones.add(bone);
        }


        geo.identifier = reader.readString();
        geo.textureHeight = reader.readFloat();
        geo.textureWidth = reader.readFloat();
        geo.visibleBoundsHeight = reader.readFloat();
        geo.visibleBoundsWidth = reader.readFloat();

        int visibleBoundsOffsetSize = reader.readCount(3);
        geo.visibleBoundsOffset = new float[visibleBoundsOffsetSize];
        for (int i = 0; i < visibleBoundsOffsetSize; i++) {
            geo.visibleBoundsOffset[i] = reader.readFloat();
        }

        geo.unkFloat1 = reader.readFloat();
        geo.unkFloat2 = reader.readFloat();

        int hasInfoJsonFlag = reader.readVarInt();
        if (hasInfoJsonFlag > 0) {
            parseLegacyYSMInfo();
        }

        geo.footerPad1 = reader.readVarInt();
        geo.footerPad2 = reader.readVarInt();
        geo.footerPad3 = reader.readVarInt();

        return geo;
    }

    private void parseYSMJson() throws IOException {
        model.properties.sha256 = reader.readString();
        int isNewVersionYsm = reader.readVarInt();

        if (isNewVersionYsm != 0) {
            if (format <= 15) {
                reader.readVarInt(); // unknown
            }

            model.metadata.name = reader.readString();
            model.metadata.tips = reader.readString();
            model.metadata.licenseType = reader.readString();
            model.metadata.licenseDescription = reader.readString();

            int authorsCount = reader.readCount(4096);
            for (int i = 0; i < authorsCount; i++) {
                YsmBinaryModel.RawMetadata.Author author = new YsmBinaryModel.RawMetadata.Author();
                author.name = reader.readString();
                author.role = reader.readString();
                int contactsCount = reader.readCount(4096);
                for (int j = 0; j < contactsCount; j++) {
                    author.contacts.put(reader.readString(), reader.readString());
                }
                author.comment = reader.readString();
                model.metadata.authors.add(author);
            }

            int linksCount = reader.readCount(4096);
            for (int i = 0; i < linksCount; i++) {
                model.metadata.links.put(reader.readString(), reader.readString());
            }
        }

        if (isNewVersionYsm == 0 && format <= 15) {
            return;
        }

        model.properties.widthScale = reader.readFloat();
        model.properties.heightScale = reader.readFloat();

        int extraAnimationsCount = reader.readCount(4096);
        for (int i = 0; i < extraAnimationsCount; i++) {
            putUnique(model.properties.extraAnimations, reader.readString(), reader.readString());
        }

        if (format > 9) {
            int extraAnimationButtonsCount = reader.readCount(4096);
            for (int i = 0; i < extraAnimationButtonsCount; i++) {
                YsmBinaryModel.ExtraAnimationButton btn = new YsmBinaryModel.ExtraAnimationButton();
                btn.id = reader.readString();
                btn.name = reader.readString();
                reader.readVarInt(); // buttonPadding

                int configurationFormsCount = reader.readCount(4096);
                for (int j = 0; j < configurationFormsCount; j++) {
                    YsmBinaryModel.ConfigForm form = new YsmBinaryModel.ConfigForm();
                    form.type = reader.readString();
                    form.title = reader.readString();
                    form.description = reader.readString();
                    form.defaultValue = reader.readString();
                    form.step = reader.readFloat();
                    form.min = reader.readFloat();
                    form.max = reader.readFloat();
                    int labelsSize = reader.readCount(4096);
                    for (int l = 0; l < labelsSize; l++) {
                        form.labels.put(reader.readString(), reader.readString());
                    }
                    btn.forms.add(form);
                }
                model.properties.extraAnimationButtons.add(btn);
            }

            int extraAnimationClassifyCount = reader.readCount(4096);
            for (int i = 0; i < extraAnimationClassifyCount; i++) {
                YsmBinaryModel.ExtraAnimationClassify classify = new YsmBinaryModel.ExtraAnimationClassify();
                classify.id = reader.readString();
                int classificationExtrasCount = reader.readCount(4096);
                for (int j = 0; j < classificationExtrasCount; j++) {
                    classify.extras.put(reader.readString(), reader.readString());
                }
                model.properties.extraAnimationClassifies.add(classify);
            }
        }

        model.properties.defaultTexture = reader.readString();
        model.properties.previewAnimation = reader.readString();
        model.properties.isFree = reader.readVarInt() != 0;

        if (format > 4) {
            model.properties.renderLayersFirst = reader.readVarInt() != 0;
        }

        if (format >= 15) {
            model.properties.allCutout = reader.readVarInt() != 0;
            model.properties.disablePreviewRotation = reader.readVarInt() != 0;
        }

        if (format > 15) {
            model.properties.guiNoLighting = reader.readVarInt() != 0;
            if (format >= 32) {
                model.properties.mergeMultilineExpr = reader.readVarInt() != 0;
            }

            model.properties.guiForeground = reader.readString();
            model.properties.guiBackground = reader.readString();

            int avatarsCount = reader.readCount(4096);
            for (int i = 0; i < avatarsCount; i++) {
                YsmBinaryModel.RawImage avatar = new YsmBinaryModel.RawImage();
                avatar.name = reader.readString();
                avatar.data = reader.readByteArray();
                avatar.width = reader.readVarInt();
                avatar.height = reader.readVarInt();
                reader.checkImageDimensions(avatar.width, avatar.height);
                avatar.format = reader.readVarInt();
                avatar.unknownFlag = reader.readVarInt();
                if (i < model.metadata.authors.size()) {
                    model.metadata.authors.get(i).avatar = avatar.name;
                    model.metadata.authors.get(i).avatarImage = avatar;
                } else {
                    model.metadata.extraAvatars.add(avatar);
                }
            }
        }

        if (format <= 15) {
            return;
        }

        int backgroundImagesCount = reader.readCount(4096);
        for (int i = 0; i < backgroundImagesCount; i++) {
            YsmBinaryModel.RawImage bg = new YsmBinaryModel.RawImage();
            bg.name = reader.readString();
            bg.data = reader.readByteArray();
            bg.width = reader.readVarInt();
            bg.height = reader.readVarInt();
            reader.checkImageDimensions(bg.width, bg.height);
            bg.format = reader.readVarInt();
            bg.unknownFlag = reader.readVarInt();
            model.properties.backgroundImages.add(bg);
        }
    }

    private void parseLegacyYSMInfo() throws IOException {
        model.metadata.name = reader.readString();
        model.metadata.tips = reader.readString();
        int extraAnimationsCount = reader.readCount(4096);
        for (int i = 0; i < extraAnimationsCount; i++) {
            model.properties.extraAnimations.put("extra" + i, reader.readString());
        }
        int authorsCount = reader.readCount(4096);
        for (int i = 0; i < authorsCount; i++) {
            YsmBinaryModel.RawMetadata.Author author = new YsmBinaryModel.RawMetadata.Author();
            author.name = reader.readString();
            model.metadata.authors.add(author);
        }
        model.metadata.licenseType = reader.readString();
        model.properties.isFree = reader.readVarInt() != 0;
    }

    private YsmBinaryModel.RawAnimationFile parseAnimations() throws IOException {
        YsmBinaryModel.RawAnimationFile animFile = new YsmBinaryModel.RawAnimationFile();

        int animationCount = reader.readCount(4096);
        for (int animIndex = 0; animIndex < animationCount; ++animIndex) {
            YsmBinaryModel.RawAnimation anim = new YsmBinaryModel.RawAnimation();
            anim.name = reader.readString();
            anim.length = reader.readAnimationLength() / 20.0f;
            anim.loopMode = reader.readVarInt();

            if (format > 9) {
                anim.unkInt1 = reader.readVarInt();
                anim.unkInt2 = reader.readVarInt();
                int blendWeightMolangCount = reader.readCount(4096);
                for (int i = 0; i < blendWeightMolangCount; i++) {
                    // 1 = float
                    // 2 = string
                    byte datatype = reader.readByte();

                    if (datatype == 0x01) {
                        anim.blendWeight = reader.readFloat();
                    } else if (datatype == 0x02) {
                        anim.blendWeight = reader.readString();
                    } else {
                        throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "binary/blend-value-type", reader.getOffset());
                    }
                }
                anim.unkInt4 = reader.readVarInt();
            }

            int boneCount = reader.readCount(4096);
            for (int i = 0; i < boneCount; ++i) {
                YsmBinaryModel.RawBoneAnimation ba = new YsmBinaryModel.RawBoneAnimation();
                ba.boneName = reader.readString();
                parseChannel(ba.rotation);
                parseChannel(ba.position);
                parseChannel(ba.scale);
                anim.boneAnimations.add(ba);
            }

            // Timeline
            int timelineEventGroupsCount = reader.readCount(4096);
            for (int i = 0; i < timelineEventGroupsCount; ++i) {
                YsmBinaryModel.RawTimelineEvent event = new YsmBinaryModel.RawTimelineEvent();
                int timelineEventsCount = reader.readCount(4096);
                for (int j = 0; j < timelineEventsCount; ++j) {
                    event.events.add(reader.readString());
                }
                event.timestamp = reader.readFloat() / 20.0f;
                anim.timelineEvents.add(event);
            }

            // Effects
            if (format > 9) {
                int soundEffectsCount = reader.readCount(4096);
                for (int i = 0; i < soundEffectsCount; i++) {
                    YsmBinaryModel.RawSoundEffect sfx = new YsmBinaryModel.RawSoundEffect();
                    sfx.effectName = reader.readString();
                    sfx.timestamp = reader.readFloat() / 20.0f;
                    anim.soundEffects.add(sfx);
                }
            }
            putUnique(animFile.animations, anim.name, anim);
        }

        return animFile;
    }

    private void parseChannel(List<YsmBinaryModel.RawKeyframe> channel) throws IOException {
        int keyframeCount = reader.readAnimationKeyframesCount();
        if (keyframeCount == 0) {
            return;
        }

        for (int i = 0; i < keyframeCount; i++) {
            YsmBinaryModel.RawKeyframe kf = new YsmBinaryModel.RawKeyframe();
            kf.timestamp = reader.readFloat() / 20.0f;
            kf.interpolationMode = reader.readVarInt();

            Object[] firstData = new Object[3];
            for (int j = 0; j < 3; j++) {
                byte datatype = reader.readByte();
                if (datatype == 0x01) {
                    firstData[j] = reader.readFloat();
                } else if (datatype == 0x02) {
                    firstData[j] = reader.readString();
                } else {
                    throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "binary/keyframe-value-type", reader.getOffset());
                }
            }

            kf.hasPreData = reader.readVarInt() > 0;
            if (kf.hasPreData) {
                for (int j = 0; j < 3; j++) {
                    byte datatype = reader.readByte();
                    if (datatype == 0x01) {
                        kf.postData[j] = reader.readFloat();
                    } else if (datatype == 0x02) {
                        kf.postData[j] = reader.readString();
                    } else {
                        throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "binary/keyframe-value-type", reader.getOffset());
                    }
                }

                kf.preData = firstData;
                kf.hasPreData = true;
            } else {

                kf.postData = firstData;
                kf.hasPreData = false;
            }
            channel.add(kf);
        }
    }

    private void parseAnimationControllers(List<YsmBinaryModel.RawAnimationControllerFile> targetList, boolean readName) throws IOException {
        int controllerCount = reader.readCount(4096);
        for (int i = 0; i < controllerCount; i++) {
            YsmBinaryModel.RawAnimationControllerFile file = new YsmBinaryModel.RawAnimationControllerFile();

            if (format <= 15) {
                file.legacyUnknownInt = reader.readVarInt();
                file.name = "legacy_controller_" + i;
            } else {
                if (readName) {
                    file.name = reader.readString();
                }
                file.hash = reader.readString();
            }

            parseAnimationControllerBody(file.controllers);
            targetList.add(file);
        }
    }

    private void parseAnimationControllerBody(Map<String, YsmBinaryModel.RawAnimationController> targetMap) throws IOException {
        int animationCount = reader.readCount(4096);
        for (int animIndex = 0; animIndex < animationCount; ++animIndex) {
            YsmBinaryModel.RawAnimationController entry = new YsmBinaryModel.RawAnimationController();
            entry.animationName = reader.readString();
            entry.initialState = reader.readString();

            int statesCount = reader.readCount(4096);
            for (int s = 0; s < statesCount; s++) {
                YsmBinaryModel.RawControllerState state = new YsmBinaryModel.RawControllerState();
                state.name = reader.readString();

                // animations
                int animationsSize = reader.readCount(4096);
                for (int j = 0; j < animationsSize; j++) {
                    state.animations.put(reader.readString(), reader.readString());
                }
                // transitions
                int transitionsSize = reader.readCount(4096);
                for (int j = 0; j < transitionsSize; j++) {
                    state.transitions.put(reader.readString(), reader.readString());
                }
                // on_entry
                int onEntryCount = reader.readCount(4096);
                for (int j = 0; j < onEntryCount; j++) {
                    state.onEntry.add(reader.readString());
                }
                // on_exit
                int onExitCount = reader.readCount(4096);
                for (int j = 0; j < onExitCount; j++) {
                    state.onExit.add(reader.readString());
                }
                // blend_transition
                if (reader.readVarInt() != 0) {
                    state.blendTransitionValue = reader.readFloat();
                } else {
                    int blendTransitionsCount = reader.readCount(4096);
                    for (int j = 0; j < blendTransitionsCount; j++) {
                        state.blendTransitions.put(reader.readFloat(), reader.readFloat());
                    }
                }
                state.blendViaShortestPath = reader.readVarInt() != 0;
                // sound_effects (format > 26)
                if (format > 26) {
                    int soundEffectsCount = reader.readCount(4096);
                    for (int j = 0; j < soundEffectsCount; j++) {
                        state.soundEffects.add(reader.readString());
                    }
                }
                entry.states.add(state);
            }
            putUnique(targetMap, entry.animationName, entry);
        }
    }

    private void parseSoundFiles() throws IOException {
        int soundCount = reader.readCount(4096);
        for (int i = 0; i < soundCount; i++) {
            String soundName = reader.readString();
            String hash = "";
            if (format > 15) {
                hash = reader.readString(); // soundHash
            }
            byte[] data = reader.readByteArray();
            putUnique(model.soundFiles, soundName, new YsmBinaryModel.RawDataFile(hash, data));
        }
    }

    private void parseFunctionFiles() throws IOException {
        int functionCount = reader.readCount(4096);
        for (int i = 0; i < functionCount; i++) {
            String functionName = reader.readString();
            String hash = reader.readString();
            byte[] data = reader.readByteArray();
            putUnique(model.functionFiles, functionName, new YsmBinaryModel.RawDataFile(hash, data));
        }
    }

    private void parseLanguageFiles() throws IOException {
        int languageCount = reader.readCount(4096);
        for (int i = 0; i < languageCount; i++) {
            String languageName = reader.readString();
            String hash = reader.readString();
            int nodesCount = reader.readCount(4096);
            Map<String, String> langMap = new java.util.LinkedHashMap<>();
            for (int j = 0; j < nodesCount; j++) {
                langMap.put(reader.readString(), reader.readString());
            }
            putUnique(model.languageFiles, languageName, new YsmBinaryModel.RawLanguageFile(hash, langMap));
        }
    }

    private void parseTextureFiles(Map<String, YsmBinaryModel.RawTexture> targetMap) throws IOException {
        int textureCount = reader.readCount(4096);
        for (int i = 0; i < textureCount; i++) {
            YsmBinaryModel.RawTexture tex = new YsmBinaryModel.RawTexture();
            tex.name = reader.readString();
            tex.hash = reader.readString();
            tex.data = reader.readByteArray();
            tex.width = reader.readVarInt();
            tex.height = reader.readVarInt();
            reader.checkImageDimensions(tex.width, tex.height);
            tex.imageFormat = reader.readVarInt();
            tex.unknownFlag = reader.readVarInt();

            int subTextureSize = reader.readCount(4096);
            for (int j = 0; j < subTextureSize; j++) {
                YsmBinaryModel.RawTexture.SubTexture subTex = new YsmBinaryModel.RawTexture.SubTexture();
                subTex.specularType = reader.readVarInt();
                SpecialImageResult specRes = parseSpecialImage();
                subTex.hash = specRes.hash;
                subTex.data = specRes.data;
                subTex.width = reader.readVarInt();
                subTex.height = reader.readVarInt();
                reader.checkImageDimensions(subTex.width, subTex.height);
                subTex.imageFormat = reader.readVarInt();
                subTex.unknownFlag = reader.readVarInt();
                tex.subTextures.add(subTex);
            }
            putUnique(targetMap, tex.name, tex);
        }
    }

    private SpecialImageResult parseSpecialImage() throws IOException {
        String imageHash = reader.readString();
        byte[] imageData = reader.readByteArray();
        return new SpecialImageResult(imageHash, imageData);
    }

    private <T> void putUnique(Map<String, T> target, String name, T value) throws IOException {
        if (name == null || name.isBlank() || target.containsKey(name)) {
            throw new YsmDecodeException(YsmDecodeException.Code.INVALID_DATA, "binary/duplicate-or-empty-name", reader.getOffset());
        }
        target.put(name, value);
    }

    private float[] readVector3D() throws IOException {
        return new float[]{reader.readFloat(), reader.readFloat(), reader.readFloat()};
    }

    private void assignMainModels(List<YsmBinaryModel.RawGeometry> tempMainModels) throws IOException {
        for (YsmBinaryModel.RawGeometry tempMainModel : tempMainModels) {
            switch (tempMainModel.modelType) {
                case 1:
                    model.mainEntity.mainModel = tempMainModel;
                    break;
                case 2:
                    model.mainEntity.armModel = tempMainModel;
                    break;
                case 3:
                    arrow().model = tempMainModel;
                    break;
                default:
                    throw new RuntimeException("Unknown model type: " + tempMainModel.modelType);
            }
        }
    }

    private record SpecialImageResult(String hash, byte[] data) {
    }
}
