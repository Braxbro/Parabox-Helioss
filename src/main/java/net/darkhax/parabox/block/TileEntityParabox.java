package net.darkhax.parabox.block;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;

import net.darkhax.bookshelf.block.tileentity.TileEntityBasicTickable;
import net.darkhax.parabox.Parabox;
import net.darkhax.parabox.handler.EnergyHandlerParabox;
import net.darkhax.parabox.handler.ItemHandlerParabox;
import net.darkhax.parabox.handler.VotingHandler;
import net.darkhax.parabox.util.ParaboxItemManager;
import net.darkhax.parabox.util.ParaboxUserData;
import net.darkhax.parabox.util.WorldSpaceTimeManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraft.server.management.PlayerList;

public class TileEntityParabox extends TileEntityBasicTickable {

	public static int cycleTime = 12000;
	public static int rfPerTick = 400;
	public static float cycleFactor = 2F;
	public static NumberFormat format = NumberFormat.getNumberInstance(Locale.ROOT);
	public static NumberFormat pct = NumberFormat.getIntegerInstance(Locale.ROOT);
	public static boolean updateMessages = true;

	protected boolean active = false;
	protected double cycleTimeLeft = 0;
	protected int points = 0;
	protected int ticksOnline = 0;
	protected int power = 0;
	protected int redstoneValue = 0;

	protected VotingHandler voter = new VotingHandler(this);
	protected EnergyHandlerParabox energyHandler = new EnergyHandlerParabox(rfPerTick * 2, rfPerTick * 2);
	protected ItemHandlerParabox itemHandler = new ItemHandlerParabox(this);

	@Override
	public void onEntityUpdate() {
		if (this.world.isRemote || !this.active) return;
		if (WorldSpaceTimeManager.getWorldData() == null && this.active && !WorldSpaceTimeManager.isSaving() && !WorldSpaceTimeManager.requireSaving()) {
			this.deactivate();
		}

		this.ticksOnline++;
		if (this.ticksOnline < 0) this.ticksOnline = 0;

		power = this.energyHandler.getEnergyStored();

		if (this.ticksOnline % 20 == 0) {
			this.sync();
		}

		if (this.ticksOnline % 200 == 0) {
			this.updateRedstone();
		}

		this.cycleTimeLeft -= this.getTicksPerTick();
		this.energyHandler.setEnergy(0);

		if (this.cycleTimeLeft <= 0) {
			this.points++;
			PlayerList pList = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList();
			for (Entry<UUID, ParaboxUserData> data : WorldSpaceTimeManager.getWorldData().getUserData())
				if(pList.getPlayerByUUID(data.getKey()) != null){
					data.getValue().setPoints(data.getValue().getPoints() + 1);
				}
			WorldSpaceTimeManager.saveCustomWorldData();
			Parabox.sendMessage(TextFormatting.LIGHT_PURPLE, "info.parabox.update.daily", this.getRFTNeeded());
			this.cycleTimeLeft = getCycleTime();
			this.energyHandler.updateValues(getRFTNeeded() * 2);
			this.itemHandler.randomizeTarget();
		}
	}

	public void activate() {
		this.active = true;
		//WorldSpaceTimeManager.initiateWorldBackup();
		this.points = 0;
		this.cycleTimeLeft = getCycleTime();
		this.ticksOnline = 0;
		this.energyHandler.updateValues(getRFTNeeded() * 2);
		this.itemHandler.randomizeTarget();
		this.sync();
	}

	public void deactivate() {
		this.active = false;
		this.points = 0;

		for (Entry<UUID, ParaboxUserData> data : WorldSpaceTimeManager.getWorldData().getUserData()) {
			data.getValue().setPoints(0);
		}

		WorldSpaceTimeManager.saveCustomWorldData();
		WorldSpaceTimeManager.handleFailState();
	}

	public ItemStack genRandomItem() {
		Random rand = new Random();
		rand.setSeed(this.ticksOnline ^ this.pos.getZ() * rand.nextLong());
		return ParaboxItemManager.CRAFTING_ITEMS.get(rand.nextInt(ParaboxItemManager.CRAFTING_ITEMS.size()));
	}

	public void provideItem(ItemStack stack) {
		this.cycleTimeLeft -= 1200;
		String oldName = this.itemHandler.getTarget().getDisplayName();
		this.itemHandler.randomizeTarget();
		if (updateMessages) Parabox.sendMessage(TextFormatting.GOLD, "info.parabox.update.item", oldName, this.itemHandler.getTarget().getDisplayName());
	}

	public List<String> getInfo(List<String> entries, EntityPlayer player) {
		if (this.active) {
			entries.add(I18n.format("parabox.status.power", format.format(this.getPower())));
			entries.add(I18n.format("parabox.status.target", format.format(this.getRFTNeeded() / 2), format.format(this.getRFTNeeded() * 2)));
			entries.add(I18n.format("parabox.status.item", this.itemHandler.getTarget().getDisplayName()));
			entries.add(I18n.format("parabox.status.speed", pct.format(this.getTicksPerTick() * 100)));
			entries.add(I18n.format("parabox.status.cycle", Parabox.ticksToTime(this.getRemainingTicks())));
			entries.add(I18n.format("parabox.status.points", this.points));
		} else {
			entries.add(I18n.format("parabox.status.offline"));
		}
		return entries;
	}

	@Override
	public void writeNBT(NBTTagCompound dataTag) {
		dataTag.setInteger("StoredPower", this.energyHandler.getEnergyStored());
		dataTag.setDouble("RemainingTicks", this.cycleTimeLeft);
		dataTag.setLong("Points", this.points);
		dataTag.setBoolean("Active", this.active);
		dataTag.setTag("Item", this.itemHandler.getTarget().writeToNBT(new NBTTagCompound()));
		dataTag.setInteger("Energy", power);
	}

	@Override
	public void readNBT(NBTTagCompound dataTag) {
		this.energyHandler.setEnergy(dataTag.getInteger("StoredPower"));
		this.cycleTimeLeft = dataTag.getDouble("RemainingTicks");
		this.points = dataTag.getInteger("Points");
		this.active = dataTag.getBoolean("Active");
		this.itemHandler.setTarget(new ItemStack(dataTag.getCompoundTag("Item")));
		this.energyHandler.updateValues(getRFTNeeded() * 2);
		if (world != null && world.isRemote) this.energyHandler.setEnergy(dataTag.getInteger("Energy"));
	}

	@Override
	public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
		return this.isActive() && (capability == CapabilityEnergy.ENERGY || capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) || super.hasCapability(capability, facing);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
		if (this.isActive() && capability == CapabilityEnergy.ENERGY) return (T) this.energyHandler;
		if (this.isActive() && capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return (T) this.itemHandler;
		return super.getCapability(capability, facing);
	}

	public int getRFTNeeded() {
		return points == 0 ? rfPerTick : floor(rfPerTick * points * cycleFactor);
	}

	public int getRemainingTicks() {
		return floor(this.cycleTimeLeft);
	}

	public static int floor(double f) {
		return MathHelper.floor(f);
	}

	public IEnergyStorage getEnergyHandler() {
		return this.energyHandler;
	}

	public boolean isActive() {
		return this.active;
	}

	public int getPower() {
		return this.energyHandler.getEnergyStored();
	}

	public int getGeneratedPoints() {
		return this.points;
	}

	public ItemStack getTarget() {
		return this.itemHandler.getTarget();
	}

	public VotingHandler getVoter() {
		return this.voter;
	}

	public double getCycleTime() {
		return cycleTime;
	}

	public double getTicksPerTick() {
		double sp = (double) getPower() / getRFTNeeded();
		if (sp >= 1) {
			double bonus = (sp - 1) / 5;
			return 1 + bonus;
		}
		if (sp < 0.5) return 0;
		return sp;
	}

	public void updateRedstone() {
		double sp = (double) getPower() / getRFTNeeded();
		if (sp >= 1) redstoneValue = 0;
		else if (sp < 0.5) {
			redstoneValue = 15;
		} else {
			sp -= 0.5;
			redstoneValue = 15 - MathHelper.ceil(sp / (1D / 30));
		}
		world.notifyNeighborsOfStateChange(pos, Parabox.parabox, false);
	}

}
