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
import world.landfall.persona.data.PlayerCharacterData;
import world.landfall.persona.data.PlayerCharacterCapability;

import java.util.Optional;

@EventBusSubscriber(modid = Persona.MODID, bus = EventBusSubscriber.Bus.MOD)
public class PersonaNetworking {
    private static final ResourceLocation SYNC_ID = ResourceLocation.parse(Persona.MODID + ":sync_character");
    private static CustomPacketPayload.Type<SyncCharacterDataPayload> SYNC_PACKET_TYPE = null;
    private static StreamCodec<RegistryFriendlyByteBuf, SyncCharacterDataPayload> SYNC_PACKET_CODEC = null;
    
    @SubscribeEvent
    public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        if (SYNC_PACKET_TYPE != null) {
            Persona.LOGGER.warn("[Persona] Attempted to register payload handlers more than once. Skipping.");
            return;
        }
        
        try {
            SYNC_PACKET_TYPE = new CustomPacketPayload.Type<>(SYNC_ID);
            SYNC_PACKET_CODEC = StreamCodec.of(
                (buf, payload) -> payload.write(buf),
                SyncCharacterDataPayload::new
            );
            
            final PayloadRegistrar registrar = event.registrar(Persona.MODID);
            
            registrar.playToClient(SYNC_PACKET_TYPE, SYNC_PACKET_CODEC, SyncCharacterDataPayload.Handler::handleClientPacket);
            registrar.playToServer(SYNC_PACKET_TYPE, SYNC_PACKET_CODEC, SyncCharacterDataPayload.Handler::handleServerPacket);
            
            Persona.LOGGER.info("[Persona] Payload handlers registered successfully.");
        } catch (Exception e) {
            Persona.LOGGER.error("[Persona] Failed to register payload handlers", e);
            SYNC_PACKET_TYPE = null;
            SYNC_PACKET_CODEC = null;
        }
    }
    
    public static record SyncCharacterDataPayload(PlayerCharacterData data) implements CustomPacketPayload {
        public SyncCharacterDataPayload(RegistryFriendlyByteBuf buf) {
            this(PlayerCharacterData.deserialize(buf.readNbt()));
        }
        
        public void write(FriendlyByteBuf buf) { 
            buf.writeNbt(data.serialize());
        }
        
        @Override
        public Type<SyncCharacterDataPayload> type() {
            if (SYNC_PACKET_TYPE == null) {
                throw new IllegalStateException("Attempted to use SYNC_PACKET_TYPE before it was initialized");
            }
            return SYNC_PACKET_TYPE;
        }

        public static class Handler {
            public static void handleClientPacket(final SyncCharacterDataPayload payload, final IPayloadContext context) {
                Optional.ofNullable(context.player()).ifPresent(player -> {
                    player.getData(PlayerCharacterCapability.CHARACTER_DATA)
                        .copyFrom(payload.data());
                });
            }
        
            public static void handleServerPacket(final SyncCharacterDataPayload payload, final IPayloadContext context) {
                Optional.ofNullable(context.player()).ifPresent(player -> {
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.getData(PlayerCharacterCapability.CHARACTER_DATA)
                            .copyFrom(payload.data());
                        GlobalCharacterRegistry.syncRegistry(serverPlayer);
                    }
                });
            }
        }
    }
    
    public static void sendToPlayer(PlayerCharacterData data, ServerPlayer player) {
        if (SYNC_PACKET_TYPE == null) {
            Persona.LOGGER.error("[Persona] Cannot send packet, network not initialized");
            return;
        }
        PacketDistributor.sendToPlayer(player, new SyncCharacterDataPayload(data));
    }
    
    public static void sendToServer(PlayerCharacterData data) {
        if (SYNC_PACKET_TYPE == null) {
            Persona.LOGGER.error("[Persona] Cannot send packet, network not initialized");
            return;
        }
        PacketDistributor.sendToServer(new SyncCharacterDataPayload(data));
    }
} 