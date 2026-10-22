/** Verifies matching advancement resources, trigger events, and translations. */
package net.lostpatrol.onekick.advancement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdvancementResourcesTest {
    /** Resource paths and their corresponding trigger event names. */
    private static final Map<String, String> ADVANCEMENTS = Map.ofEntries(
            Map.entry("root", "kick"),
            Map.entry("charged_kick", "charged_kick"),
            Map.entry("angular_momentum_spin", "angular_momentum_spin"),
            Map.entry("aerodynamic_air_kick", "aerodynamic_air_kick"),
            Map.entry("reaction_hit", "reaction_hit"),
            Map.entry("disintegration_block_break", "disintegration_block_break"),
            Map.entry("unstable_explosion", "unstable_explosion"),
            Map.entry("explosive_disintegration", "explosive_disintegration"),
            Map.entry("maximum_charge", "maximum_charge"),
            Map.entry("mach_launch", "mach_launch"),
            Map.entry("mass_destruction", "mass_destruction"),
            Map.entry("massive_damage", "massive_damage"),
            Map.entry("ultimate_kick", "ultimate_kick"),
            Map.entry("override", "override"),
            Map.entry("trailblazer", "trailblazer"));

    /** Every declared kick event has one valid advancement resource. */
    @Test
    void advancementResourcesCoverEveryTriggerEvent() throws IOException {
        Set<String> configuredEvents = new HashSet<>();
        for (Map.Entry<String, String> expected : ADVANCEMENTS.entrySet()) {
            JsonObject advancement = resourceJson(
                    "data/onekick/advancements/" + expected.getKey() + ".json");
            JsonObject icon = advancement.getAsJsonObject("display").getAsJsonObject("icon");
            assertTrue(icon.has("item"));
            assertTrue(!icon.has("id"));
            var criterionEntry = advancement.getAsJsonObject("criteria")
                    .entrySet().iterator().next();
            assertEquals(expected.getValue(), criterionEntry.getKey());
            JsonObject criterion = criterionEntry.getValue().getAsJsonObject();
            assertEquals("onekick:kick_event", criterion.get("trigger").getAsString());
            String event = criterion.getAsJsonObject("conditions").get("event").getAsString();
            assertEquals(expected.getValue(), event);
            assertTrue(configuredEvents.add(event), "Duplicate event " + event);
        }

        Set<String> declaredEvents = new HashSet<>();
        for (KickAdvancementTrigger.Event event : KickAdvancementTrigger.Event.values()) {
            declaredEvents.add(event.serializedName());
            assertSame(event, KickAdvancementTrigger.Event.fromName(event.serializedName()));
        }
        assertEquals(declaredEvents, configuredEvents);
    }

    /** Every advancement has English and Simplified Chinese display text. */
    @Test
    void bothLanguagesContainEveryAdvancementText() throws IOException {
        JsonObject english = resourceJson("assets/onekick/lang/en_us.json");
        JsonObject chinese = resourceJson("assets/onekick/lang/zh_cn.json");
        for (String advancement : ADVANCEMENTS.keySet()) {
            String prefix = "advancements.onekick." + advancement;
            assertTrue(english.has(prefix + ".title"));
            assertTrue(english.has(prefix + ".description"));
            assertTrue(chinese.has(prefix + ".title"));
            assertTrue(chinese.has(prefix + ".description"));
        }
    }

    /** The two new milestones retain their requested advancement types. */
    @Test
    void newMilestonesUseRequestedFrames() throws IOException {
        assertEquals("challenge", resourceJson("data/onekick/advancements/override.json")
                .getAsJsonObject("display").get("frame").getAsString());
        assertEquals("task", resourceJson("data/onekick/advancements/trailblazer.json")
                .getAsJsonObject("display").get("frame").getAsString());
    }

    /** Invalid event names fail when loading advancement criteria. */
    @Test
    void unknownEventNamesAreRejected() {
        assertThrows(JsonSyntaxException.class,
                () -> KickAdvancementTrigger.Event.fromName("not_an_event"));
    }

    /** Loads a packaged JSON resource as UTF-8. */
    private static JsonObject resourceJson(String path) throws IOException {
        InputStream stream = AdvancementResourcesTest.class.getClassLoader()
                .getResourceAsStream(path);
        assertNotNull(stream, path);
        try (stream;
             InputStreamReader reader =
                     new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
