package world.landfall.persona.features.landfalladdon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import world.landfall.persona.Persona;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.registry.PersonaEvents;

import java.util.UUID;

@EventBusSubscriber(modid = Persona.MODID)
public class SpawnPointHandler {

    @SubscribeEvent
    public static void onSetSpawnPoint(PlayerSetSpawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null) return;
        
        UUID activeId = data.getActiveCharacterId();
        if (activeId == null) return;
        
        CharacterProfile profile = data.getCharacter(activeId);
        if (profile == null) return;

        BlockPos pos = event.getNewSpawn();
        // If position is null, it means we're resetting to world spawn
        // In this case, we should remove the stored spawn point
        if (pos == null) {
            profile.removeModData(LandfallAddonData.DATA_KEY);
            return;
        }

        // Store spawn point in character data
        LandfallAddonData.setSpawnPoint(
            profile,
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            player.level().dimension().location(),
            event.isForced()
        );
    }

    @SubscribeEvent
    public static void onCharacterSwitch(PersonaEvents.CharacterSwitchEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        
        PlayerCharacterData data = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
        if (data == null) return;
        
        CharacterProfile profile = data.getCharacter(event.getToCharacterId());
        if (profile == null) return;

        // Apply stored spawn point if it exists, otherwise use world spawn
        if (LandfallAddonData.hasSpawnPoint(profile)) {
            GlobalPos spawnPos = LandfallAddonData.getSpawnPoint(profile);
            boolean forced = LandfallAddonData.isSpawnForced(profile);
            
            if (spawnPos != null) {
                player.setRespawnPosition(
                    spawnPos.dimension(),
                    spawnPos.pos(),
                    0.0F, // angle
                    forced,
                    false // shouldShowMessage
                );
            }
        } else {
            // Reset to world spawn by removing any existing respawn point
            player.setRespawnPosition(
                null, // null dimension = world spawn
                null, // null position = world spawn
                0.0F,
                false,
                false
            );
        }
    }
} 