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
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import world.landfall.persona.command.CommandRegistry;
import world.landfall.persona.config.Config;
import world.landfall.persona.data.CharacterProfile;
import world.landfall.persona.features.figura.FiguraReflector;
import world.landfall.persona.features.figura.FiguraClientEventListener;
import world.landfall.persona.registry.GlobalCharacterRegistry;
import world.landfall.persona.util.NameListManager;
import world.landfall.persona.registry.PersonaNetworking;
import net.minecraft.server.level.ServerPlayer;
import world.landfall.persona.features.aging.AgingClientEvents;

@Mod(Persona.MODID)
public class Persona {
    public static final String MODID = "persona";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Persona(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onConfigReload);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedIn);

        // Initialize name lists
        NameListManager.init();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[Persona] Initializing core systems...");
        GlobalCharacterRegistry.initialize();

        // Reload name lists when server starts
        NameListManager.reloadLists();
    }

    private void onConfigReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.SPEC) {
            LOGGER.info("[Persona] Reloading config...");
            CharacterProfile.updateNamePattern();
        }
    }

    public void registerCommands(final RegisterCommandsEvent event) {
        CommandRegistry.register(event.getDispatcher());
        LOGGER.info("[Persona] Commands registered.");
    }

    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PersonaNetworking.sendServerConfigToPlayer(serverPlayer);
        }
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("[Persona] Client setup complete for {}", Minecraft.getInstance().getUser().getName());
            NeoForge.EVENT_BUS.addListener(AgingClientEvents::onCollectCharacterInfo);

            event.enqueueWork(() -> {
                if (FiguraReflector.isFiguraAvailable()) {
                    Persona.LOGGER.info("[Persona-Figura] Figura integration is active. Registering event listener for ClientPersonaSwitchedEvent.");
                    // Register FiguraClientEventListener to the game event bus to catch ClientPersonaSwitchedEvent
                    NeoForge.EVENT_BUS.register(FiguraClientEventListener.class); 
                } else {
                    Persona.LOGGER.info("[Persona-Figura] Figura integration is disabled (Figura mod not found).");
                }
            });
        }
    }
}
