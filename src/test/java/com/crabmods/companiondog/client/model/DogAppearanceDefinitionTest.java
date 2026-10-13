package com.crabmods.companiondog.client.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DogAppearanceDefinitionTest {
    private static JsonObject template() throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("appearance-pack-template/assets/companiondog/appearance/dog.json"))).getAsJsonObject();
    }
    @Test void emptyTemplateHasNoGeometryOrTextures() throws Exception {
        var definition = DogAppearanceDefinition.parse(template().toString());
        assertFalse(definition.hasGeometry());
        assertTrue(definition.textures().isEmpty());
        assertEquals(DogAppearanceDefinition.empty().bones(), definition.bones());
    }
    @Test void missingRigBoneIsRejected() throws Exception {
        var object = template();
        object.getAsJsonObject("bones").remove("jaw");
        assertThrows(IllegalArgumentException.class, () -> DogAppearanceDefinition.parse(object.toString()));
    }
    @Test void unknownFieldsAreRejected() throws Exception {
        var object = template(); object.addProperty("unexpected", 1);
        assertThrows(IllegalArgumentException.class, () -> DogAppearanceDefinition.parse(object.toString()));
    }
    @Test void fractionalTextureSizeIsRejected() throws Exception {
        var object = template(); object.add("texture_size", JsonParser.parseString("[64.5,64]"));
        assertThrows(IllegalArgumentException.class, () -> DogAppearanceDefinition.parse(object.toString()));
    }
    @Test void invalidScaleIsRejected() throws Exception {
        for (float scale : new float[] {0, -1, 9}) {
            var object = template(); object.addProperty("scale", scale);
            assertThrows(IllegalArgumentException.class, () -> DogAppearanceDefinition.parse(object.toString()));
        }
    }
    @Test void textureTraversalIsRejected() throws Exception {
        var object = template(); object.getAsJsonObject("textures").addProperty("normal", "example:textures/../secret.png");
        assertThrows(IllegalArgumentException.class, () -> DogAppearanceDefinition.parse(object.toString()));
    }
    @Test void cubeRequiresNormalTextureAndSockets() throws Exception {
        var object = withCube();
        assertThrows(IllegalArgumentException.class, () -> DogAppearanceDefinition.parse(object.toString()));
        object.getAsJsonObject("textures").addProperty("normal", "example:textures/dog.png");
        assertThrows(IllegalArgumentException.class, () -> DogAppearanceDefinition.parse(object.toString()));
    }
    @Test void validExternalCubeAndSocketDataAreAccepted() throws Exception {
        var object = withCube();
        object.getAsJsonObject("textures").addProperty("normal", "example:textures/dog.png");
        for (String name : new String[] {"default", "tennis_ball", "frisbee"}) {
            object.getAsJsonObject("sockets").add(name, JsonParser.parseString("{\"offset\":[0,0,0],\"rotation\":[0,0,0],\"scale\":1}"));
        }
        assertTrue(DogAppearanceDefinition.parse(object.toString()).hasGeometry());
    }
    @Test void oversizedDefinitionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DogAppearanceDefinition.parse(" ".repeat(262145)));
    }
    @Test void textureMayBeUniformIntegerMultipleOfUvGrid() throws Exception {
        var definition = DogAppearanceDefinition.parse(template().toString());
        assertDoesNotThrow(() -> definition.validateTextureDimensions(64, 64));
        assertDoesNotThrow(() -> definition.validateTextureDimensions(128, 128));
        assertDoesNotThrow(() -> definition.validateTextureDimensions(512, 512));
        for (int[] size : new int[][] {{32, 32}, {96, 64}, {128, 64}, {100, 100}, {576, 576}}) {
            assertThrows(IllegalArgumentException.class, () -> definition.validateTextureDimensions(size[0], size[1]),
                    size[0] + "x" + size[1]);
        }
    }
    @Test void overflowingUvIsRejected() throws Exception {
        var object = withCube();
        object.getAsJsonObject("bones").getAsJsonObject("body").getAsJsonArray("cubes").get(0).getAsJsonObject().add("uv", JsonParser.parseString("[63,63]"));
        assertThrows(IllegalArgumentException.class, () -> DogAppearanceDefinition.parse(object.toString()));
    }
    private static JsonObject withCube() throws Exception {
        var object = template();
        object.getAsJsonObject("bones").getAsJsonObject("body").add("cubes", JsonParser.parseString("[{\"origin\":[0,0,0],\"size\":[1,1,1],\"uv\":[0,0]}]"));
        return object;
    }
}
