package com.example.rickregen;

import net.minecraft.resources.ResourceLocation;

public final class RickRegen {
	public static final String MOD_ID = "rickregen";

	private RickRegen() {}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
