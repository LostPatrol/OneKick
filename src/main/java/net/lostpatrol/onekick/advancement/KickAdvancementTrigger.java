/** Data-driven advancement events emitted by kick effects. */
package net.lostpatrol.onekick.advancement;

import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

public final class KickAdvancementTrigger
        extends SimpleCriterionTrigger<KickAdvancementTrigger.TriggerInstance> {
    /** Supplies the advancement criterion codec. */
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Awards matching listeners for this player's kick event. */
    public void trigger(ServerPlayer player, Event event) {
        trigger(player, instance -> instance.matches(event));
    }

    public enum Event {
        KICK("kick"),
        CHARGED_KICK("charged_kick"),
        ANGULAR_MOMENTUM_SPIN("angular_momentum_spin"),
        AERODYNAMIC_AIR_KICK("aerodynamic_air_kick"),
        REACTION_HIT("reaction_hit"),
        DISINTEGRATION_BLOCK_BREAK("disintegration_block_break"),
        UNSTABLE_EXPLOSION("unstable_explosion"),
        EXPLOSIVE_DISINTEGRATION("explosive_disintegration"),
        MAXIMUM_CHARGE("maximum_charge"),
        MACH_LAUNCH("mach_launch"),
        MASS_DESTRUCTION("mass_destruction"),
        MASSIVE_DAMAGE("massive_damage"),
        ULTIMATE_KICK("ultimate_kick"),
        OVERRIDE("override"),
        TRAILBLAZER("trailblazer");

        /** Serialized event names used by advancement JSON. */
        private static final Codec<Event> CODEC =
                Codec.STRING.xmap(Event::fromName, Event::serializedName);
        private final String name;

        /** Associates an event with its persistent resource name. */
        Event(String name) {
            this.name = name;
        }

        /** Resolves resource names and rejects unknown criteria. */
        static Event fromName(String name) {
            return Arrays.stream(values())
                    .filter(event -> event.name.equals(name))
                    .findFirst()
                    .orElseThrow(() -> new JsonSyntaxException(
                            "Unknown OneKick advancement event: " + name));
        }

        /** Returns the persistent resource name. */
        String serializedName() {
            return name;
        }
    }

    public record TriggerInstance(
            Optional<ContextAwarePredicate> player,
            Event event
    ) implements SimpleCriterionTrigger.SimpleInstance {
        /** Criterion schema shared by every kick advancement. */
        private static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(
                instance -> instance.group(
                                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")
                                        .forGetter(TriggerInstance::player),
                                Event.CODEC.fieldOf("event")
                                        .forGetter(TriggerInstance::event))
                        .apply(instance, TriggerInstance::new));

        /** Tests whether this criterion listens for the emitted event. */
        private boolean matches(Event event) {
            return this.event == event;
        }
    }
}
