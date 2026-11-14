package com.example.rickregen;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RegenCommand {
	private RegenCommand() {}

	/** Comando /regen y /regen reset. Para solo operadores: .requires(src -> src.hasPermission(2)) */
	public static LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("regen")
			.executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				if (!RegenLogic.start(player)) {
					ctx.getSource().sendFailure(Component.literal("Ya estas regenerando."));
					return 0;
				}
				return 1;
			})
			.then(Commands.literal("reset").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				RegenLogic.resetSkin(player);
				ctx.getSource().sendSuccess(() -> Component.literal("Skin original restaurada."), false);
				return 1;
			}));
	}
}
