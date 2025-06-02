package world.landfall.persona.features.landfalladdon.decay;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import world.landfall.persona.client.event.CollectCharacterInfoEvent;
import world.landfall.persona.data.CharacterProfile;

@EventBusSubscriber(modid = world.landfall.persona.Persona.MODID, value = Dist.CLIENT)
public class DecayClientEvents {
    @SubscribeEvent
    public static void onCollectCharacterInfo(CollectCharacterInfoEvent event) {
        CharacterProfile profile = event.getCharacterProfile();
        int decayIndex = DecayManager.calculateDecayIndex(profile);
        DecayStages stage = DecayManager.getStage(decayIndex);
        // Gray text similar to Age display
        Component comp = Component.literal("(Decay: " + stage.getDisplayName() + ")").withStyle(ChatFormatting.GRAY);
        event.addInfo(comp);
    }
} 