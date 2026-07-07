package org.figuramc.figura.avatar;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.animation.Animation;
import org.figuramc.figura.animation.AnimationPlayer;
import org.figuramc.figura.backend2.NetworkStuff;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.ducks.FiguraEntityRenderStateExtension;
import org.figuramc.figura.ducks.NodeCollectorExtension;
import org.figuramc.figura.gui.FiguraPortraitRenderState;
import org.figuramc.figura.lua.FiguraLuaPrinter;
import org.figuramc.figura.lua.FiguraLuaRuntime;
import org.figuramc.figura.lua.api.TextureAPI;
import org.figuramc.figura.lua.api.data.FiguraBuffer;
import org.figuramc.figura.lua.api.data.FiguraInputStream;
import org.figuramc.figura.lua.api.data.FiguraOutputStream;
import org.figuramc.figura.lua.api.entity.EntityAPI;
import org.figuramc.figura.lua.api.particle.ParticleAPI;
import org.figuramc.figura.lua.api.ping.PingArg;
import org.figuramc.figura.lua.api.ping.PingFunction;
import org.figuramc.figura.lua.api.sound.SoundAPI;
import org.figuramc.figura.lua.api.world.BlockStateAPI;
import org.figuramc.figura.lua.api.world.ItemStackAPI;
import org.figuramc.figura.lua.api.world.WorldAPI;
import org.figuramc.figura.math.matrix.FiguraMat3;
import org.figuramc.figura.math.matrix.FiguraMat4;
import org.figuramc.figura.math.vector.FiguraVec3;
import org.figuramc.figura.mixin.gui.GuiGraphicsAccessor;
import org.figuramc.figura.model.FiguraModelPart;
import org.figuramc.figura.model.ParentType;
import org.figuramc.figura.model.PartCustomization;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.figuramc.figura.molang.MolangEngine;
import org.figuramc.figura.molang.parser.ast.Expression;
import org.figuramc.figura.molang.runtime.ExpressionEvaluator;
import org.figuramc.figura.mixin.ClientAvatarStateAccessor;
import org.figuramc.figura.molang.runtime.binding.QueryVariables;
import org.figuramc.figura.molang.runtime.binding.MolangBindings;
import org.figuramc.figura.molang.storage.VariableStorage;
import org.figuramc.figura.model.rendering.FiguraRenderer;
import org.figuramc.figura.model.rendering.EntityRenderMode;
import org.figuramc.figura.model.rendering.ImmediateFiguraRenderer;
import org.figuramc.figura.model.rendering.PartFilterScheme;
import org.figuramc.figura.model.rendering.texture.FiguraTexture;
import org.figuramc.figura.model.ysm.YsmModelRuntime;
import org.figuramc.figura.permissions.PermissionManager;
import org.figuramc.figura.permissions.PermissionPack;
import org.figuramc.figura.permissions.Permissions;
import org.figuramc.figura.utils.ColorUtils;
import org.figuramc.figura.utils.EntityUtils;
import org.figuramc.figura.utils.PathUtils;
import org.figuramc.figura.utils.RefilledNumber;
import org.figuramc.figura.utils.Version;
import org.figuramc.figura.utils.ui.UIHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

// the avatar class
// contains all things related to the avatar
// and also related to the owner, like its permissions
public class Avatar {

    private static CompletableFuture<Void> tasks;
    public static boolean firstPerson;

    // properties
    public final UUID owner;
    public final EntityType<?> entityType;
    public CompoundTag nbt;
    public boolean loaded = true;
    public final boolean isHost;

    //metadata
    public String name, entityName;
    public String authors;
    public Version version;
    public String id;
    public int fileSize;
    public String color;
    public Map<String, String> badgeToColor = new HashMap<>();
    public Map<String, byte[]> resources = new HashMap<>();

    public boolean minify;

    // Runtime data
    private final Queue<Runnable> events = new ConcurrentLinkedQueue<>();
    public final ArrayList<FiguraBuffer> openBuffers = new ArrayList<>();
    public final ArrayList<FiguraInputStream> openInputStreams = new ArrayList<>();
    public final ArrayList<FiguraOutputStream> openOutputStreams = new ArrayList<>();

    public FiguraRenderer renderer;
    private YsmModelRuntime ysmRuntime;
    public FiguraLuaRuntime luaRuntime;
    public EntityRenderMode renderMode = EntityRenderMode.OTHER;

    public final PermissionPack.PlayerPermissionPack permissions;

    public final Map<String, SoundBuffer> customSounds = new HashMap<>();
    public final Map<Integer, Animation> animations = new HashMap<>();

    // -- Molang engine -- //
    /** Shared Molang engine instance (lazily initialized) for stateless parsing */
    private static MolangEngine molangEngine;
    /** Per-avatar bindings that include this avatar's v/c/t storage */
    private MolangBindings avatarBindings;
    /** Molang context for this avatar (dynamic variables, evaluator) */
    private MolangContext molangContext;

    /**
     * Returns the shared Molang engine instance.
     * Created on first access with default query/math bindings (no v/c/t).
     * Used for parsing Molang strings that don't reference per-avatar storage.
     */
    public static MolangEngine getMolangEngine() {
        if (molangEngine == null) {
            MolangBindings bindings = new MolangBindings();
            registerDefaultQueryBindings(bindings);
            molangEngine = MolangEngine.fromCustomBinding(bindings);
        }
        return molangEngine;
    }

    /**
     * Creates or returns per-avatar MolangBindings that include this avatar's
     * v&#47;c&#47;t variable storages, enabling per-avatar variable isolation.
     */
    public MolangBindings getAvatarBindings() {
        if (avatarBindings == null) {
            avatarBindings = new MolangBindings();
            registerDefaultQueryBindings(avatarBindings);

            // Bind this avatar's variable storages for v.*/c.*/t.* access
            if (molangContext != null) {
                avatarBindings.register("v", molangContext.variables);
                avatarBindings.register("variable", molangContext.variables);
                avatarBindings.register("c", molangContext.controller);
                avatarBindings.register("context", molangContext.controller);
                avatarBindings.register("t", molangContext.temp);
                avatarBindings.register("temp", molangContext.temp);
            }
        }
        return avatarBindings;
    }

    /**
     * Registers default query.* variables into the given bindings.
     * These are stateless Variables that read from the EvaluationContext's entity
     * (which is the MolangContext) at evaluation time.
     */
    private static void registerDefaultQueryBindings(MolangBindings bindings) {
        // ===== Time =====
        bindings.registerQuery("anim_time",            QueryVariables.ANIM_TIME);
        bindings.registerQuery("life_time",            QueryVariables.LIFE_TIME);
        bindings.registerQuery("delta_time",           QueryVariables.DELTA_TIME);
        bindings.registerQuery("time_of_day",          QueryVariables.TIME_OF_DAY);
        bindings.registerQuery("time_stamp",           QueryVariables.TIME_STAMP);
        bindings.registerQuery("moon_phase",           QueryVariables.MOON_PHASE);
        bindings.registerQuery("frame_count",          QueryVariables.FRAME_COUNT);

        // ===== Actor =====
        bindings.registerQuery("actor_count",          QueryVariables.ACTOR_COUNT);

        // ===== Health =====
        bindings.registerQuery("health",               QueryVariables.HEALTH);
        bindings.registerQuery("max_health",           QueryVariables.MAX_HEALTH);
        bindings.registerQuery("hurt_time",            QueryVariables.HURT_TIME);

        // ===== Position =====
        bindings.registerQuery("position",             QueryVariables.POSITION_FUNC);
        bindings.registerQuery("position_x",           QueryVariables.POSITION_X);
        bindings.registerQuery("position_y",           QueryVariables.POSITION_Y);
        bindings.registerQuery("position_z",           QueryVariables.POSITION_Z);
        bindings.registerQuery("position_delta",       QueryVariables.POSITION_DELTA_FUNC);
        bindings.registerQuery("position_delta_x",     QueryVariables.POSITION_DELTA_X);
        bindings.registerQuery("position_delta_y",     QueryVariables.POSITION_DELTA_Y);
        bindings.registerQuery("position_delta_z",     QueryVariables.POSITION_DELTA_Z);
        bindings.registerQuery("distance_from_camera", QueryVariables.DISTANCE_FROM_CAMERA);
        bindings.registerQuery("rotation_to_camera",   QueryVariables.ROTATION_TO_CAMERA_FUNC);

        // ===== Movement =====
        bindings.registerQuery("ground_speed",         QueryVariables.GROUND_SPEED);
        bindings.registerQuery("vertical_speed",       QueryVariables.VERTICAL_SPEED);
        bindings.registerQuery("yaw_speed",            QueryVariables.YAW_SPEED);
        bindings.registerQuery("walk_distance",        QueryVariables.WALK_DISTANCE);
        bindings.registerQuery("modified_distance_moved", QueryVariables.MODIFIED_DISTANCE_MOVED);

        // ===== Rotation =====
        bindings.registerQuery("body_x_rotation",      QueryVariables.BODY_X_ROTATION);
        bindings.registerQuery("body_y_rotation",      QueryVariables.BODY_Y_ROTATION);
        bindings.registerQuery("head_x_rotation",      QueryVariables.HEAD_X_ROTATION);
        bindings.registerQuery("head_y_rotation",      QueryVariables.HEAD_Y_ROTATION);
        bindings.registerQuery("eye_target_x_rotation", QueryVariables.EYE_TARGET_X_ROTATION);
        bindings.registerQuery("eye_target_y_rotation", QueryVariables.EYE_TARGET_Y_ROTATION);
        bindings.registerQuery("cardinal_facing_2d",   QueryVariables.CARDINAL_FACING_2D);

        // ===== Boolean states =====
        bindings.registerQuery("is_on_ground",         QueryVariables.IS_ON_GROUND);
        bindings.registerQuery("is_jumping",           QueryVariables.IS_JUMPING);
        bindings.registerQuery("is_sneaking",          QueryVariables.IS_SNEAKING);
        bindings.registerQuery("is_sprinting",         QueryVariables.IS_SPRINTING);
        bindings.registerQuery("is_swimming",          QueryVariables.IS_SWIMMING);
        bindings.registerQuery("is_in_water",          QueryVariables.IS_IN_WATER);
        bindings.registerQuery("is_in_water_or_rain",  QueryVariables.IS_IN_WATER_OR_RAIN);
        bindings.registerQuery("is_on_fire",           QueryVariables.IS_ON_FIRE);
        bindings.registerQuery("is_riding",            QueryVariables.IS_RIDING);
        bindings.registerQuery("has_rider",            QueryVariables.HAS_RIDER);
        bindings.registerQuery("is_sleeping",          QueryVariables.IS_SLEEPING);
        bindings.registerQuery("is_spectator",         QueryVariables.IS_SPECTATOR);
        bindings.registerQuery("is_first_person",      QueryVariables.IS_FIRST_PERSON);
        bindings.registerQuery("is_using_item",        QueryVariables.IS_USING_ITEM);
        bindings.registerQuery("is_swinging",          QueryVariables.IS_SWINGING);
        bindings.registerQuery("is_eating",            QueryVariables.IS_EATING);
        bindings.registerQuery("is_playing_dead",      QueryVariables.IS_PLAYING_DEAD);
        bindings.registerQuery("has_cape",             QueryVariables.HAS_CAPE);

        // ===== Animation state =====
        bindings.registerQuery("all_animations_finished", QueryVariables.ALL_ANIMATIONS_FINISHED);
        bindings.registerQuery("any_animation_finished", QueryVariables.ANY_ANIMATION_FINISHED);
        bindings.registerQuery("swing_time",           QueryVariables.SWING_TIME);
        bindings.registerQuery("attack_time",          QueryVariables.ATTACK_TIME);

        // ===== Item =====
        bindings.registerQuery("item_in_use_duration", QueryVariables.ITEM_IN_USE_DURATION);
        bindings.registerQuery("item_max_use_duration", QueryVariables.ITEM_MAX_USE_DURATION);
        bindings.registerQuery("item_remaining_use_duration", QueryVariables.ITEM_REMAINING_USE_DURATION);
        bindings.registerQuery("equipment_count",      QueryVariables.EQUIPMENT_COUNT);
        bindings.registerQuery("max_durability",       QueryVariables.MAX_DURABILITY);
        bindings.registerQuery("remaining_durability", QueryVariables.REMAINING_DURABILITY);

        // ===== Biome / Tag queries =====
        bindings.registerQuery("biome_has_all_tags",   QueryVariables.BIOME_HAS_ALL_TAGS);
        bindings.registerQuery("biome_has_any_tag",    QueryVariables.BIOME_HAS_ANY_TAG);
        bindings.registerQuery("relative_block_has_all_tags", QueryVariables.RELATIVE_BLOCK_HAS_ALL_TAGS);
        bindings.registerQuery("relative_block_has_any_tag", QueryVariables.RELATIVE_BLOCK_HAS_ANY_TAG);
        bindings.registerQuery("is_item_name_any",     QueryVariables.IS_ITEM_NAME_ANY);
        bindings.registerQuery("equipped_item_all_tags", QueryVariables.EQUIPPED_ITEM_ALL_TAGS);
        bindings.registerQuery("equipped_item_any_tag", QueryVariables.EQUIPPED_ITEM_ANY_TAG);

        // ===== Misc =====
        bindings.registerQuery("cape_flap_amount",     QueryVariables.CAPE_FLAP_AMOUNT);
        bindings.registerQuery("player_level",         QueryVariables.PLAYER_LEVEL);

        // ===== Geometry =====
        bindings.registerQuery("geometry_is_model",    QueryVariables.GEOMETRY_IS_MODEL);
        bindings.registerQuery("geometry_is_block",    QueryVariables.GEOMETRY_IS_BLOCK);
        bindings.registerQuery("geometry_is_entity",   QueryVariables.GEOMETRY_IS_ENTITY);
        bindings.registerQuery("geometry_is_flat",     QueryVariables.GEOMETRY_IS_FLAT);

        // ===== Functions =====
        bindings.registerQuery("debug_output",         QueryVariables.DEBUG_OUTPUT_FUNC);

    }

    /**
     * Returns the Molang context for this avatar.
     */
    public MolangContext getMolangContext() {
        return molangContext;
    }

    /**
     * Represents the Molang evaluation context for a single avatar.
     * Contains variable storages and per-frame state populated from Minecraft.
     * Passed as the {@code entity} to {@link org.figuramc.figura.molang.runtime.ExpressionEvaluator}.
     *
     * <p>All query.* fields are updated each frame by {@link #populateQueryContext(Entity)}.
     * Molang variables ({@code query.anim_time}, etc.) read these fields via {@link QueryVariables}.</p>
     */
    public static class MolangContext {
        public final VariableStorage variables = new VariableStorage();  // v.* / variable.*
        public final VariableStorage controller = new VariableStorage(); // c.* / context.*
        public final org.figuramc.figura.molang.storage.TempVariableStorage temp =
                new org.figuramc.figura.molang.storage.TempVariableStorage(); // t.* / temp.*

        // ===== Time fields =====
        public float anim_time;
        public float life_time;
        public float delta_time;
        public float time_of_day;
        public int moon_phase;
        public long frame_count;

        // ===== Health fields =====
        public float health;
        public float max_health;
        public float hurt_time;

        // ===== Position & movement =====
        public double pos_x, pos_y, pos_z;
        public double pos_delta_x, pos_delta_y, pos_delta_z;
        public float ground_speed;
        public float vertical_speed;
        public float yaw_speed;
        public float walk_distance;
        public float modified_distance_moved;

        // ===== Rotation =====
        public float body_x_rot, body_y_rot;
        public float head_x_rot, head_y_rot;

        // ===== Boolean states (0 or 1) =====
        public float is_on_ground;
        public float is_jumping;
        public float is_sneaking;
        public float is_sprinting;
        public float is_swimming;
        public float is_in_water;
        public float is_in_water_or_rain;
        public float is_on_fire;
        public float is_riding;
        public float is_sleeping;
        public float is_spectator;
        public float is_first_person;
        public float is_using_item;
        public float is_swinging;
        public float has_rider;
        public float is_eating;
        public float is_playing_dead;
        public float has_cape;

        // ===== Misc =====
        public float distance_from_camera;
        public int cardinal_facing_2d;
        public float swing_time;
        public float attack_time;
        public float item_in_use_duration;
        public float item_max_use_duration;
        public float item_remaining_use_duration;
        public float cape_flap_amount;
        public int equipment_count;
        public int player_level;
        public float time_stamp;
        public float eye_target_x_rot, eye_target_y_rot;

        // ===== Entity reference (for query functions that need it) =====
        /** The Minecraft Entity for this context, set each frame by populateQueryContext */
        public net.minecraft.world.entity.Entity entity;

        // ===== Previous frame data (for delta calculations) =====
        private double prev_pos_x, prev_pos_y, prev_pos_z;
        private float prev_body_y_rot;

        /**
         * Populates all query fields from a Minecraft Entity and the game state.
         * Called every frame before Molang evaluation.
         */
        public void populateQueryContext(Entity entity) {
            if (entity == null) return;
            this.entity = entity;

            // Store previous position for delta calculation
            prev_pos_x = pos_x;
            prev_pos_y = pos_y;
            prev_pos_z = pos_z;
            prev_body_y_rot = body_y_rot;

            // Entity position
            pos_x = entity.getX();
            pos_y = entity.getY();
            pos_z = entity.getZ();

            // Position delta
            pos_delta_x = pos_x - prev_pos_x;
            pos_delta_y = pos_y - prev_pos_y;
            pos_delta_z = pos_z - prev_pos_z;

            // Speed
            float dx = (float) pos_delta_x;
            float dz = (float) pos_delta_z;
            float dy = (float) pos_delta_y;
            ground_speed = (float) Math.sqrt(dx * dx + dz * dz);
            vertical_speed = Math.abs(dy);

            // Rotation
            body_y_rot = entity.getYRot();
            body_x_rot = entity.getXRot();
            yaw_speed = body_y_rot - prev_body_y_rot;

            // Entity state booleans
            is_on_ground = entity.onGround() ? 1f : 0f;
            is_in_water = entity.isInWater() ? 1f : 0f;
            is_in_water_or_rain = (entity.isInWater() || entity.isInWaterOrRain()) ? 1f : 0f;
            is_on_fire = entity.isOnFire() ? 1f : 0f;
            is_riding = entity.isPassenger() ? 1f : 0f;
            is_sprinting = entity.isSprinting() ? 1f : 0f;
            is_swimming = entity.isSwimming() ? 1f : 0f;
            is_spectator = entity.isSpectator() ? 1f : 0f;
            has_rider = entity.isVehicle() ? 1f : 0f;

            // Player-specific fields
            if (entity instanceof Player player) {
                health = player.getHealth();
                max_health = player.getMaxHealth();
                hurt_time = player.hurtTime;
                is_sneaking = player.isShiftKeyDown() ? 1f : 0f;
                is_sleeping = player.isSleeping() ? 1f : 0f;
                player_level = player.experienceLevel;

                // Walk distance from ClientAvatarState (via AbstractClientPlayer)
                if (player instanceof net.minecraft.client.player.AbstractClientPlayer acp) {
                    walk_distance = ((ClientAvatarStateAccessor) acp.avatarState()).figura$walkDist();
                }

                // Jump detection via local player input
                var mc2 = Minecraft.getInstance();
                if (mc2.player == player) {
                    is_jumping = (player.getDeltaMovement().y > 0.1 && !entity.onGround()) ? 1f : 0f;
                    is_eating = player.isUsingItem() && (player.getUsedItemHand() != null) ? 1f : 0f;
                    has_cape = 0f; // Cape detection requires skin data access
                }

                // Equipment count
                equipment_count = 0;
                for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                    var item = player.getItemBySlot(slot);
                    if (!item.isEmpty()) equipment_count++;
                }
                if (!player.getMainHandItem().isEmpty()) equipment_count++;
                if (!player.getOffhandItem().isEmpty()) equipment_count++;
            }

            // Frame counter
            frame_count++;

            // Level / world data
            var mc = Minecraft.getInstance();
            if (mc.level != null) {
                long dayTime = mc.level.getDefaultClockTime();
                time_of_day = dayTime % 24000L / 24000.0f;
                time_stamp = dayTime;
                moon_phase = mc.level.environmentAttributes().getDimensionValue(EnvironmentAttributes.MOON_PHASE).index();
                // Cardinal facing (0=South, 1=West, 2=North, 3=East)
                cardinal_facing_2d = entity.getDirection().get2DDataValue();
            }

            // Camera distance
            if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
                var camera = mc.gameRenderer.getMainCamera();
                float dxc = (float) (pos_x - camera.position().x);
                float dyc = (float) (pos_y - camera.position().y);
                float dzc = (float) (pos_z - camera.position().z);
                distance_from_camera = (float) Math.sqrt(dxc * dxc + dyc * dyc + dzc * dzc);
            }

            // First person
            is_first_person = mc.options.getCameraType().isFirstPerson() ? 1f : 0f;
        }

        /**
         * Resets all fields to defaults.
         */
        public void reset() {
            anim_time = 0;
            life_time = 0;
            delta_time = 0;
            time_of_day = 0;
            moon_phase = 0;
            frame_count = 0;
            health = 20f;
            max_health = 20f;
            hurt_time = 0;
            pos_x = pos_y = pos_z = 0;
            pos_delta_x = pos_delta_y = pos_delta_z = 0;
            ground_speed = vertical_speed = yaw_speed = 0;
            walk_distance = modified_distance_moved = 0;
            body_x_rot = body_y_rot = 0;
            head_x_rot = head_y_rot = 0;
            is_on_ground = is_jumping = is_sneaking = is_sprinting = 0;
            is_swimming = is_in_water = is_in_water_or_rain = is_on_fire = 0;
            is_riding = is_sleeping = is_spectator = is_first_person = 0;
            is_using_item = is_swinging = has_rider = is_eating = is_playing_dead = has_cape = 0;
            distance_from_camera = 0;
            cardinal_facing_2d = 0;
            swing_time = attack_time = 0;
            time_stamp = eye_target_x_rot = eye_target_y_rot = 0;
            item_in_use_duration = item_max_use_duration = item_remaining_use_duration = 0;
            cape_flap_amount = 0;
            equipment_count = 0;
            player_level = 0;
            entity = null;
            prev_pos_x = prev_pos_y = prev_pos_z = 0;
            prev_body_y_rot = 0;
        }
    }

    // runtime status
    public boolean hasTexture, scriptError;
    public Component errorText;
    public Set<Permissions> noPermissions = new HashSet<>();
    public Set<Permissions> permissionsToTick = new HashSet<>();
    public int lastPlayingSound = 0;
    public int versionStatus = 0;

    // limits
    public int animationComplexity;
    public final Instructions complexity;
    public final Instructions init, render, worldRender, tick, worldTick, animation;
    public final Map<String, Instructions> customInstructions = new HashMap<>();
    public final RefilledNumber particlesRemaining, soundsRemaining;
    private Avatar(UUID owner, EntityType<?> type, String name) {
        this.owner = owner;
        this.entityType = type;
        this.isHost = type == EntityType.PLAYER && FiguraMod.isLocal(owner);
        this.permissions = type == EntityType.PLAYER ? PermissionManager.get(owner) : PermissionManager.getMobPermissions(owner);
        this.complexity = new Instructions(permissions.get(Permissions.COMPLEXITY));
        this.init = new Instructions(permissions.get(Permissions.INIT_INST));
        this.render = new Instructions(permissions.get(Permissions.RENDER_INST));
        this.worldRender = new Instructions(permissions.get(Permissions.WORLD_RENDER_INST));
        this.tick = new Instructions(permissions.get(Permissions.TICK_INST));
        this.worldTick = new Instructions(permissions.get(Permissions.WORLD_TICK_INST));
        this.animation = new Instructions(permissions.get(Permissions.ANIMATION_INST));
        this.particlesRemaining = new RefilledNumber(permissions.get(Permissions.PARTICLES));
        this.soundsRemaining = new RefilledNumber(permissions.get(Permissions.SOUNDS));
        this.entityName = name == null ? "" : name;

        for (Collection<Permissions> pluginPermissions : PermissionManager.CUSTOM_PERMISSIONS.values()) {
            for (Permissions customPermission : pluginPermissions) {
                customInstructions.putIfAbsent(customPermission.name, new Instructions(permissions.get(customPermission)));
            }
        }
    }

    public Avatar(UUID owner) {
        this(owner, EntityType.PLAYER, EntityUtils.getNameForUUID(owner));
    }

    public Avatar(Entity entity) {
        this(entity.getUUID(), entity.getType(), entity.getName().getString());
    }

    public Avatar(EntityRenderState entity) {
        this(AvatarManager.ENTITY_CACHE.computeIfAbsent((int)(entity instanceof AvatarRenderState playerRenderState ? playerRenderState.id : ((FiguraEntityRenderStateExtension)entity).figura$getEntityId()), (id2) -> WorldAPI.getCurrentWorld().getEntity(id2)));
    }

    public void load(CompoundTag nbt) {
        Runnable toRun = () -> {
            this.nbt = nbt;
            loaded = false;
        };

        if (tasks == null || tasks.isDone()) {
            tasks = CompletableFuture.runAsync(toRun);
        } else {
            tasks.thenRun(toRun);
        }

        tasks.join();

        if (nbt == null) {
            loaded = true;
            return;
        }

        tasks.thenRun(() -> {
            try {
                // metadata
                CompoundTag metadata = nbt.getCompoundOrEmpty("metadata");
                name = metadata.getStringOr("name", "");
                authors = metadata.getStringOr("authors", "");
                version = new Version(metadata.getStringOr("ver", ""));
                if (metadata.contains("id"))
                    id = metadata.getStringOr("id", "");
                if (metadata.contains("color"))
                    color = metadata.getStringOr("color", "");
                if (metadata.contains("minify"))
                    minify = metadata.getBooleanOr("minify", false);
                if (nbt.contains("resources")) {
                    CompoundTag res = nbt.getCompoundOrEmpty("resources");
                    for (String k :
                            res.keySet()) {
                        resources.put(k, res.getByteArray(k).orElse(new byte[0]));
                    }
                }
                for (String key : metadata.keySet()) {
                    if (key.contains("badge_color_")) {
                        badgeToColor.put(key.replace("badge_color_", ""), metadata.getStringOr(key, ""));
                    }
                }
                fileSize = getFileSize();
                versionStatus = getVersionStatus();
                if (entityName.isBlank())
                    entityName = name;

                if (metadata.getStringOr("format", "").equals("ysm-native") && nbt.contains("ysm")) {
                    ysmRuntime = YsmModelRuntime.fromNbt(this, nbt.getCompoundOrEmpty("ysm"));
                    renderer = null;
                    hasTexture = true;
                } else {
                    // animations and models
                    loadAnimations();
                    renderer = new ImmediateFiguraRenderer(this);
                }

                // sounds and script
                loadCustomSounds();
                createLuaRuntime();
            } catch (Exception e) {
                FiguraMod.LOGGER.error("", e);
                clean();
                this.nbt = null;
                this.renderer = null;
                this.luaRuntime = null;
            }

            loaded = true;
        });
    }

    public void tick() {
        if (scriptError || luaRuntime == null || !loaded)
            return;

        // fetch this avatar entity
        if (luaRuntime.getUser() == null) {
            Entity entity = EntityUtils.getEntityByUUID(owner);
            if (entity != null) {
                luaRuntime.setUser(entity);
                run("ENTITY_INIT", init.post());
            }
        }

        // tick permissions
        for (Permissions t : permissionsToTick) {
            if (permissions.get(t) > 0) {
                noPermissions.remove(t);
            } else {
                noPermissions.add(t);
            }
        }
        if (lastPlayingSound > 0)
            lastPlayingSound--;

        // sound
        particlesRemaining.set(permissions.get(Permissions.PARTICLES));
        particlesRemaining.tick();

        // particles
        soundsRemaining.set(permissions.get(Permissions.SOUNDS));
        soundsRemaining.tick();

        // call events
        FiguraMod.pushProfiler("worldTick");
        worldTick.reset(permissions.get(Permissions.WORLD_TICK_INST));
        run("WORLD_TICK", worldTick);

        FiguraMod.popPushProfiler("tick");
        tick.reset(permissions.get(Permissions.TICK_INST));
        tickEvent();

        FiguraMod.popProfiler();
    }

    public void render(float delta) {
        if (complexity.remaining <= 0) {
            noPermissions.add(Permissions.COMPLEXITY);
        } else {
            noPermissions.remove(Permissions.COMPLEXITY);
        }

        complexity.reset(permissions.get(Permissions.COMPLEXITY));

        if (scriptError || luaRuntime == null || !loaded)
            return;

        render.reset(permissions.get(Permissions.RENDER_INST));
        worldRender.reset(permissions.get(Permissions.WORLD_RENDER_INST));
        run("WORLD_RENDER", worldRender, delta);
    }

    public void runPing(int id, byte[] data) {
        events.offer(() -> {
            if (scriptError || luaRuntime == null || !loaded)
                return;

            LuaValue[] args = PingArg.fromByteArray(data, this);
            String name = luaRuntime.ping.getName(id);
            PingFunction function = luaRuntime.ping.get(name);
            if (args == null || function == null)
                return;

            FiguraLuaPrinter.sendPingMessage(this, name, data.length, args);
            luaRuntime.run(function.func, tick, (Object[]) args);
        });
    }

    public LuaValue loadScript(String name, String chunk) {
        return scriptError || luaRuntime == null || !loaded ? null : luaRuntime.load(name, chunk);
    }

    private void flushQueuedEvents() {
        // run all queued events
        Runnable e;
        while ((e = events.poll()) != null) {
            try {
                e.run();
            } catch (Exception | StackOverflowError ex) {
                if (luaRuntime != null)
                    luaRuntime.error(ex);
            }
        }
    }

    @Nullable
    public Varargs run(Object toRun, Instructions limit, Object... args) {
        // stuff that was not run yet
        flushQueuedEvents();

        if (scriptError || luaRuntime == null || !loaded)
            return null;

        // run event
        Varargs ret = luaRuntime.run(toRun, limit, args);

        // stuff that this run produced
        flushQueuedEvents();

        // return
        return ret;
    }

    public void punish(int amount) {
        if (luaRuntime != null)
            luaRuntime.takeInstructions(amount);
    }

    // -- script events -- // 

    private boolean isCancelled(Varargs args) {
        if (args == null)
            return false;
        for (int i = 1; i <= args.narg(); i++) {
            if (args.arg(i).isboolean() && args.arg(i).checkboolean())
                return true;
        }
        return false;
    }

    public void tickEvent() {
        if (loaded && luaRuntime != null && luaRuntime.getUser() != null)
            run("TICK", tick);
    }

    public void renderEvent(float delta, FiguraMat4 poseMatrix) {
        if (loaded && luaRuntime != null && luaRuntime.getUser() != null)
            run("RENDER", render, delta, renderMode.name(), poseMatrix);
    }

    public void postRenderEvent(float delta, FiguraMat4 poseMatrix) {
        if (loaded && luaRuntime != null && luaRuntime.getUser() != null)
            run("POST_RENDER", render.post(), delta, renderMode.name(), poseMatrix);
        renderMode = EntityRenderMode.OTHER;
    }

    public void postWorldRenderEvent(float delta) {
        if (!loaded)
            return;

        if (renderer != null)
            renderer.allowMatrixUpdate = false;

        run("POST_WORLD_RENDER", worldRender.post(), delta);
    }

    public boolean skullRenderEvent(float delta, BlockStateAPI block, ItemStackAPI item, EntityAPI<?> entity, String mode) {
        Varargs result = null;
        if (loaded && renderer != null && renderer.interceptRendersIntoFigura)
            result = run("SKULL_RENDER", render, delta, block, item, entity, mode);
        return isCancelled(result);
    }

    public boolean useItemEvent(ItemStackAPI stack, String type, int particleCount) {
        Varargs result = loaded ? run("USE_ITEM", tick, stack, type, particleCount) : null;
        return isCancelled(result);
    }

    public boolean arrowRenderEvent(float delta, EntityAPI<?> arrow) {
        Varargs result = null;
        if (loaded) result = run("ARROW_RENDER", render, delta, arrow);
        return isCancelled(result);
    }

    public boolean tridentRenderEvent(float delta, EntityAPI<?> trident) {
        Varargs result = null;
        if (loaded) result = run("TRIDENT_RENDER", render, delta, trident);
        return isCancelled(result);
    }

    public boolean itemRenderEvent(ItemStackAPI item, String mode, FiguraVec3 pos, FiguraVec3 rot, FiguraVec3 scale, boolean leftHanded, PoseStack stack, SubmitNodeCollector nodeCollector, int light, int overlay) {
        if (!loaded || renderer == null || !renderer.interceptRendersIntoFigura) {
            return false;
        }
        Varargs result = run("ITEM_RENDER", render, item, mode, pos, rot, scale, leftHanded);
        NodeCollectorExtension extension = (NodeCollectorExtension) nodeCollector;

        if(result == null)
            return false;
        PoseStack copy = new PoseStack();
        copy.pushPose();
        copy.last().set(stack.last());

        boolean rendered = false;
        for (int i = 1; i <= result.narg(); i++) {
            if (result.arg(i).isuserdata(FiguraModelPart.class)) {
                FiguraModelPart modelPart = (FiguraModelPart) result.arg(i).checkuserdata(FiguraModelPart.class);

                boolean renderedPart = figuraItemRendered(modelPart);
                rendered |= renderedPart;
                if (renderedPart) {
                    extension.submitFiguraModel(this, null, (avatar, entity, bufferSource) -> {
                        renderItem(copy, bufferSource, modelPart, light, overlay);
                        return null;
                    });
                }
            }

        }
        return rendered;
    }

    public boolean itemRenderEventDirect(ItemStackAPI item, String mode, FiguraVec3 pos, FiguraVec3 rot, FiguraVec3 scale, boolean leftHanded, PoseStack stack, MultiBufferSource bufferSource, int light, int overlay) {
        if (!loaded || renderer == null || !renderer.interceptRendersIntoFigura) {
            return false;
        }
        Varargs result = run("ITEM_RENDER", render, item, mode, pos, rot, scale, leftHanded);

        if(result == null)
            return false;
        PoseStack copy = new PoseStack();
        copy.pushPose();
        copy.last().set(stack.last());

        boolean rendered = false;
        for (int i = 1; i <= result.narg(); i++) {
            if (result.arg(i).isuserdata(FiguraModelPart.class)) {
                FiguraModelPart modelPart = (FiguraModelPart) result.arg(i).checkuserdata(FiguraModelPart.class);

                boolean renderedPart = figuraItemRendered(modelPart);
                rendered |= renderedPart;
                if (renderedPart) {
                    renderItem(copy, bufferSource, modelPart, light, overlay);
                }
            }
        }
        return rendered;
    }

    public boolean playSoundEvent(String id, FiguraVec3 pos, float vol, float pitch, boolean loop, String category, String file) {
        Varargs result = null;
        if (loaded) result = run("ON_PLAY_SOUND", tick, id, pos, vol, pitch, loop, category, file);
        return isCancelled(result);
    }

    public void resourceReloadEvent() {
        if (loaded) run("RESOURCE_RELOAD", tick);
    }

    public void damageEvent(String sourceType, EntityAPI<?> sourceCause, EntityAPI<?> sourceDirect, FiguraVec3 sourcePosition) {
        if (loaded) run("DAMAGE", tick, sourceType, sourceCause, sourceDirect, sourcePosition);
    }

    // -- host only events -- //

    public String chatSendMessageEvent(String message) { // piped event
        Varargs val = loaded ? run("CHAT_SEND_MESSAGE", tick, message) : null;
        return val == null || (!val.isnil(1) && !Configs.CHAT_MESSAGES.value) ? message : val.isnil(1) ? "" : val.arg(1).tojstring();
    }

    public Pair<String, Integer> chatReceivedMessageEvent(String message, String json) { // special case
        Varargs val = loaded ? run("CHAT_RECEIVE_MESSAGE", tick, message, json) : null;
        if (val == null)
            return null;

        if (val.arg(1).isboolean() && !val.arg(1).checkboolean())
            return Pair.of(null, null);

        String msg = val.isnil(1) ? json : val.arg(1).tojstring();
        Integer color = null;
        if (val.arg(2).isuserdata(FiguraVec3.class))
            color = ColorUtils.rgbToInt((FiguraVec3) val.arg(2).checkuserdata(FiguraVec3.class));

        return Pair.of(msg, color);
    }

    public boolean mouseScrollEvent(double delta) {
        Varargs result = loaded ? run("MOUSE_SCROLL", tick, delta) : null;
        return isCancelled(result);
    }

    public boolean mouseMoveEvent(double x, double y) {
        Varargs result = loaded ? run("MOUSE_MOVE", tick, x, y) : null;
        return isCancelled(result);
    }

    public boolean mousePressEvent(int button, int action, int modifiers) {
        Varargs result = loaded ? run("MOUSE_PRESS", tick, button, action, modifiers) : null;
        return isCancelled(result);
    }

    public boolean keyPressEvent(int key, int action, int modifiers) {
        Varargs result = loaded ? run("KEY_PRESS", tick, key, action, modifiers) : null;
        return isCancelled(result);
    }

    public void charTypedEvent(String chars, int modifiers, int codePoint) {
        if (loaded) run("CHAR_TYPED", tick, chars, modifiers, codePoint);
    }

    public boolean totemEvent() {
        return isCancelled(loaded ? run("TOTEM",tick) : null);
    }

    // -- rendering events -- //

    private void render() {
        if (renderMode == EntityRenderMode.RENDER || renderMode == EntityRenderMode.FIRST_PERSON) {
            complexity.use(renderer.render());
            return;
        }

        int prev = complexity.remaining;
        complexity.remaining = permissions.get(Permissions.COMPLEXITY);
        renderer.render();
        complexity.remaining = prev;
    }

    public void render(Entity entity, float yaw, float delta, float alpha, PoseStack stack, MultiBufferSource bufferSource, int light, int overlay, EntityModel<?> entityModel, PartFilterScheme filter, boolean translucent, boolean glowing) {
        if (renderer == null || !loaded)
            return;

        renderer.vanillaModelData.update(entityModel);
        renderer.yaw = yaw;
        renderer.entity = entity;

        renderer.setupRenderer(
                filter, bufferSource, stack,
                delta, light, alpha, overlay,
                translucent, glowing
        );

        render();
    }

    public synchronized void worldRender(Entity entity, double camX, double camY, double camZ, PoseStack stack, MultiBufferSource bufferSource, int lightFallback, float tickDelta, EntityRenderMode mode) {
        if (renderer == null || !loaded)
            return;

        EntityRenderMode prevRenderMode = renderMode;
        renderMode = mode;
        boolean update = prevRenderMode != EntityRenderMode.OTHER || renderMode == EntityRenderMode.FIRST_PERSON_WORLD;

        renderer.pivotCustomizations.values().clear();
        renderer.allowMatrixUpdate = renderer.updateLight = update;
        renderer.entity = entity;

        renderer.setupRenderer(
                PartFilterScheme.WORLD, bufferSource, stack,
                tickDelta, lightFallback, 1f, OverlayTexture.NO_OVERLAY,
                false, false,
                camX, camY, camZ
        );

        complexity.use(renderer.renderSpecialParts());

        renderMode = prevRenderMode;
        renderer.updateLight = false;
    }

    public void capeRender(Entity entity, MultiBufferSource bufferSource, PoseStack stack, int light, float tickDelta, ModelPart cloak) {
        if (renderer == null || !loaded)
            return;

        FiguraMod.pushProfiler(FiguraMod.MOD_ID);
        FiguraMod.pushProfiler(this);
        FiguraMod.pushProfiler("capeRender");

        renderer.vanillaModelData.update(ParentType.Cape, cloak);
        renderer.entity = entity;

        renderer.setupRenderer(
                PartFilterScheme.CAPE, bufferSource, stack,
                tickDelta, light, 1f, OverlayTexture.NO_OVERLAY,
                renderer.translucent, renderer.glowing
        );

        render();

        FiguraMod.popProfiler(3);
    }

    public void elytraRender(Entity entity, MultiBufferSource bufferSource, PoseStack stack, int light, float tickDelta, EntityModel<?> model) {
        if (renderer == null || !loaded)
            return;

        FiguraMod.pushProfiler(FiguraMod.MOD_ID);
        FiguraMod.pushProfiler(this);
        FiguraMod.pushProfiler("elytraRender");

        renderer.entity = entity;

        renderer.setupRenderer(
                PartFilterScheme.LEFT_ELYTRA, bufferSource, stack,
                tickDelta, light, 1f, OverlayTexture.NO_OVERLAY,
                renderer.translucent, renderer.glowing
        );

        // left
        FiguraMod.pushProfiler("leftWing");
        renderer.vanillaModelData.update(ParentType.LeftElytra, model);
        renderer.renderSpecialParts();

        // right
        FiguraMod.popPushProfiler("rightWing");
        renderer.vanillaModelData.update(ParentType.RightElytra, model);
        renderer.currentFilterScheme = PartFilterScheme.RIGHT_ELYTRA;
        renderer.renderSpecialParts();

        FiguraMod.popProfiler(4);
    }

    public void firstPersonWorldRender(Entity watcher, MultiBufferSource bufferSource, PoseStack matrices, Camera camera, float tickDelta) {
        if (renderer == null || !loaded)
            return;

        FiguraMod.pushProfiler(FiguraMod.MOD_ID);
        FiguraMod.pushProfiler(this);
        FiguraMod.pushProfiler("firstPersonWorldRender");

        int light = Minecraft.getInstance().getEntityRenderDispatcher().getPackedLightCoords(watcher, tickDelta);
        Vec3 camPos = camera.position();

        worldRender(watcher, camPos.x, camPos.y, camPos.z, matrices, bufferSource, light, tickDelta, EntityRenderMode.FIRST_PERSON_WORLD);

        FiguraMod.popProfiler(3);
    }

    public void firstPersonRender(PoseStack stack, MultiBufferSource bufferSource, Player player, PlayerModel playerModel, ModelPart arm, int light, float tickDelta) {
        if (renderer == null || !loaded)
            return;

        boolean lefty = arm == playerModel.leftArm;

        FiguraMod.pushProfiler(FiguraMod.MOD_ID);
        FiguraMod.pushProfiler(this);
        FiguraMod.pushProfiler("firstPersonRender");
        FiguraMod.pushProfiler(lefty ? "leftArm" : "rightArm");

        PartFilterScheme filter = lefty ? PartFilterScheme.LEFT_ARM : PartFilterScheme.RIGHT_ARM;
        boolean config = Configs.ALLOW_FP_HANDS.value;
        renderer.allowHiddenTransforms = config;
        renderer.allowMatrixUpdate = false;
        renderer.ignoreVanillaVisibility = true;

        stack.pushPose();
        if (!config) {
            stack.mulPose(Axis.ZP.rotation(arm.zRot));
            stack.mulPose(Axis.YP.rotation(arm.yRot));
            stack.mulPose(Axis.XP.rotation(arm.xRot));
        }
        render(player, 0f, tickDelta, 1f, stack, bufferSource, light, OverlayTexture.NO_OVERLAY, playerModel, filter, false, false);
        stack.popPose();

        renderer.allowHiddenTransforms = true;
        renderer.ignoreVanillaVisibility = false;

        FiguraMod.popProfiler(4);
    }

    public void hudRender(PoseStack stack, MultiBufferSource bufferSource, Entity entity, float tickDelta) {
        if (renderer == null || !loaded)
            return;

        FiguraMod.pushProfiler(this);
        FiguraMod.pushProfiler("hudRender");

        stack.pushPose();
        stack.last().pose().scale(16, 16, -16);
        stack.last().normal().scale(1, 1, -1);

        Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_FLAT);
        GlStateManager._disableDepthTest();

        renderer.entity = entity;

        renderer.setupRenderer(
                PartFilterScheme.HUD, bufferSource, stack,
                tickDelta, LightCoordsUtil.FULL_BRIGHT, 1f, OverlayTexture.NO_OVERLAY,
                false, false
        );

        if (renderer.renderSpecialParts() > 0)
            ((MultiBufferSource.BufferSource) renderer.bufferSource).endBatch();

        GlStateManager._enableDepthTest();
        Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
        stack.popPose();

        FiguraMod.popProfiler(2);
    }

    public boolean skullRender(PoseStack stack, MultiBufferSource bufferSource, int light, Direction direction, float yaw) {
        if (renderer == null || !loaded || !renderer.interceptRendersIntoFigura)
            return false;
        renderer.allowPivotParts = false;

        renderer.setupRenderer(
                PartFilterScheme.SKULL, bufferSource, stack,
                1f, light, 1f, OverlayTexture.NO_OVERLAY,
                false, false
        );

        int comp = renderer.renderSpecialParts();
        complexity.use(comp);

        // head
        boolean bool = comp > 0 || headRender(stack, bufferSource, light, true);

        renderer.allowPivotParts = true;
        return bool;
    }

    public boolean headRender(PoseStack stack, MultiBufferSource bufferSource, int light, boolean useComplexity) {
        if (renderer == null || !loaded)
            return false;

        boolean oldMat = renderer.allowMatrixUpdate;

        // pre render
        renderer.setupRenderer(
                PartFilterScheme.HEAD, bufferSource, stack,
                1f, light, 1f, OverlayTexture.NO_OVERLAY,
                false, false
        );

        renderer.allowHiddenTransforms = false;
        renderer.allowMatrixUpdate = false;
        renderer.ignoreVanillaVisibility = true;

        // render
        int comp = renderer.render();
        if (useComplexity)
            complexity.use(comp);

        // pos render
        renderer.allowMatrixUpdate = oldMat;
        renderer.allowHiddenTransforms = true;
        renderer.ignoreVanillaVisibility = false;

        return comp > 0 && luaRuntime != null && !luaRuntime.vanilla_model.HEAD.checkVisible();
    }

    public boolean submitPortraitDraw(GuiGraphicsExtractor gui, Identifier fallback, int x, int y, int size, float modelScale, boolean upsideDown) {
        if (!Configs.AVATAR_PORTRAIT.value || renderer == null || !loaded)
            return false;

        // matrices
        Matrix3x2fStack pose = gui.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        //pose.scale(modelScale, modelScale * (upsideDown ? 1 : -1));
        pose.rotate(180f * (float) (Math.PI / 180.0));

        // scissors
        Vector2f pos = pose.transformPosition(new Vector2f());

        int x1 = (int) pos.x;
        int y1 = (int) pos.y;
        int x2 = (int) pos.x + size;
        int y2 = (int) pos.y + size;

        gui.pose().pushMatrix();
        gui.pose().identity();
        gui.enableScissor(x1, y1, x2, y2);
        gui.pose().popMatrix();


        // setup render
        pose.translate((float)(4d / 16d), (float) (upsideDown ? 0 : (8d / 16d)));


        FiguraPortraitRenderState state = new FiguraPortraitRenderState(this, fallback, modelScale, upsideDown, x1, y1, x2, y2, size, ((GuiGraphicsAccessor)gui).figura$getScissorStack().peek());
        gui.fill(x1, y1, x2, y2, -1);
        ((GuiGraphicsAccessor)gui).figura$getRenderState().addPicturesInPictureState(state);
        gui.pose().popMatrix();

        gui.disableScissor();

        // return
        return true;
    }

    public boolean renderHeadForPortrait(MultiBufferSource.BufferSource buffer, PoseStack stack, int light, float modelScale, boolean upsideDown) {
        stack.pushPose();
        stack.scale(2, 2, 2); // i have no clue why it's exactly 2x smaller than it should be
        //stack.scale(modelScale, modelScale * (upsideDown ? 1 : -1), modelScale);
        renderer.allowPivotParts = false;

        UIHelper.paperdoll = true;
        UIHelper.dollScale = 16f;

        renderer.setupRenderer(
                PartFilterScheme.PORTRAIT, buffer, stack,
                1f, light, 1f, OverlayTexture.NO_OVERLAY,
                false, false
        );

        // render
        int comp = renderer.renderSpecialParts();
        boolean ret = comp > 0 || headRender(stack, buffer, light, false);

        // after render
        stack.popPose();
        buffer.endBatch();
        UIHelper.paperdoll = false;

        renderer.allowPivotParts = true;

        // return
        return ret;
    }

    public boolean renderArrow(PoseStack stack, MultiBufferSource bufferSource, float delta, int light) {
        if (renderer == null || !loaded)
            return false;

        stack.pushPose();
        Quaternionf quaternionf = Axis.XP.rotationDegrees(135f);
        Quaternionf quaternionf2 = Axis.YP.rotationDegrees(-90f);
        quaternionf.mul(quaternionf2);
        stack.mulPose(quaternionf);

        renderer.setupRenderer(
                PartFilterScheme.ARROW, bufferSource, stack,
                delta, light, 1f, OverlayTexture.NO_OVERLAY,
                false, false
        );

        int comp = renderer.renderSpecialParts();

        stack.popPose();
        return comp > 0;
    }

    public boolean renderTrident(PoseStack stack, MultiBufferSource bufferSource, float delta, int light) {
        if (renderer == null || !loaded)
            return false;

        stack.pushPose();
        Quaternionf quaternionf = Axis.ZP.rotationDegrees(90f);
        Quaternionf quaternionf2 = Axis.YP.rotationDegrees(90f);
        quaternionf.mul(quaternionf2);
        stack.mulPose(quaternionf);

        renderer.setupRenderer(
                PartFilterScheme.TRIDENT, bufferSource, stack,
                delta, light, 1f, OverlayTexture.NO_OVERLAY,
                false, false
        );

        int comp = renderer.renderSpecialParts();

        stack.popPose();
        return comp > 0;
    }

    public boolean isItemPart(FiguraModelPart modelPart) {
        return renderer != null && loaded && modelPart.parentType == ParentType.Item;
    }

    public boolean renderItem(PoseStack stack, MultiBufferSource bufferSource, FiguraModelPart part, int light, int overlay) {
        if (!isItemPart(part))
            return false;

        stack.pushPose();
        stack.mulPose(Axis.ZP.rotationDegrees(180f));

        renderer.setupRenderer(
                PartFilterScheme.ITEM, bufferSource, stack,
                1f, light, 1f, overlay,
                false, false
        );

        renderer.itemToRender = part;

        int ret = renderer.renderSpecialParts();

        stack.popPose();
        return ret > 0;
    }

    public boolean figuraItemRendered(FiguraModelPart part) {
        if (!isItemPart(part))
            return false;
        // save current filter scheme, set it to item, get complexity, and then set it back
        PartFilterScheme partFilterScheme = renderer.currentFilterScheme;
        renderer.currentFilterScheme = PartFilterScheme.ITEM;
        renderer.itemToRender = part;
        int ret = renderer.getComplexity();
        renderer.currentFilterScheme = partFilterScheme;

        return ret > 0;
    }

    private static final PartCustomization PIVOT_PART_RENDERING_CUSTOMIZATION = new PartCustomization();
    public synchronized boolean pivotPartRender(ParentType parent, Consumer<PoseStack> consumer) {
        if (renderer == null || !loaded || !parent.isPivot)
            return false;

        Queue<Pair<FiguraMat4, FiguraMat3>> queue = renderer.pivotCustomizations.computeIfAbsent(parent, p -> new ConcurrentLinkedQueue<>());

        if (queue.isEmpty())
            return false;

        int i = 0;
        while (!queue.isEmpty() && i++ < 1000) { // limit of 1000 pivot part renders, just in case something goes infinitely somehow
            Pair<FiguraMat4, FiguraMat3> matrixPair = queue.poll();
            PIVOT_PART_RENDERING_CUSTOMIZATION.setPositionMatrix(matrixPair.getFirst());
            PIVOT_PART_RENDERING_CUSTOMIZATION.setNormalMatrix(matrixPair.getSecond());
            PIVOT_PART_RENDERING_CUSTOMIZATION.needsMatrixRecalculation = false;
            PoseStack stack = PIVOT_PART_RENDERING_CUSTOMIZATION.copyIntoGlobalPoseStack();
            consumer.accept(stack);
        }

        queue.clear();
        return true;
    }

    public void updateMatrices(EntityModel<?> entityModel, PoseStack stack) {
        if (renderer == null || !loaded)
            return;

        FiguraMod.pushProfiler(FiguraMod.MOD_ID);
        FiguraMod.pushProfiler(this);
        FiguraMod.pushProfiler("updateMatrices");

        renderer.vanillaModelData.update(entityModel);
        renderer.currentFilterScheme = PartFilterScheme.MODEL;
        renderer.setMatrices(stack);
        renderer.updateMatrices();

        FiguraMod.popProfiler(3);
    }


    // -- animations -- // 


    public void applyAnimations() {
        if (!loaded || scriptError)
            return;

        animation.reset(permissions.get(Permissions.ANIMATION_INST));

        // Populate Molang query context from the current game state
        if (molangContext != null) {
            Entity entity = getEntity();
            if (entity != null) {
                molangContext.populateQueryContext(entity);
            }
        }

        int animationsLimit = permissions.get(Permissions.BB_ANIMATIONS);
        int limit = animationsLimit;
        for (Animation animation : animations.values())
            limit = AnimationPlayer.tick(animation, limit);
        animationComplexity = animationsLimit - limit;

        if (limit <= 0) {
            noPermissions.add(Permissions.BB_ANIMATIONS);
        } else {
            noPermissions.remove(Permissions.BB_ANIMATIONS);
        }
    }

    /**
     * Gets the Minecraft Entity associated with this avatar.
     * Tries to find the entity by owner UUID, falls back to the local player.
     */
    private Entity getEntity() {
        if (owner != null) {
            var mc = Minecraft.getInstance();
            if (mc.level != null) {
                Entity entity = mc.level.getPlayerByUUID(owner);
                if (entity != null) return entity;
            }
        }
        return null;
    }

    public void clearAnimations() {
        if (!loaded || scriptError)
            return;

        for (Animation animation : animations.values())
            AnimationPlayer.clear(animation);
    }

    // -- functions -- // 

    /**
     * We should call this whenever an avatar is no longer reachable!
     * It free()s all the CachedType used inside of the avatar, and also
     * closes the native texture resources.
     * also closes and stops this avatar sounds
     */
    public void clean() {
        if (renderer != null)
            renderer.invalidate();
        if (ysmRuntime != null) {
            ysmRuntime.close();
            ysmRuntime = null;
        }

        clearSounds();
        clearParticles();
        closeBuffers();
        closeStreams();

        events.clear();
    }

    public boolean isYsmNative() {
        return ysmRuntime != null;
    }

    public YsmModelRuntime getYsmRuntime() {
        return ysmRuntime;
    }

    public void clearSounds() {
        SoundAPI.getSoundEngine().figura$stopSound(owner, null);
        if (SoundAPI.getSoundEngine().figura$isEngineActive()) {
            for (SoundBuffer value : customSounds.values())
                value.releaseAlBuffer();
        }
    }

    public void closeBuffers() {
        for (FiguraBuffer buffer :
                openBuffers) {
            if (!buffer.isClosed()) {
                try {
                    buffer.baseClose();
                } catch (Exception ignored) {}
            }
        }
        openBuffers.clear();
    }

    public void closeStreams() {
        for (FiguraInputStream stream :
                new ArrayList<>(openInputStreams)) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
        openInputStreams.clear();

        for (FiguraOutputStream stream :
                new ArrayList<>(openOutputStreams)) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
        openOutputStreams.clear();
    }

    public void clearParticles() {
        ParticleAPI.getParticleEngine().figura$clearParticles(owner);
    }

    private int getFileSize() {
        try {
            // get size
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(nbt, baos);
            return baos.size();
        } catch (Exception e) {
            FiguraMod.LOGGER.warn("Failed to generate file size for model " + this.name, e);
            return 0;
        }
    }

    private int getVersionStatus() {
        if (version == null || (NetworkStuff.latestVersion != null && version.compareTo(NetworkStuff.latestVersion) > 0))
            return 0;
        return version.compareTo(FiguraMod.VERSION);
    }

    // -- loading -- // 

    private void createLuaRuntime() {
        if (!nbt.contains("scripts"))
            return;

        Map<String, String> scripts = new HashMap<>();
        CompoundTag scriptsNbt = nbt.getCompoundOrEmpty("scripts");
        for (String s : scriptsNbt.keySet())
            scripts.put(PathUtils.computeSafeString(s), new String(scriptsNbt.getByteArray(s).orElse(new byte[0]), StandardCharsets.UTF_8));

        CompoundTag metadata = nbt.getCompoundOrEmpty("metadata");

        ListTag autoScripts;
        if (metadata.contains("autoScripts"))
            autoScripts = metadata.getListOrEmpty("autoScripts");
        else
            autoScripts = null;

        FiguraLuaRuntime runtime = new FiguraLuaRuntime(this, scripts);
        if (renderer != null && renderer.root != null)
            runtime.setGlobal("models", renderer.root);

        init.reset(permissions.get(Permissions.INIT_INST));
        runtime.setInstructionLimit(init.remaining);

        events.offer(() -> {
            if (runtime.init(autoScripts))
                init.use(runtime.getInstructions());
        });
    }

    private void loadAnimations() {
        if (!nbt.contains("animations"))
            return;

        // Initialize Molang context
        this.molangContext = new MolangContext();

        ArrayList<String> autoAnims = new ArrayList<>();
        CompoundTag metadata = nbt.getCompoundOrEmpty("metadata");
        if (metadata.contains("autoAnims")) {
            for (Tag name : metadata.getListOrEmpty("autoAnims"))
                autoAnims.add(name.asString().orElse(""));
        }

        ListTag root = nbt.getListOrEmpty("animations");
        for (int i = 0; i < root.size(); i++) {
            try {
                CompoundTag animNbt = root.getCompoundOrEmpty(i);

                if (!animNbt.contains("mdl") || !animNbt.contains("name"))
                    continue;

                String mdl = animNbt.getStringOr("mdl", "");
                String name = animNbt.getStringOr("name", "");
                Animation.LoopMode loop = Animation.LoopMode.ONCE;
                if (animNbt.contains("loop")) {
                    try {
                        loop = Animation.LoopMode.valueOf(animNbt.getStringOr("loop", "").toUpperCase(Locale.US));
                    } catch (Exception ignored) {}
                }

                Animation animation = new Animation(this,
                        mdl, name, loop,
                        animNbt.contains("ovr") && animNbt.getBooleanOr("ovr", false),
                        animNbt.contains("len") ? animNbt.getFloatOr("len", 0.0f) : 0f,
                        animNbt.contains("off") ? animNbt.getFloatOr("off", 0.0f) : 0f,
                        animNbt.contains("bld") ? animNbt.getFloatOr("bld", 0.0f) : 1f,
                        animNbt.contains("sdel") ? animNbt.getFloatOr("sdel", 0.0f) : 0f,
                        animNbt.contains("ldel") ? animNbt.getFloatOr("ldel", 0.0f) : 0f
                );

                // Parse Molang expression strings from NBT (keys: mau, mbw, msd, mld)
                Expression molangOffsetAst = null;
                Expression molangBlendAst = null;
                Expression molangStartDelayAst = null;
                Expression molangLoopDelayAst = null;

                // Use per-avatar bindings for parsing to enable v/c/t variable resolution
                MolangBindings bindings = getAvatarBindings();
                if (animNbt.contains("mau")) {
                    try {
                        java.util.List<Expression> parsed = MolangEngine.fromCustomBinding(bindings).parse(animNbt.getStringOr("mau", ""));
                        if (!parsed.isEmpty()) molangOffsetAst = parsed.get(0);
                    } catch (Exception ignored) {}
                }
                if (animNbt.contains("mbw")) {
                    try {
                        java.util.List<Expression> parsed = MolangEngine.fromCustomBinding(bindings).parse(animNbt.getStringOr("mbw", ""));
                        if (!parsed.isEmpty()) molangBlendAst = parsed.get(0);
                    } catch (Exception ignored) {}
                }
                if (animNbt.contains("msd")) {
                    try {
                        java.util.List<Expression> parsed = MolangEngine.fromCustomBinding(bindings).parse(animNbt.getStringOr("msd", ""));
                        if (!parsed.isEmpty()) molangStartDelayAst = parsed.get(0);
                    } catch (Exception ignored) {}
                }
                if (animNbt.contains("mld")) {
                    try {
                        java.util.List<Expression> parsed = MolangEngine.fromCustomBinding(bindings).parse(animNbt.getStringOr("mld", ""));
                        if (!parsed.isEmpty()) molangLoopDelayAst = parsed.get(0);
                    } catch (Exception ignored) {}
                }

                if (molangOffsetAst != null || molangBlendAst != null
                        || molangStartDelayAst != null || molangLoopDelayAst != null) {
                    animation.setMolangAst(molangOffsetAst, molangBlendAst,
                            molangStartDelayAst, molangLoopDelayAst);
                    animation.setMolangEvaluator(ExpressionEvaluator.evaluator(molangContext));
                }

                if (animNbt.contains("code")) {
                    for (Tag code : animNbt.getListOrEmpty("code")) {
                        CompoundTag compound = (CompoundTag) code;
                        animation.newCode(compound.getFloatOr("time", 0.0f), compound.getStringOr("src", ""));
                    }
                }

                animations.put(i, animation);

                if (autoAnims.contains(mdl + "." + name))
                    animation.play();
            } catch (Exception ignored) {}
        }
    }

    private void loadCustomSounds() {
        if (!nbt.contains("sounds"))
            return;

        CompoundTag root = nbt.getCompoundOrEmpty("sounds");
        for (String key : root.keySet()) {
            try {
                loadSound(key, root.getByteArray(key).orElse(new byte[0]));
            } catch (Exception e) {
                FiguraMod.LOGGER.warn("Failed to load custom sound \"" + key + "\"", e);
            }
        }
    }

    public void loadSound(String name, byte[] data) throws Exception {
        if (SoundAPI.getSoundEngine().figura$isEngineActive()) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data); JOrbisAudioStream oggAudioStream = new JOrbisAudioStream(inputStream)) {
                SoundBuffer sound = new SoundBuffer(oggAudioStream.readAll(), oggAudioStream.getFormat());
                this.customSounds.put(name, sound);
            }
        } else {
            FiguraMod.LOGGER.error("Sound is not supported or enabled on this system but a custom sound tried to load anyway, scripts may break.");
        }
    }

    public FiguraTexture registerTexture(String name, NativeImage image, boolean ignoreSize) {
        int max = permissions.get(Permissions.TEXTURE_SIZE);
        if (!ignoreSize && (image.getWidth() > max || image.getHeight() > max)) {
            noPermissions.add(Permissions.TEXTURE_SIZE);
            throw new LuaError("Texture exceeded max size of " + max + " x " + max + " resolution, got " + image.getWidth() + " x " + image.getHeight());
        }

        FiguraTexture oldText = renderer.customTextures.get(name);
        if (oldText != null)
            oldText.close();

        if (renderer.customTextures.size() > TextureAPI.TEXTURE_LIMIT)
            throw new LuaError("Maximum amount of textures reached!");

        FiguraTexture texture = new FiguraTexture(this, name, image);
        renderer.customTextures.put(name, texture);
        return texture;
    }

    public static class Instructions {

        public int max, remaining;
        private int currPre, currPost;
        public int pre, post;
        private boolean inverted;

        public Instructions(int remaining) {
            reset(remaining);
        }

        public Instructions post() {
            inverted = true;
            return this;
        }

        public int getTotal() {
            return pre + post;
        }

        public void reset(int remaining) {
            this.max = this.remaining = remaining;
            currPre = currPost = 0;
        }

        public void use(int amount) {
            remaining -= amount;

            if (!inverted) {
                currPre += amount;
                pre = currPre;
            } else {
                currPost += amount;
                post = currPost;
                inverted = false;
            }
        }
    }
}
