package com.example.rickregen.forge;

import com.example.rickregen.RegenCommand;
import com.example.rickregen.RegenLogic;
import com.example.rickregen.RickRegen;
import com.example.rickregen.client.ClientRegenData;
import com.example.rickregen.network.RegenSyncData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod(RickRegen.MOD_ID)
public class RickRegenForge {
	private static final String PROTOCOL = "1";

	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
		RickRegen.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

	public RickRegenForge() {
		CHANNEL.registerMessage(0, RegenSyncData.class, RegenSyncData::write, RegenSyncData::read, (msg, ctxSupplier) -> {
			NetworkEvent.Context ctx = ctxSupplier.get();
			ctx.enqueueWork(() -> ClientRegenData.update(msg.playerId(), msg.regenerating(), msg.rickPrime()));
			ctx.setPacketHandled(true);
		});

		RegenLogic.sender = (to, data) -> CHANNEL.send(PacketDistributor.PLAYER.with(() -> to), data);

		MinecraftForge.EVENT_BUS.addListener(this::onDeath);
		MinecraftForge.EVENT_BUS.addListener(this::onAttack);
		MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
		MinecraftForge.EVENT_BUS.addListener(this::onLogin);
		MinecraftForge.EVENT_BUS.addListener(this::onCommands);
		MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
	}

	private void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && RegenLogic.onDeath(player)) {
			event.setCanceled(true);
		}
	}

	private void onAttack(LivingAttackEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && RegenLogic.isRegenerating(player)) {
			event.setCanceled(true);
		}
	}

	private void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.END && ServerLifecycleHooks.getCurrentServer() != null) {
			RegenLogic.tick(ServerLifecycleHooks.getCurrentServer());
		}
	}

	private void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			RegenLogic.syncAllTo(player);
		}
	}

	private void onCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(RegenCommand.build());
	}

	private void onServerStopped(ServerStoppedEvent event) {
		RegenLogic.clear();
	}
}
