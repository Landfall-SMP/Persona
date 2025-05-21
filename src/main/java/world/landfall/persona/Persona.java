package world.landfall.persona;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import world.landfall.persona.command.CommandRegistry;
import world.landfall.persona.config.Config;
import world.landfall.persona.registry.GlobalCharacterRegistry;

@Mod(Persona.MODID)
public class Persona {
    public static final String MODID = "persona";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Persona(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Persona] Initializing core systems...");
        GlobalCharacterRegistry.initialize();
    }

    public void registerCommands(final RegisterCommandsEvent event) {
        CommandRegistry.register(event.getDispatcher());
        LOGGER.info("[Persona] Commands registered.");
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("[Persona] Client setup complete for {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
