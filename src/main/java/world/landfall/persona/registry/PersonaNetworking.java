package world.landfall.persona.registry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import world.landfall.persona.Persona;
import world.landfall.persona.command.CommandRegistry;
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.data.PlayerCharacterCapability;
import world.landfall.persona.config.Config;
import world.landfall.persona.config.ClientSyncedConfig;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@EventBusSubscriber(modid = Persona.MODID, bus = EventBusSubscriber.Bus.MOD)
public class PersonaNetworking {
    private static final ResourceLocation SYNC_TO_CLIENT_ID = ResourceLocation.tryParse(Persona.MODID + ":sync_to_client");
    private static final ResourceLocation SYNC_TO_SERVER_ID = ResourceLocation.tryParse(Persona.MODID + ":sync_to_server");
    private static final ResourceLocation ACTION_ID = ResourceLocation.tryParse(Persona.MODID + ":character_action");
    private static final ResourceLocation SYNC_REQUEST_ID = ResourceLocation.tryParse(Persona.MODID + ":sync_request");
    private static final ResourceLocation CREATION_RESPONSE_ID = ResourceLocation.tryParse(Persona.MODID + ":creation_response");
    private static final ResourceLocation CREATE_WITH_MODDATA_ID = ResourceLocation.tryParse(Persona.MODID + ":create_with_moddata");
    private static final ResourceLocation SERVER_CONFIG_SYNC_ID = ResourceLocation.tryParse(Persona.MODID + ":server_config_sync");
    
    private static CustomPacketPayload.Type<SyncToClientPayload> SYNC_TO_CLIENT_TYPE = null;
    private static CustomPacketPayload.Type<SyncToServerPayload> SYNC_TO_SERVER_TYPE = null;
    private static CustomPacketPayload.Type<CharacterActionPayload> ACTION_PACKET_TYPE = null;
    private static CustomPacketPayload.Type<SyncRequestPayload> SYNC_REQUEST_TYPE = null;
    private static CustomPacketPayload.Type<CharacterCreationResponsePayload> CREATION_RESPONSE_TYPE = null;
    private static CustomPacketPayload.Type<CharacterCreateWithModDataPayload> CREATE_WITH_MODDATA_TYPE = null;
    private static CustomPacketPayload.Type<ServerConfigSyncPayload> SERVER_CONFIG_SYNC_TYPE = null;
    
    private static StreamCodec<RegistryFriendlyByteBuf, SyncToClientPayload> SYNC_TO_CLIENT_CODEC = null;
    private static StreamCodec<RegistryFriendlyByteBuf, SyncToServerPayload> SYNC_TO_SERVER_CODEC = null;
    private static StreamCodec<RegistryFriendlyByteBuf, CharacterActionPayload> ACTION_PACKET_CODEC = null;
    private static StreamCodec<RegistryFriendlyByteBuf, SyncRequestPayload> SYNC_REQUEST_CODEC = null;
    private static StreamCodec<RegistryFriendlyByteBuf, CharacterCreationResponsePayload> CREATION_RESPONSE_CODEC = null;
    private static StreamCodec<RegistryFriendlyByteBuf, CharacterCreateWithModDataPayload> CREATE_WITH_MODDATA_CODEC = null;
    private static StreamCodec<RegistryFriendlyByteBuf, ServerConfigSyncPayload> SERVER_CONFIG_SYNC_CODEC = null;
    
    public enum Action {
        CREATE,
        SWITCH,
        DELETE
    }
    
    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        if (SYNC_TO_CLIENT_TYPE != null || SYNC_TO_SERVER_TYPE != null || ACTION_PACKET_TYPE != null || 
            CREATION_RESPONSE_TYPE != null || CREATE_WITH_MODDATA_TYPE != null || SERVER_CONFIG_SYNC_TYPE != null) {
            Persona.LOGGER.warn("[Persona] Attempted to register payload handlers more than once. Skipping.");
            return;
        }
        
        try {
            final PayloadRegistrar registrar = event.registrar(Persona.MODID);
            
            // Register sync to client packet
            SYNC_TO_CLIENT_TYPE = new CustomPacketPayload.Type<>(SYNC_TO_CLIENT_ID);
            SYNC_TO_CLIENT_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf),
                SyncToClientPayload::new
            );
            registrar.playToClient(SYNC_TO_CLIENT_TYPE, SYNC_TO_CLIENT_CODEC, SyncToClientPayload.Handler::handleClientPacket);
            
            // Register sync to server packet
            SYNC_TO_SERVER_TYPE = new CustomPacketPayload.Type<>(SYNC_TO_SERVER_ID);
            SYNC_TO_SERVER_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf),
                SyncToServerPayload::new
            );
            registrar.playToServer(SYNC_TO_SERVER_TYPE, SYNC_TO_SERVER_CODEC, SyncToServerPayload.Handler::handleServerPacket);
            
            // Register action packet
            ACTION_PACKET_TYPE = new CustomPacketPayload.Type<>(ACTION_ID);
            ACTION_PACKET_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf),
                CharacterActionPayload::new
            );
            registrar.playToServer(ACTION_PACKET_TYPE, ACTION_PACKET_CODEC, CharacterActionPayload.Handler::handleServerPacket);
            
            // Register create with modData packet
            CREATE_WITH_MODDATA_TYPE = new CustomPacketPayload.Type<>(CREATE_WITH_MODDATA_ID);
            CREATE_WITH_MODDATA_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf),
                CharacterCreateWithModDataPayload::new
            );
            registrar.playToServer(CREATE_WITH_MODDATA_TYPE, CREATE_WITH_MODDATA_CODEC, CharacterCreateWithModDataPayload.Handler::handleServerPacket);
            
            // Register sync request packet
            SYNC_REQUEST_TYPE = new CustomPacketPayload.Type<>(SYNC_REQUEST_ID);
            SYNC_REQUEST_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf),
                SyncRequestPayload::new
            );
            registrar.playToServer(SYNC_REQUEST_TYPE, SYNC_REQUEST_CODEC, SyncRequestPayload.Handler::handleServerPacket);
            
            // Register creation response packet (Server to Client)
            CREATION_RESPONSE_TYPE = new CustomPacketPayload.Type<>(CREATION_RESPONSE_ID);
            CREATION_RESPONSE_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf),
                CharacterCreationResponsePayload::new
            );
            registrar.playToClient(CREATION_RESPONSE_TYPE, CREATION_RESPONSE_CODEC, CharacterCreationResponsePayload.Handler::handleClientPacket);
            
            // Register server config sync packet (Server to Client)
            SERVER_CONFIG_SYNC_TYPE = new CustomPacketPayload.Type<>(SERVER_CONFIG_SYNC_ID);
            SERVER_CONFIG_SYNC_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf),
                ServerConfigSyncPayload::new
            );
            registrar.playToClient(SERVER_CONFIG_SYNC_TYPE, SERVER_CONFIG_SYNC_CODEC, ServerConfigSyncPayload.Handler::handleClientPacket);
            
            Persona.LOGGER.debug("[Persona] Payload handlers registered successfully.");
        } catch (Exception e) {
            Persona.LOGGER.error("[Persona] Failed to register payload handlers", e);
            SYNC_TO_CLIENT_TYPE = null;
            SYNC_TO_CLIENT_CODEC = null;
            SYNC_TO_SERVER_TYPE = null;
            SYNC_TO_SERVER_CODEC = null;
            ACTION_PACKET_TYPE = null;
            ACTION_PACKET_CODEC = null;
            SYNC_REQUEST_TYPE = null;
            SYNC_REQUEST_CODEC = null;
            CREATION_RESPONSE_TYPE = null;
            CREATION_RESPONSE_CODEC = null;
            CREATE_WITH_MODDATA_TYPE = null;
            CREATE_WITH_MODDATA_CODEC = null;
            SERVER_CONFIG_SYNC_TYPE = null;
            SERVER_CONFIG_SYNC_CODEC = null;
        }
    }
    
    public static record SyncToClientPayload(PlayerCharacterData data) implements CustomPacketPayload {
        public SyncToClientPayload(RegistryFriendlyByteBuf buf) {
            this(PlayerCharacterData.deserialize(buf.readNbt()));
        }
        
        public void write(FriendlyByteBuf buf) { 
            buf.writeNbt(data.serialize());
        }
        
        @Override
        public Type<SyncToClientPayload> type() {
            if (SYNC_TO_CLIENT_TYPE == null) {
                throw new IllegalStateException("Attempted to use SYNC_TO_CLIENT_TYPE before it was initialized");
            }
            return SYNC_TO_CLIENT_TYPE;
        }

        public static class Handler {
            public static void handleClientPacket(final SyncToClientPayload payload, final IPayloadContext context) {
                Optional.ofNullable(context.player()).ifPresent(player -> {
                    PlayerCharacterData currentData = player.getData(PlayerCharacterCapability.CHARACTER_DATA);
                    if (currentData != null) {
                        currentData.copyFrom(payload.data());
                    }
                });
            }
        }
    }
    
    public static record SyncToServerPayload(PlayerCharacterData data) implements CustomPacketPayload {
        public SyncToServerPayload(RegistryFriendlyByteBuf buf) {
            this(PlayerCharacterData.deserialize(buf.readNbt()));
        }
        
        public void write(FriendlyByteBuf buf) { 
            buf.writeNbt(data.serialize());
        }
        
        @Override
        public Type<SyncToServerPayload> type() {
            if (SYNC_TO_SERVER_TYPE == null) {
                throw new IllegalStateException("Attempted to use SYNC_TO_SERVER_TYPE before it was initialized");
            }
            return SYNC_TO_SERVER_TYPE;
        }

        public static class Handler {
            public static void handleServerPacket(final SyncToServerPayload payload, final IPayloadContext context) {
                Optional.ofNullable(context.player()).ifPresent(player -> {
                    if (player instanceof ServerPlayer serverPlayer) {
                        PlayerCharacterData currentData = serverPlayer.getData(PlayerCharacterCapability.CHARACTER_DATA);
                        if (currentData != null) {
                            currentData.setActiveCharacterId(payload.data().getActiveCharacterId());
                            GlobalCharacterRegistry.syncRegistry(serverPlayer);
                        }
                    }
                });
            }
        }
    }
    
    public static record CharacterActionPayload(Action action, String data, int startingAge, boolean fromGui) implements CustomPacketPayload {
        public CharacterActionPayload(RegistryFriendlyByteBuf buf) {
            this(Action.values()[buf.readByte()], buf.readUtf(), buf.readInt(), buf.readBoolean());
        }
        
        public void write(FriendlyByteBuf buf) {
            buf.writeByte(action.ordinal());
            buf.writeUtf(data);
            buf.writeInt(startingAge);
            buf.writeBoolean(fromGui);
        }
        
        @Override
        public Type<CharacterActionPayload> type() {
            if (ACTION_PACKET_TYPE == null) {
                throw new IllegalStateException("Attempted to use ACTION_PACKET_TYPE before it was initialized");
            }
            return ACTION_PACKET_TYPE;
        }

        public static class Handler {
            public static void handleServerPacket(final CharacterActionPayload payload, final IPayloadContext context) {
                Optional.ofNullable(context.player()).ifPresent(player -> {
                    if (player instanceof ServerPlayer serverPlayer) {
                        switch (payload.action()) {
                            case CREATE -> {
                                try {
                                    CommandRegistry.createCharacter(serverPlayer, payload.data(), payload.fromGui());
                                } catch (Exception e) {
                                    Persona.LOGGER.error("[Persona] Failed to create character via network action", e);
                                    PersonaNetworking.sendCreationResponseToPlayer(serverPlayer, false, "gui.persona.error.generic_creation_fail");
                                }
                            }
                            case SWITCH -> {
                                try {
                                    CommandRegistry.switchCharacter(serverPlayer, payload.data(), payload.fromGui());
                                } catch (Exception e) {
                                    Persona.LOGGER.error("[Persona] Failed to switch character", e);
                                }
                            }
                            case DELETE -> {
                                try {
                                    CommandRegistry.deleteCharacter(serverPlayer, payload.data(), payload.fromGui());
                                } catch (Exception e) {
                                    Persona.LOGGER.error("[Persona] Failed to delete character", e);
                                }
                            }
                        }
                    }
                });
            }
        }
    }
    
    public static record SyncRequestPayload() implements CustomPacketPayload {
        public SyncRequestPayload(RegistryFriendlyByteBuf buf) {
            this();
        }
        
        public void write(FriendlyByteBuf buf) {
            // No data to write
        }
        
        @Override
        public Type<SyncRequestPayload> type() {
            if (SYNC_REQUEST_TYPE == null) {
                throw new IllegalStateException("Attempted to use SYNC_REQUEST_TYPE before it was initialized");
            }
            return SYNC_REQUEST_TYPE;
        }

        public static class Handler {
            public static void handleServerPacket(final SyncRequestPayload payload, final IPayloadContext context) {
                Optional.ofNullable(context.player()).ifPresent(player -> {
                    if (player instanceof ServerPlayer serverPlayer) {
                        // Get player's character data and send it back to the client
                        GlobalCharacterRegistry.syncRegistry(serverPlayer);
                    }
                });
            }
        }
    }
    
    public static record CharacterCreationResponsePayload(boolean success, String messageKey, String[] messageArgs) implements CustomPacketPayload {
        public CharacterCreationResponsePayload(RegistryFriendlyByteBuf buf) {
            this(buf.readBoolean(), buf.readUtf(), buf.readArray(String[]::new, FriendlyByteBuf::readUtf));
        }
        
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(success);
            buf.writeUtf(messageKey);
            buf.writeArray(messageArgs, FriendlyByteBuf::writeUtf);
        }
        
        @Override
        public Type<CharacterCreationResponsePayload> type() {
            if (CREATION_RESPONSE_TYPE == null) {
                throw new IllegalStateException("Attempted to use CREATION_RESPONSE_TYPE before it was initialized");
            }
            return CREATION_RESPONSE_TYPE;
        }

        public static class Handler {
            public static void handleClientPacket(final CharacterCreationResponsePayload payload, final IPayloadContext context) {
                context.enqueueWork(() -> {
                    try {
                        // Route to appropriate handler based on message key
                        String messageKey = payload.messageKey();
                        if (messageKey != null && (messageKey.contains("switch") || messageKey.contains("cooldown") || messageKey.contains("already_active"))) {
                            // This is a character switch response
                            world.landfall.persona.client.network.ClientNetworkHandler
                                .handleCharacterSwitchResponse(payload.success(), payload.messageKey(), payload.messageArgs());
                        } else {
                            // This is a character creation response
                            world.landfall.persona.client.network.ClientNetworkHandler
                                .handleCharacterCreationResponse(payload.success(), payload.messageKey(), payload.messageArgs());
                        }
                    } catch (Exception e) {
                        // Log error but don't crash the client
                        System.err.println("[PersonaNetworking] Error handling character response: " + e.getMessage());
                    }
                });
            }
        }
    }
    
    public static void sendToPlayer(PlayerCharacterData data, ServerPlayer player) {
        if (SYNC_TO_CLIENT_TYPE == null) {
            Persona.LOGGER.error("[Persona] Cannot send packet, network not initialized");
            return;
        }
        PacketDistributor.sendToPlayer(player, new SyncToClientPayload(data));
    }
    
    public static void sendToServer(PlayerCharacterData data) {
        if (SYNC_TO_SERVER_TYPE == null) {
            Persona.LOGGER.error("[Persona] Cannot send packet, network not initialized");
            return;
        }
        PacketDistributor.sendToServer(new SyncToServerPayload(data));
    }
    
    public static void sendActionToServer(Action action, String data) {
        sendActionToServer(action, data, false);
    }
    
    public static void sendActionToServer(Action action, String data, boolean fromGui) {
        sendActionToServer(action, data, -1, fromGui);
    }
    
    public static void sendActionToServer(Action action, String data, int startingAge, boolean fromGui) {
        if (ACTION_PACKET_TYPE == null || ACTION_PACKET_CODEC == null) {
            throw new IllegalStateException("Attempted to send action packet before packet types were initialized");
        }
        PacketDistributor.sendToServer(new CharacterActionPayload(action, data, startingAge, fromGui));
    }
    
    /**
     * Sends a request to the server to sync character data
     */
    public static void requestCharacterSync() {
        if (SYNC_REQUEST_TYPE == null) {
            Persona.LOGGER.error("[Persona] Cannot send sync request, network not initialized");
            return;
        }
        PacketDistributor.sendToServer(new SyncRequestPayload());
    }

    // New send method for the response
    public static void sendCreationResponseToPlayer(ServerPlayer player, boolean success, String messageKey, String... messageArgs) {
        if (CREATION_RESPONSE_TYPE == null) {
            Persona.LOGGER.error("[Persona] Cannot send creation response, network not initialized");
            return;
        }
        PacketDistributor.sendToPlayer(player, new CharacterCreationResponsePayload(success, messageKey, messageArgs));
    }

    // New payload for character creation with modData
    public static record CharacterCreateWithModDataPayload(String characterName, Map<ResourceLocation, CompoundTag> modData, boolean fromGui) implements CustomPacketPayload {
        public CharacterCreateWithModDataPayload(RegistryFriendlyByteBuf buf) {
            this(
                buf.readUtf(),
                readModDataMap(buf),
                buf.readBoolean()
            );
        }
        
        private static Map<ResourceLocation, CompoundTag> readModDataMap(RegistryFriendlyByteBuf buf) {
            int size = buf.readVarInt();
            Map<ResourceLocation, CompoundTag> modData = new HashMap<>(size);
            
            for (int i = 0; i < size; i++) {
                ResourceLocation key = buf.readResourceLocation();
                CompoundTag value = buf.readNbt();
                if (value != null) {
                    modData.put(key, value);
                }
            }
            
            return modData;
        }
        
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(characterName);
            
            // Write modData
            buf.writeVarInt(modData.size());
            modData.forEach((key, value) -> {
                buf.writeResourceLocation(key);
                buf.writeNbt(value);
            });
            
            buf.writeBoolean(fromGui);
        }
        
        @Override
        public Type<CharacterCreateWithModDataPayload> type() {
            if (CREATE_WITH_MODDATA_TYPE == null) {
                throw new IllegalStateException("Attempted to use CREATE_WITH_MODDATA_TYPE before it was initialized");
            }
            return CREATE_WITH_MODDATA_TYPE;
        }

        public static class Handler {
            public static void handleServerPacket(final CharacterCreateWithModDataPayload payload, final IPayloadContext context) {
                Optional.ofNullable(context.player()).ifPresent(player -> {
                    if (player instanceof ServerPlayer serverPlayer) {
                        try {
                            CommandRegistry.createCharacter(serverPlayer, payload.characterName(), payload.fromGui(), payload.modData());
                        } catch (Exception e) {
                            Persona.LOGGER.error("[Persona] Failed to create character with modData", e);
                            // Send failure response
                            PersonaNetworking.sendCreationResponseToPlayer(serverPlayer, false, "gui.persona.error.generic_creation_fail");
                        }
                    }
                });
            }
        }
    }
    
    /**
     * Sends a character creation request with modData from input providers.
     * @param characterName The name of the character to create
     * @param modData A map of ResourceLocation to CompoundTag containing provider-specific data
     * @param fromGui Whether this request came from the GUI
     */
    public static void sendCreateWithModData(String characterName, Map<ResourceLocation, CompoundTag> modData, boolean fromGui) {
        if (CREATE_WITH_MODDATA_TYPE == null || CREATE_WITH_MODDATA_CODEC == null) {
            throw new IllegalStateException("Attempted to send createWithModData packet before packet types were initialized");
        }
        PacketDistributor.sendToServer(new CharacterCreateWithModDataPayload(characterName, modData, fromGui));
    }

    // New payload for server config sync
    public static record ServerConfigSyncPayload(
        boolean isAgingEnabled,
        boolean isInventoryEnabled,
        boolean isLocationEnabled,
        boolean isLandfallAddonsEnabled
    ) implements CustomPacketPayload {
        public ServerConfigSyncPayload(RegistryFriendlyByteBuf buf) {
            this(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
        }
        
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(isAgingEnabled);
            buf.writeBoolean(isInventoryEnabled);
            buf.writeBoolean(isLocationEnabled);
            buf.writeBoolean(isLandfallAddonsEnabled);
        }

        @Override
        public Type<ServerConfigSyncPayload> type() {
            if (SERVER_CONFIG_SYNC_TYPE == null) {
                throw new IllegalStateException("Attempted to use SERVER_CONFIG_SYNC_TYPE before it was initialized");
            }
            return SERVER_CONFIG_SYNC_TYPE;
        }

        public static class Handler {
            public static void handleClientPacket(final ServerConfigSyncPayload payload, final IPayloadContext context) {
                context.enqueueWork(() -> {
                    ClientSyncedConfig.updateAgingSystemStatus(payload.isAgingEnabled());
                    ClientSyncedConfig.updateInventorySystemStatus(payload.isInventoryEnabled());
                    ClientSyncedConfig.updateLocationSystemStatus(payload.isLocationEnabled());
                    ClientSyncedConfig.updateLandfallAddonsStatus(payload.isLandfallAddonsEnabled());
                });
            }
        }
    }

    /**
     * Sends the server config to a specific player, typically on login.
     * @param player The player to send the config to.
     */
    public static void sendServerConfigToPlayer(ServerPlayer player) {
        if (SERVER_CONFIG_SYNC_TYPE == null) {
            Persona.LOGGER.error("[Persona] Cannot send server config, network not initialized");
            return;
        }
        boolean agingEnabled = Config.ENABLE_AGING_SYSTEM.get();
        boolean inventoryEnabled = Config.ENABLE_INVENTORY_SYSTEM.get();
        boolean locationEnabled = Config.ENABLE_LOCATION_SYSTEM.get();
        boolean landfallEnabled = Config.ENABLE_LANDFALL_ADDONS.get();
        PacketDistributor.sendToPlayer(player, new ServerConfigSyncPayload(
            agingEnabled, inventoryEnabled, locationEnabled, landfallEnabled));
    }
} 