package net.darkhax.parabox.network;

import net.darkhax.bookshelf.network.TileEntityMessage;
import net.darkhax.parabox.block.TileEntityParabox;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketConfirmReset extends TileEntityMessage<TileEntityParabox> {

	public PacketConfirmReset() {

	}

	public PacketConfirmReset(BlockPos pos) {

		super(pos);
	}

	@Override
	public final IMessage handleMessage(MessageContext context) {

		super.handleMessage(context);
		return new PacketRefreshGui();
	}

	@Override
	public void getAction() {
		this.tile.getVoter().voteCollapse(this.context.getServerHandler().player);
		this.tile.sync();
	}
}
