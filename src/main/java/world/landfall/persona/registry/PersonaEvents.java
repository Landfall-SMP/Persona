package world.landfall.persona.registry;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import world.landfall.persona.Persona;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = Persona.MODID)
public class PersonaEvents {
    public static class CharacterCreateEvent extends Event {
        private final Player player;
        private final UUID characterId;
        private final CharacterProfile profile;
        
        public CharacterCreateEvent(Player player, UUID characterId, CharacterProfile profile) {
            this.player = player;
            this.characterId = characterId;
            this.profile = profile;
        }
        
        public Player getPlayer() { return player; }
        public UUID getCharacterId() { return characterId; }
        public CharacterProfile getProfile() { return profile; }
    }
    
    public static class CharacterPreSwitchEvent extends Event {
        private final Player player;
        private final UUID fromCharacterId;
        private final UUID toCharacterId;
        private final CompletableFuture<Void> ready;
        
        public CharacterPreSwitchEvent(Player player, UUID fromCharacterId, UUID toCharacterId) {
            this.player = player;
            this.fromCharacterId = fromCharacterId;
            this.toCharacterId = toCharacterId;
            this.ready = new CompletableFuture<>();
        }
        
        public Player getPlayer() { return player; }
        public UUID getFromCharacterId() { return fromCharacterId; }
        public UUID getToCharacterId() { return toCharacterId; }
        public CompletableFuture<Void> getReady() { return ready; }
    }
    
    public static class CharacterSwitchEvent extends Event {
        private final Player player;
        private final UUID fromCharacterId;
        private final UUID toCharacterId;
        
        public CharacterSwitchEvent(Player player, UUID fromCharacterId, UUID toCharacterId) {
            this.player = player;
            this.fromCharacterId = fromCharacterId;
            this.toCharacterId = toCharacterId;
        }
        
        public Player getPlayer() { return player; }
        public UUID getFromCharacterId() { return fromCharacterId; }
        public UUID getToCharacterId() { return toCharacterId; }
    }
    
    public static class CharacterDeleteEvent extends Event {
        private final Player player;
        private final UUID characterId;
        private boolean canceled = false;
        
        public CharacterDeleteEvent(Player player, UUID characterId) {
            this.player = player;
            this.characterId = characterId;
        }
        
        public Player getPlayer() { return player; }
        public UUID getCharacterId() { return characterId; }
        
        public void setCanceled(boolean canceled) {
            this.canceled = canceled;
        }
        
        public boolean isCanceled() {
            return this.canceled;
        }
    }
    
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerCharacterData data = serverPlayer.getData(PlayerCharacterCapability.CHARACTER_DATA);
            if (data != null) {
                GlobalCharacterRegistry.syncRegistry(serverPlayer);
            } else {
                Persona.LOGGER.warn("[Persona] Player {} missing character data on login.", serverPlayer.getName().getString());
            }
        } else if (player != null) {
            Persona.LOGGER.debug("[Persona] Non-ServerPlayer type ({}) logged in, character data: {}", 
                player.getClass().getSimpleName(), player.getData(PlayerCharacterCapability.CHARACTER_DATA) != null);
        }
    }
    
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player != null) {
            GlobalCharacterRegistry.removePlayerCharacters(player);
        } else {
            Persona.LOGGER.warn("[Persona] PlayerLoggedOutEvent with null player entity.");
        }
    }
} 