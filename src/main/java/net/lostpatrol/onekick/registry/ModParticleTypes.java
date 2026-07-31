package net.lostpatrol.onekick.registry;

import net.lostpatrol.onekick.OneKick;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticleTypes {
    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, OneKick.MOD_ID);

    public static final RegistryObject<SimpleParticleType> MACH_RING =
            PARTICLE_TYPES.register("mach_ring", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> MACH_TRAIL =
            PARTICLE_TYPES.register("mach_trail", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> DISINTEGRATION_SMOKE =
            PARTICLE_TYPES.register("disintegration_smoke", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> CHARGE_SMOKE =
            PARTICLE_TYPES.register("charge_smoke", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> UNSTABLE_EXPLOSION =
            PARTICLE_TYPES.register("unstable_explosion", () -> new SimpleParticleType(false));

    private ModParticleTypes() {
    }

    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}
