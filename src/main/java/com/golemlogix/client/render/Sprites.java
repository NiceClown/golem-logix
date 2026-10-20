package com.golemlogix.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

/** Block textures by id (since 1.21.9 the atlases live in the AtlasManager). */
public final class Sprites {
    private Sprites() {
    }

    public static TextureAtlas blocks() {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
    }

    public static TextureAtlasSprite block(Identifier id) {
        return blocks().getSprite(id);
    }
}
