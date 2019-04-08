package net.darkhax.parabox.util;

import java.io.File;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.UUID;

import com.jarhax.prestige.data.GlobalPrestigeData;
import com.jarhax.prestige.data.PlayerData;

import net.darkhax.parabox.Parabox;
import net.darkhax.parabox.block.BlockParabox;
import net.darkhax.parabox.block.TileEntityParabox;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;

@EventBusSubscriber
public class WorldSpaceTimeManager {

	private static File currentSaveRootDirectory;
	private static ParaboxWorldData currentWorldData;
	private static boolean[] oldSaveStates;
	private static boolean isSaving = false;
	private static boolean requireSaving = false;

	public static boolean isSaving() {

		return isSaving;
	}

	public static boolean requireSaving() {

		return requireSaving;
	}

	public static ParaboxWorldData getWorldData() {

		return currentWorldData;
	}

	public static void onGameInstanceStart() {

		Parabox.LOG.info("Initializing Parabox world data.");
		currentSaveRootDirectory = DimensionManager.getCurrentSaveRootDirectory();
		currentWorldData = ParaboxWorldData.getData(currentSaveRootDirectory);
	}

	public static void onGameInstanceClose() {

		Parabox.LOG.info("Saving Parabox world data.");
		currentWorldData.save(currentSaveRootDirectory);

		if (currentWorldData.isShouldDelete()) {

			Parabox.LOG.info("World has been marked for deletion. Starting world loop process.");

			try {

				Parabox.LOG.info("Deleting the current world file.");
				BlacklistedFileUtils.delete(currentSaveRootDirectory);
				Parabox.LOG.info("Restoring the initial world backup.");
				BlacklistedFileUtils.unzipFolder(currentWorldData.getBackupFile(), currentSaveRootDirectory.getParentFile());
				currentWorldData.getBackupFile().delete();

				for (final Entry<UUID, ParaboxUserData> entry : currentWorldData.getUserData()) {

					final PlayerData prestigeData = GlobalPrestigeData.getPlayerData(entry.getKey());
					prestigeData.addPrestige(entry.getValue().getPoints());
					GlobalPrestigeData.save(prestigeData);
				}
			}

			catch (final IOException e) {

				Parabox.LOG.catching(e);
			}

		}

		currentSaveRootDirectory = null;
		currentWorldData = null;
	}

	public static void initiateWorldBackup() {

		if (!currentWorldData.getBackupFile().exists() && !isSaving) {

			requireSaving = true;
		}
	}

	public static void triggerCollapse(WorldServer server) {

		for (final EntityPlayerMP player : server.getMinecraftServer().getPlayerList().getPlayers()) {

			player.connection.disconnect(new TextComponentString("The world is collapsing!"));
			Parabox.proxy.onGameShutdown(server.getSaveHandler().getWorldDirectory().getName());
		}

		if (!currentWorldData.getBackupFile().exists()) {

			Parabox.LOG.warn("Attempted to do a world reset, but no world backup found. This mod will not work as intended if the backup file {} is not restored.", currentWorldData.getBackupFile().getPath());
			return;
		}

		currentWorldData.setShouldDelete(true);
		WorldHelper.shutdown();
	}

	private static void disableSaving() {

		final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		Parabox.LOG.info("Temporarily disabling world saving.");

		server.getPlayerList().saveAllPlayerData();

		oldSaveStates = new boolean[server.worlds.length];

		for (int i = 0; i < oldSaveStates.length; i++) {
			final WorldServer worldServer = server.worlds[i];
			if (worldServer == null) {
				continue;
			}

			oldSaveStates[i] = worldServer.disableLevelSaving;

			try {
				worldServer.saveAllChunks(true, null);
				worldServer.flush();
			} catch (final MinecraftException ex) {
				Parabox.LOG.warn("Failed to save world.");
				Parabox.LOG.catching(ex);
			}

			worldServer.disableLevelSaving = true;
		}
	}

	private static void restoreSaving() {

		final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
		final int l = Math.min(oldSaveStates.length, server.worlds.length);
		Parabox.LOG.info("Restoring world saving states.");

		for (int i = 0; i < l; i++) {
			final WorldServer worldServer = server.worlds[i];

			if (worldServer != null) {
				worldServer.disableLevelSaving = oldSaveStates[i];
			}
		}

		if (server.getPlayerList() != null) {

			server.getPlayerList().sendMessage(new TextComponentTranslation("parabox.status.backup"));
		}
	}

	public static void saveCustomWorldData() {

		if (currentWorldData != null && currentSaveRootDirectory != null) currentWorldData.save(currentSaveRootDirectory);
	}

	@SubscribeEvent
	public static void serverTick(TickEvent.ServerTickEvent event) {

		if (event.phase == Phase.END) {

		return; }

		if (requireSaving && !isSaving) {

			try {

				disableSaving();
				isSaving = true;
				requireSaving = false;
				WorldHelper.saveWorld();

				Parabox.LOG.info("Creating snapshot of world at " + currentWorldData.getBackupFile().getName());
				ZipUtils.createZip(currentSaveRootDirectory, currentWorldData.getBackupFile());
				Parabox.LOG.info("Snapshot created succesffully.");
				currentWorldData.save(currentSaveRootDirectory);
			} catch (final IOException e) {

			}
		}

		else if (!requireSaving && isSaving) {

			restoreSaving();
			isSaving = false;
		}
	}

	@SubscribeEvent
	public static void login(PlayerLoggedInEvent event) {
		WorldSpaceTimeManager.getWorldData().getOrCreateData(event.player.getGameProfile().getId());
	}

	public static void handleFailState() {

		boolean noActiveUsers = true;

		TileEntityParabox box = BlockParabox.getParabox(Parabox.overworld(), currentWorldData.getParabox());

		if (box != null && box.isActive()) {
			noActiveUsers = false;
		}

		if (noActiveUsers && currentWorldData.getBackupFile().exists()) {

			currentWorldData.getBackupFile().delete();

			final PlayerList playerList = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList();
			if (playerList != null) {

				playerList.sendMessage(new TextComponentTranslation("parabox.status.backup.reset"));
			}
		}
	}
}
