package net.darkhax.parabox.proxy;

import org.apache.commons.lang3.tuple.Pair;

import net.darkhax.parabox.gui.GuiPostParabox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiListWorldSelectionEntry;
import net.minecraft.world.storage.WorldSummary;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ClientProxy extends Proxy {

	boolean hasShutDown = false;
	String worldName = "";

	@Override
	public void onGameShutdown(String worldName) {
		hasShutDown = true;
		this.worldName = worldName;
	}

	@SubscribeEvent
	public void onGui(GuiOpenEvent e) {
		if (hasShutDown && e.getGui() instanceof GuiDisconnected) {
			e.setCanceled(true);
			hasShutDown = false;
			Minecraft.getMinecraft().displayGuiScreen(new GuiPostParabox(worldName));
		}
	}

	@SuppressWarnings("unchecked") 
	@Override
	public void tryOpenList(Object guiListWorldEntry) {
		Pair<WorldSummary, GuiListWorldSelectionEntry> ent = (Pair<WorldSummary, GuiListWorldSelectionEntry>) guiListWorldEntry;
		if (ent.getLeft().getFileName().equals(worldName)) {
			worldName = "";
			ent.getRight().joinWorld();
		}
	}

}
