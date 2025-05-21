package world.landfall.persona.data;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import world.landfall.persona.Persona;
import net.minecraft.nbt.CompoundTag;
import com.mojang.serialization.Codec;

@EventBusSubscriber(modid = Persona.MODID, bus = EventBusSubscriber.Bus.MOD)
public class PlayerCharacterCapability {
    public static final ResourceLocation CHARACTER_DATA_KEY = ResourceLocation.parse(Persona.MODID + ":character_data");
    public static AttachmentType<PlayerCharacterData> CHARACTER_DATA;
    
    private static final Codec<PlayerCharacterData> CODEC = CompoundTag.CODEC.xmap(
        PlayerCharacterData::deserialize,
        PlayerCharacterData::serialize
    );
    
    @SubscribeEvent
    public static void registerAttachments(RegisterEvent event) {
        if (event.getRegistryKey().equals(NeoForgeRegistries.Keys.ATTACHMENT_TYPES)) {
            CHARACTER_DATA = AttachmentType.builder(PlayerCharacterData::new)
                .serialize(CODEC)
                .build();
                
            event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CHARACTER_DATA_KEY, () -> CHARACTER_DATA);
        }
    }
}
