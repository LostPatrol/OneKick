/** Data-driven advancement events emitted by kick effects. */
package net.lostpatrol.onekick.advancement;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.Arrays;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

public final class KickAdvancementTrigger
        extends SimpleCriterionTrigger<KickAdvancementTrigger.TriggerInstance> {
    private final ResourceLocation id;

    /** Associates all kick event instances with the criterion registry ID. */
    public KickAdvancementTrigger(ResourceLocation id) {
        this.id = id;
    }

    /** Returns the registered criterion ID. */
    @Override
    public ResourceLocation getId() {
        return id;
    }

    /** Reads the event name from advancement JSON. */
    @Override
    protected TriggerInstance createInstance(
            JsonObject json,
            ContextAwarePredicate player,
            DeserializationContext context) {
        return new TriggerInstance(id, player,
                Event.fromName(GsonHelper.getAsString(json, "event")));
    }

    /** Awards listeners matching the emitted kick event. */
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

    public static final class TriggerInstance extends AbstractCriterionTriggerInstance {
        private final Event event;

        /** Freezes the criterion's event and player predicate. */
        private TriggerInstance(
                ResourceLocation id, ContextAwarePredicate player, Event event) {
            super(id, player);
            this.event = event;
        }

        /** Tests whether this criterion listens for the emitted event. */
        private boolean matches(Event event) {
            return this.event == event;
        }

        /** Writes the event alongside vanilla player conditions. */
        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            json.addProperty("event", event.serializedName());
            return json;
        }
    }
}
