package world.landfall.persona.client.network;

import net.minecraft.client.Minecraft;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.registry.PersonaNetworking;

import java.util.function.Consumer;

/**
 * Manages character data synchronization between client and server.
 * Provides a centralized way to handle sync requests and callbacks.
 */
public class CharacterSyncManager {
    private static final int MAX_SYNC_ATTEMPTS = 20; // 1 second at 20 ticks per second
    private static final int SYNC_REQUEST_INTERVAL = 5; // Request sync every 5 ticks
    
    private int syncAttempts = 0;
    private final Minecraft minecraft;
    private final Consumer<Boolean> onSyncComplete;
    
    public CharacterSyncManager(Consumer<Boolean> onSyncComplete) {
        this.minecraft = Minecraft.getInstance();
        this.onSyncComplete = onSyncComplete;
    }
    
    /**
     * Starts the sync process.
     * @return true if sync was started, false if already syncing
     */
    public boolean startSync() {
        if (syncAttempts > 0) return false;
        syncAttempts = 1;
        PersonaNetworking.requestCharacterSync();
        return true;
    }
    
    /**
     * Should be called every tick to manage the sync process.
     */
    public void tick() {
        if (syncAttempts == 0) return;
        
        if (minecraft.player != null) {
            PlayerCharacterData data = minecraft.player.getData(PlayerCharacterCapability.CHARACTER_DATA);
            if (data != null && data.getCharacterCount() > 0) {
                // Sync successful
                syncAttempts = 0;
                onSyncComplete.accept(true);
                return;
            }
        }
        
        if (syncAttempts >= MAX_SYNC_ATTEMPTS) {
            // Sync failed
            syncAttempts = 0;
            onSyncComplete.accept(false);
            return;
        }
        
        syncAttempts++;
        if (syncAttempts % SYNC_REQUEST_INTERVAL == 0) {
            PersonaNetworking.requestCharacterSync();
        }
    }
    
    /**
     * @return true if currently syncing
     */
    public boolean isSyncing() {
        return syncAttempts > 0;
    }
} 