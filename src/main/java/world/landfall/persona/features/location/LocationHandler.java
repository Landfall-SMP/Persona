package world.landfall.persona.features.location;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.slf4j.Logger;
import world.landfall.persona.Persona;
import world.landfall.persona.registry.PersonaEvents;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.config.Config;

import java.util.UUID;

@EventBusSubscriber(modid = Persona.MODID)
public class LocationHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation LOCATION_KEY = ResourceLocation.fromNamespaceAndPath(Persona.MODID, "location");

    static {
        LOGGER.debug("LocationHandler loaded for Persona.");
    }

    @SubscribeEvent
    public static void onCreate(PersonaEvents.CharacterCreateEvent event) {
        if (!Config.ENABLE_LOCATION_SYSTEM.get()) {
            return;
        }
        try {
            LOGGER.debug("[LocationHandler] Create event for player: {}, character: {}",
                event.getPlayer().getName().getString(), event.getCharacterId());

            CharacterProfile profile = event.getProfile();
            if (profile != null) {
                // Initialize with an empty tag for location data
                profile.setModData(LOCATION_KEY, new CompoundTag());
                LOGGER.debug("[LocationHandler] Initialized empty location data for character: {}", event.getCharacterId());
            } else {
                LOGGER.warn("[LocationHandler] CharacterProfile is null for character: {}. Cannot initialize location data.", event.getCharacterId());
            }
        } catch (Exception e) {
            LOGGER.error("[LocationHandler] Error in CharacterCreateEvent handler", e);
        }
    }

    @SubscribeEvent
    public static void onPreSwitch(PersonaEvents.CharacterPreSwitchEvent event) {
        if (!Config.ENABLE_LOCATION_SYSTEM.get()) {
            event.getReady().complete(null);
            return;
        }
        ServerPlayer player;
        UUID playerId;

        try {
            if (!(event.getPlayer() instanceof ServerPlayer serverPlayer)) {
                LOGGER.warn("[LocationHandler] Player is not a ServerPlayer, skipping location save.");
                event.getReady().complete(null);
                return;
            }
            player = serverPlayer;
            playerId = player.getUUID();
            UUID fromCharacterId = event.getFromCharacterId();

            LOGGER.debug("[LocationHandler] PreSwitch event for player: {} (ID: {}), from character: {}",
                player.getName().getString(), playerId, fromCharacterId);

            if (fromCharacterId != null) {
                PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
                if (characterData == null) {
                    LOGGER.error("[LocationHandler] PlayerCharacterData is null for player {}. Cannot save location.", playerId);
                    event.getReady().complete(null);
                    return;
                }
                CharacterProfile fromProfile = characterData.getCharacter(fromCharacterId);
                if (fromProfile != null) {
                    fromProfile.setModData(LOCATION_KEY, saveLocation(player));
                    // Save the character to file to persist the location data
                    world.landfall.persona.data.CharacterFileStorage.saveCharacter(fromProfile);
                    LOGGER.debug("[LocationHandler] Saved location for character {}. Player: {}", fromCharacterId, playerId);
                } else {
                    LOGGER.warn("[LocationHandler] 'From' CharacterProfile is null for character: {}. Cannot save location.", fromCharacterId);
                }
            } else {
                LOGGER.debug("[LocationHandler] No 'from' character ID, nothing to save for location.");
            }
        } catch (Exception e) {
            LOGGER.error("[LocationHandler] Error in PreSwitch event handler for location", e);
        } finally {
            event.getReady().complete(null); // Ensure the future is always completed
        }
    }

    @SubscribeEvent
    public static void onSwitch(PersonaEvents.CharacterSwitchEvent event) {
        if (!Config.ENABLE_LOCATION_SYSTEM.get()) {
            return;
        }
        ServerPlayer player;
        UUID playerId;

        try {
            if (!(event.getPlayer() instanceof ServerPlayer serverPlayer)) {
                LOGGER.warn("[LocationHandler] Player is not a ServerPlayer, skipping location load.");
                return;
            }
            player = serverPlayer;
            playerId = player.getUUID();
            UUID toCharacterId = event.getToCharacterId();

            LOGGER.debug("[LocationHandler] Switch event for player: {} (ID: {}), to character: {}",
                player.getName().getString(), playerId, toCharacterId);

            if (toCharacterId != null) {
                PlayerCharacterData characterData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
                 if (characterData == null) {
                    LOGGER.error("[LocationHandler] PlayerCharacterData is null for player {}. Cannot load location.", playerId);
                    return;
                }
                CharacterProfile toProfile = characterData.getCharacter(toCharacterId);
                if (toProfile != null) {
                    CompoundTag locationTag = toProfile.getModData(LOCATION_KEY);
                    if (locationTag != null && !locationTag.isEmpty()) {
                        loadLocation(player, locationTag);
                        LOGGER.debug("[LocationHandler] Loaded location for character {}. Player: {}", toCharacterId, playerId);
                    } else {
                        LOGGER.debug("[LocationHandler] No location data found for character {}, player remains at current location. Player: {}", toCharacterId, playerId);
                        // If no location data, player stays where they are. Could also TP to spawn or a default location if desired.
                    }
                } else {
                    LOGGER.warn("[LocationHandler] 'To' CharacterProfile is null for character: {}. Cannot load location.", toCharacterId);
                }
            } else {
                LOGGER.warn("[LocationHandler] 'To' character ID is null. Cannot load location.");
            }
        } catch (Exception e) {
            LOGGER.error("[LocationHandler] Error in Switch event handler for location", e);
        }
    }

    public static CompoundTag saveLocation(ServerPlayer player) {
        CompoundTag locationTag = new CompoundTag();
        locationTag.putDouble("x", player.getX());
        locationTag.putDouble("y", player.getY());
        locationTag.putDouble("z", player.getZ());
        locationTag.putFloat("yaw", player.getYRot());
        locationTag.putFloat("pitch", player.getXRot());
        locationTag.putString("dimension", player.level().dimension().location().toString());
        LOGGER.debug("[LocationHandler] Saved location: Dim={}, X={}, Y={}, Z={} for player {}",
            locationTag.getString("dimension"), locationTag.getDouble("x"), locationTag.getDouble("y"), locationTag.getDouble("z"), player.getName().getString());
        return locationTag;
    }

    private static void loadLocation(ServerPlayer player, CompoundTag locationTag) {
        double x = locationTag.getDouble("x");
        double y = locationTag.getDouble("y");
        double z = locationTag.getDouble("z");
        float yaw = locationTag.getFloat("yaw");
        float pitch = locationTag.getFloat("pitch");
        ResourceLocation dimensionKey = ResourceLocation.parse(locationTag.getString("dimension"));

        ServerLevel targetLevel = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimensionKey));

        if (targetLevel == null) {
            LOGGER.warn("[LocationHandler] Dimension {} not found for player {}. Player will remain in current dimension {}. Attempting to teleport to coordinates in current dimension.",
                         dimensionKey, player.getName().getString(), player.level().dimension().location());
            targetLevel = player.serverLevel();
        }

        if (player.level() == targetLevel) {
            player.teleportTo(x, y, z); // Teleport within the same dimension - coordinates only
            player.setYRot(yaw);      // Set rotation separately
            player.setXRot(pitch);
            LOGGER.debug("[LocationHandler] Teleported player {} to X={}, Y={}, Z={}, Yaw={}, Pitch={} in current dimension {}",
                player.getName().getString(), x, y, z, yaw, pitch, targetLevel.dimension().location());
        } else {
            player.teleportTo(targetLevel, x, y, z, yaw, pitch); // Teleport to a different dimension with rotation
            LOGGER.debug("[LocationHandler] Teleported player {} to X={}, Y={}, Z={}, Yaw={}, Pitch={} in dimension {}",
                player.getName().getString(), x, y, z, yaw, pitch, targetLevel.dimension().location());
        }
    }
} 