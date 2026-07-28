package net.lostpatrol.onekick.registry;

import net.lostpatrol.onekick.OneKick;
import net.lostpatrol.onekick.world.ImpactDebrisEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntityTypes {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, OneKick.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ImpactDebrisEntity>> IMPACT_DEBRIS =
            ENTITY_TYPES.register(
            "impact_debris",
            () -> EntityType.Builder.<ImpactDebrisEntity>of(ImpactDebrisEntity::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F)
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .build("onekick:impact_debris")
    );

    private ModEntityTypes() {
    }

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
