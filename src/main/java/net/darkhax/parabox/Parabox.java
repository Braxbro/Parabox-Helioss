package net.darkhax.parabox;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.darkhax.bookshelf.network.NetworkHandler;
import net.darkhax.bookshelf.registry.RegistryHelper;
import net.darkhax.parabox.block.BlockParabox;
import net.darkhax.parabox.block.ItemBlockParabox;
import net.darkhax.parabox.block.TileEntityParabox;
import net.darkhax.parabox.gui.GuiHandler;
import net.darkhax.parabox.network.PacketActivate;
import net.darkhax.parabox.network.PacketConfirmReset;
import net.darkhax.parabox.network.PacketRefreshGui;
import net.darkhax.parabox.proxy.Proxy;
import net.darkhax.parabox.util.BlacklistedFileUtils;
import net.darkhax.parabox.util.WorldSpaceTimeManager;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid = Parabox.MODID, name = Parabox.NAME, version = "@VERSION@", dependencies = "required-after:bookshelf;required-after:prestige", certificateFingerprint = "@FINGERPRINT@")
public class Parabox {

	public static final String MODID = "parabox";
	public static final String NAME = "Parabox";
	public static final Logger LOG = LogManager.getLogger(Parabox.NAME);
	public static final RegistryHelper REGISTRY = new RegistryHelper(MODID).enableAutoRegistration().setTab(CreativeTabs.MISC);
	public static final NetworkHandler NETWORK = new NetworkHandler(MODID);

	private Block blockParabox;

	@Instance(MODID)
	public static Parabox instance;

	@SidedProxy(clientSide = "net.darkhax.parabox.proxy.ClientProxy", serverSide = "net.darkhax.parabox.proxy.Proxy")
	public static Proxy proxy;

	@EventHandler
	public void onPreInit(FMLPreInitializationEvent event) {

		NETWORK.register(PacketActivate.class, Side.SERVER);
		NETWORK.register(PacketConfirmReset.class, Side.SERVER);
		NETWORK.register(PacketRefreshGui.class, Side.CLIENT);
		NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());

		this.blockParabox = new BlockParabox();
		REGISTRY.registerBlock(this.blockParabox, new ItemBlockParabox(this.blockParabox), "parabox");
		GameRegistry.registerTileEntity(TileEntityParabox.class, new ResourceLocation(MODID, "parabox"));
		Configuration c = new Configuration(event.getSuggestedConfigurationFile());
		for (String s : c.getStringList("Backup Blacklist", "general", new String[] { "playerdata", "advancements", "level.dat" }, "The names of files/folders that will not be restored by a state backup."))
			BlacklistedFileUtils.IGNORED.add(s);
		TileEntityParabox.powerFactor = c.getInt("Power Factor", "general", 100000, 1, Integer.MAX_VALUE, "Power usage factor per cycle.");
		TileEntityParabox.cycleTime = c.getInt("Cycle Time", "general", 24000, 1, Integer.MAX_VALUE, "Tick time for a single cycle.");
		TileEntityParabox.maxReceive = c.getInt("Max Receive", "general", 120000, 1, Integer.MAX_VALUE, "Max power input per tick to the parabox.");
		if (c.hasChanged()) c.save();
		MinecraftForge.EVENT_BUS.register(proxy);
	}

	@EventHandler
	public static void serverStart(FMLServerStartedEvent event) {

		WorldSpaceTimeManager.onGameInstanceStart();
	}

	@EventHandler
	public static void serverStop(FMLServerStoppedEvent event) {

		WorldSpaceTimeManager.onGameInstanceClose();
	}

	public static void sendMessage(TextFormatting color, String text, Object... args) {

		MinecraftServer s = FMLCommonHandler.instance().getMinecraftServerInstance();
		if (s != null) {
			final TextComponentTranslation translation = new TextComponentTranslation(text, args);
			translation.getStyle().setColor(color);
			s.getPlayerList().sendMessage(translation, false);
		}
	}

	public static void sendMessage(EntityPlayer player, TextFormatting color, String text, Object... args) {

		if (!player.world.isRemote) {
			final TextComponentTranslation translation = new TextComponentTranslation(text, args);
			translation.getStyle().setColor(color);
			player.sendStatusMessage(translation, false);
		}
	}

	public static String ticksToTime(int ticks) {

		int i = ticks / 20;
		final int j = i / 60;
		i = i % 60;
		return i < 10 ? j + ":0" + i : j + ":" + i;
	}

	public static WorldServer overworld() {
		return FMLCommonHandler.instance().getMinecraftServerInstance().worlds[0];
	}
}