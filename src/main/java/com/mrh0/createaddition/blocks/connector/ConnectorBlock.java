package com.mrh0.createaddition.blocks.connector;

import com.mrh0.createaddition.config.Config;
import com.mrh0.createaddition.energy.IWireNode;
import com.mrh0.createaddition.energy.NodeRotation;
import com.mrh0.createaddition.index.CATileEntities;
import com.mrh0.createaddition.shapes.CAShapes;
import com.simibubi.create.content.contraptions.ITransformableBlock;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.utility.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ConnectorBlock extends Block implements IBE<ConnectorTileEntity>, IWrenchable, ITransformableBlock {
	boolean IGNORE_FACE_CHECK = Config.CONNECTOR_IGNORE_FACE_CHECK.get();

	public static final VoxelShaper CONNECTOR_SHAPE = CAShapes.shape(6, 0, 6, 10, 5, 10).forDirectional();
	public static final DirectionProperty FACING = BlockStateProperties.FACING;
	public static final EnumProperty<ConnectorMode> MODE = EnumProperty.create("mode", ConnectorMode.class);
	private static final VoxelShape boxwe = Block.box(0,7,7,10,9,9);
	private static final VoxelShape boxsn = Block.box(7,7,0,9,9,10);

	public ConnectorBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.defaultBlockState()
				.setValue(FACING, Direction.NORTH)
				.setValue(MODE, ConnectorMode.Passive)
				.setValue(NodeRotation.ROTATION, NodeRotation.NONE));
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return CONNECTOR_SHAPE.get(state.getValue(FACING).getOpposite());
	}

	@Override
	public Class<ConnectorTileEntity> getBlockEntityClass() {
		return ConnectorTileEntity.class;
	}

	@Override
	public BlockEntityType<? extends ConnectorTileEntity> getBlockEntityType() {
		return CATileEntities.CONNECTOR.get();
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		builder.add(FACING, MODE, NodeRotation.ROTATION);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext c) {
		Direction dir = c.getClickedFace().getOpposite();

		ConnectorMode mode = ConnectorMode.test(c.getLevel(), c.getClickedPos().relative(dir), c.getClickedFace());

		return this.defaultBlockState().setValue(FACING, dir).setValue(MODE, mode);
	}

	@Override
	public void playerWillDestroy(Level worldIn, BlockPos pos, BlockState state, Player player) {
		super.playerWillDestroy(worldIn, pos, state, player);

		if (worldIn.isClientSide()) return;
		BlockEntity te = worldIn.getBlockEntity(pos);
		if (te == null) return;
		if (!(te instanceof IWireNode cte)) return;
		cte.dropWires(worldIn, !player.isCreative());
	}

	@Override
	public InteractionResult onWrenched(BlockState state, UseOnContext c) {
		if (c.getLevel().isClientSide()) {
			c.getLevel().playLocalSound(c.getClickedPos().getX(), c.getClickedPos().getY(), c.getClickedPos().getZ(), SoundEvents.BONE_BLOCK_HIT, SoundSource.BLOCKS, 1f, 1f, false);
		}
		c.getLevel().setBlockAndUpdate(c.getClickedPos(), state.setValue(MODE, state.getValue(MODE).getNext()));
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext c) {
		BlockEntity te = c.getLevel().getBlockEntity(c.getClickedPos());
		if(te == null)
			return IWrenchable.super.onSneakWrenched(state, c);
		if(!(te instanceof IWireNode))
			return IWrenchable.super.onSneakWrenched(state, c);
		IWireNode cte = (IWireNode) te;

		if (!c.getLevel().isClientSide())
			cte.dropWires(c.getLevel(), c.getPlayer(), !c.getPlayer().isCreative());

		return IWrenchable.super.onSneakWrenched(state, c);
	}

	@Override
	public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
		BlockEntity tileentity = state.hasBlockEntity() ? worldIn.getBlockEntity(pos) : null;
		if(tileentity != null) {
			if(tileentity instanceof ConnectorTileEntity) {
				((ConnectorTileEntity)tileentity).updateCache();
			}
		}
		if (!state.canSurvive(worldIn, pos)) {
			dropResources(state, worldIn, pos, tileentity);

			if(tileentity instanceof IWireNode)
				((IWireNode) tileentity).dropWires(worldIn, true);

			worldIn.removeBlock(pos, false);

			for (Direction direction : Direction.values())
				worldIn.updateNeighborsAt(pos.relative(direction), this);
		}
	}

	public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
		Direction dir = state.getValue(FACING);
		return
				!Shapes.joinIsNotEmpty(world.getBlockState(pos.relative(dir)).getBlockSupportShape(world,pos.relative(dir)).getFaceShape(dir.getOpposite()), boxwe, BooleanOp.ONLY_SECOND) ||
				!Shapes.joinIsNotEmpty(world.getBlockState(pos.relative(dir)).getBlockSupportShape(world,pos.relative(dir)).getFaceShape(dir.getOpposite()), boxsn, BooleanOp.ONLY_SECOND) ||
				world.getBlockState(pos.relative(dir)).isFaceSturdy(world, pos, dir.getOpposite(), SupportType.CENTER) || IGNORE_FACE_CHECK;
	}

	@Override
	public BlockState rotate(BlockState state, Rotation direction) {
		// Handle old rotation.
		return state.setValue(FACING, direction.rotate(state.getValue(FACING)));
	}
	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
	}

	@Override
	public BlockState transform(BlockState state, StructureTransform transform) {
		NodeRotation rotation = NodeRotation.get(transform.rotationAxis, transform.rotation);
		// Handle default rotation & mirroring.
		if (transform.mirror != null) state = mirror(state, transform.mirror);
		state = state.setValue(FACING, rotation.rotate(state.getValue(FACING), false));
		// Set the rotation state, which will be used to update the nodes.
		return state.setValue(NodeRotation.ROTATION, rotation);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return CATileEntities.CONNECTOR.create(pos, state);
	}
}
