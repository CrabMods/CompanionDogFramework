package com.crabmods.companiondog.client.model;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** The bundled default appearance must satisfy the same contract as an external pack. */
class DefaultAppearanceTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    private static DogAppearanceDefinition bundled() throws Exception {
        return DogAppearanceDefinition.parse(Files.readString(RESOURCES.resolve("assets/companiondog/appearance/dog.json")));
    }

    @Test void bundledAppearanceParsesWithGeometryTexturesAndSockets() throws Exception {
        var definition = bundled();
        assertTrue(definition.hasGeometry());
        assertEquals(DogAppearanceDefinition.PARENTS.keySet(), definition.bones().keySet());
        for (String texture : new String[] {"normal", "blink", "happy", "sleep", "collar"}) {
            assertTrue(definition.textures().containsKey(texture), texture);
        }
        for (String socket : new String[] {"default", "tennis_ball", "frisbee"}) {
            assertTrue(definition.sockets().containsKey(socket), socket);
        }
    }

    @Test void bundledTexturesExistWithAcceptedResolution() throws Exception {
        var definition = bundled();
        for (String location : definition.textures().values()) {
            String[] parts = location.split(":", 2);
            Path png = RESOURCES.resolve("assets").resolve(parts[0]).resolve(parts[1]);
            assertTrue(Files.isRegularFile(png), location);
            var header = ByteBuffer.wrap(Files.readAllBytes(png));
            assertEquals(0x89504e470d0a1a0aL, header.getLong(), location);
            header.getInt();
            assertEquals(0x49484452, header.getInt(), location);
            int width = header.getInt(), height = header.getInt();
            assertDoesNotThrow(() -> definition.validateTextureDimensions(width, height), location);
        }
    }

    @Test void everyLimbAndTheHeadCarryGeometry() throws Exception {
        var bones = bundled().bones();
        for (String name : DogAppearanceDefinition.PARENTS.keySet()) {
            if (name.equals("root")) continue;
            assertFalse(bones.get(name).cubes().isEmpty(), name + " has no cubes");
        }
    }

    @Test void restPoseStandsOnTheGround() throws Exception {
        var bones = bundled().bones();
        // At rest only leg chains reach the ground; pivots are parent-local translations.
        float lowest = -Float.MAX_VALUE;
        for (String side : new String[] {"right", "left"}) {
            for (String end : new String[] {"front", "hind"}) {
                var leg = bones.get(side + "_" + end + "_leg");
                var paw = bones.get(side + "_" + end + "_paw");
                assertEquals(0, leg.rotation().x(), 1e-6);
                assertEquals(0, paw.rotation().x(), 1e-6);
                float base = bones.get("root").pivot().y() + bones.get("body").pivot().y() + leg.pivot().y() + paw.pivot().y();
                for (var cube : paw.cubes()) lowest = Math.max(lowest, base + cube.origin().y() + cube.size().y());
            }
        }
        assertEquals(24.0F, lowest, 1e-4F);
    }
}
