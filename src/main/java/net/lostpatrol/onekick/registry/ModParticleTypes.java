package net.lostpatrol.onekick.registry;

import net.lostpatrol.onekick.OneKick;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticleTypes {
    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, OneKick.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MACH_RING =
            PARTICLE_TYPES.register("mach_ring", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MACH_TRAIL =
            PARTICLE_TYPES.register("mach_trail", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DISINTEGRATION_SMOKE =
            PARTICLE_TYPES.register("disintegration_smoke", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CHARGE_SMOKE =
            PARTICLE_TYPES.register("charge_smoke", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> UNSTABLE_EXPLOSION =
            PARTICLE_TYPES.register("unstable_explosion", () -> new SimpleParticleType(false));

    private ModParticleTypes() {
    }

    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}
