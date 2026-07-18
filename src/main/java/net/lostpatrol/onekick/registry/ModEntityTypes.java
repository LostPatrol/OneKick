package net.lostpatrol.onekick.registry;

import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.world.ImpactDebrisEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntityTypes {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, OneKick.MOD_ID);

    public static final RegistryObject<EntityType<ImpactDebrisEntity>> IMPACT_DEBRIS = ENTITY_TYPES.register(
            "impact_debris",
            () -> EntityType.Builder.<ImpactDebrisEntity>of(ImpactDebrisEntity::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F)
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .build("impact_debris")
    );

    private ModEntityTypes() {
    }

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
