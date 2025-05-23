package world.landfall.persona.features.aging;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import world.landfall.persona.client.event.CollectCharacterInfoEvent;
import world.landfall.persona.config.ClientSyncedConfig;
import world.landfall.persona.data.CharacterProfile;
import net.neoforged.bus.api.SubscribeEvent;
import java.text.DecimalFormat;

public class AgingClientEvents {
    // Format to display age with one decimal place
    private static final DecimalFormat AGE_FORMAT = new DecimalFormat("0.#");

    @SubscribeEvent
    public static void onCollectCharacterInfo(CollectCharacterInfoEvent event) {
        if (!ClientSyncedConfig.isAgingSystemEnabled()) {
            return;
        }

        CharacterProfile profile = event.getCharacterProfile();
        
        // Use the dynamic age calculation method
        double age = AgingManager.getCalculatedAge(profile);
        
        if (age > 0) {
            // Format the age to have one decimal place
            String formattedAge = AGE_FORMAT.format(age);
            Component ageComponent = Component.literal("(Age: " + formattedAge + ")").withStyle(ChatFormatting.GRAY);
            event.addInfo(ageComponent);
        }
    }
} 