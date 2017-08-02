package com.cout970.magneticraft.block.core

import com.cout970.magneticraft.AABB
import com.cout970.magneticraft.IVector3
import com.cout970.magneticraft.misc.tileentity.getTile
import com.cout970.magneticraft.misc.world.isClient
import com.cout970.magneticraft.tileentity.core.TileBase
import com.cout970.magneticraft.util.vector.EMPTY_AABB
import com.cout970.magneticraft.util.vector.cut
import com.cout970.magneticraft.util.vector.vec3Of
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.client.renderer.block.statemap.IStateMapper
import net.minecraft.client.renderer.block.statemap.StateMapperBase
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.NonNullList
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilityProvider

@Suppress("OverridingDeprecatedMember")
open class BlockBase(material: Material) : Block(material), ICapabilityProvider {

    companion object {
        // Because Mojang is stupid and createBlockState is called BEFORE the constructor
        var states_: List<IStatesEnum>? = null
    }

    val states: List<IStatesEnum> = states_!!
    var customModels: List<Pair<String, ResourceLocation>> = emptyList()
    var enableOcclusionOptimization = true
    var translucent_ = false
    var generateDefaultItemModel = true
    var alwaysDropDefault = false

    // Methods
    var aabb: ((BoundingBoxArgs) -> List<AABB>)? = null
    var onActivated: ((OnActivatedArgs) -> Boolean)? = null
    var stateMapper: ((IBlockState) -> ModelResourceLocation)? = null
    var onBlockPlaced: ((OnBlockPlacedArgs) -> IBlockState)? = null
    var onBlockPostPlaced: ((OnBlockPostPlacedArgs) -> Unit)? = null
    var onBlockBreak: ((BreakBlockArgs) -> Unit)? = null
    var pickBlock: ((PickBlockArgs) -> ItemStack)? = null
    var canPlaceBlockOnSide: ((CanPlaceBlockOnSideArgs) -> Boolean)? = null
    var capabilityProvider: ICapabilityProvider? = null
    var onNeighborChanged: ((OnNeighborChangedArgs) -> Unit)? = null
    var blockStatesToPlace: ((BlockStatesToPlaceArgs) -> List<Pair<BlockPos, IBlockState>>)? = null
    var onDrop: ((DropsArgs) -> List<ItemStack>)? = null

    // ItemBlock stuff
    val inventoryVariants: Map<Int, String> = run {
        val map = mutableMapOf<Int, String>()
        states.filter { it.isVisible }.forEach { value ->
            map += value.ordinal to value.stateName
        }
        map
    }

    fun getItemName(stack: ItemStack?) = "${unlocalizedName}_${states.getOrNull(stack!!.metadata)?.stateName}"

    // metadata and block state stuff
    override fun getMetaFromState(state: IBlockState): Int = states.find {
        it.getBlockState(this) == state
    }?.ordinal ?: 0

    override fun getStateFromMeta(meta: Int): IBlockState = states[meta].getBlockState(this)

    override fun createBlockState(): BlockStateContainer =
            BlockStateContainer(this, *states_!![0].properties.toTypedArray())

    // event stuff
    override fun getBoundingBox(state: IBlockState, source: IBlockAccess, pos: BlockPos): AABB {
        return aabb?.invoke(BoundingBoxArgs(state, source, pos))?.reduce { acc, aabb ->
            acc.union(aabb)
        } ?: FULL_BLOCK_AABB
    }

    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override fun addCollisionBoxToList(state: IBlockState, worldIn: World, pos: BlockPos, entityBox: AxisAlignedBB,
                                       collidingBoxes: MutableList<AxisAlignedBB>, entityIn: Entity?,
                                       p_185477_7_: Boolean) {
        aabb?.let {
            val list = it.invoke(BoundingBoxArgs(state, worldIn, pos))
            val boxes = list.mapNotNull { it.cut(FULL_BLOCK_AABB)?.offset(pos) }
            boxes.filterTo(collidingBoxes) { entityBox.intersects(it) }
            return
        }
        super.addCollisionBoxToList(state, worldIn, pos, entityBox, collidingBoxes, entityIn, p_185477_7_)
    }

    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override fun getSelectedBoundingBox(state: IBlockState, worldIn: World, pos: BlockPos): AxisAlignedBB {
        aabb?.let {
            val rel = it.invoke(BoundingBoxArgs(state, worldIn, pos))

            val player = Minecraft.getMinecraft().player
            val start = player.getPositionEyes(0f)
            val look = player.getLook(0f)
            val blockReachDistance = Minecraft.getMinecraft().playerController!!.blockReachDistance
            val end = start.addVector(
                    look.x * blockReachDistance,
                    look.y * blockReachDistance,
                    look.z * blockReachDistance
            )

            val res = rel
                    .mapNotNull { it.cut(FULL_BLOCK_AABB) }
                    .associate { it to rayTrace(pos, start, end, it) }
                    .filter { it.value != null }
                    .map { it.key to it.value }
                    .sortedBy { it.second!!.hitVec.distanceTo(start) }
                    .firstOrNull()?.first

            return res?.offset(pos) ?: EMPTY_AABB
        }
        return super.getSelectedBoundingBox(state, worldIn, pos)
    }

    @Suppress("OverridingDeprecatedMember")
    override fun collisionRayTrace(blockState: IBlockState, worldIn: World, pos: BlockPos, start: Vec3d,
                                   end: Vec3d): RayTraceResult? {

        aabb?.let {
            val rel = it.invoke(BoundingBoxArgs(blockState, worldIn, pos))
            val res = rel
                    .mapNotNull { it.cut(FULL_BLOCK_AABB) }
                    .associate { it to rayTrace(pos, start, end, it) }
                    .filter { it.value != null }
                    .map { it.key to it.value }
                    .sortedBy { it.second!!.hitVec.distanceTo(start) }
                    .firstOrNull()?.second

            return res
        }
        return this.rayTrace(pos, start, end, blockState.getBoundingBox(worldIn, pos))
    }

    override fun onBlockActivated(worldIn: World, pos: BlockPos, state: IBlockState, playerIn: EntityPlayer,
                                  hand: EnumHand, side: EnumFacing, hitX: Float, hitY: Float,
                                  hitZ: Float): Boolean {
        val heldItem = playerIn.getHeldItem(hand)

        return onActivated?.invoke(
                OnActivatedArgs(worldIn, pos, state, playerIn, hand, heldItem, side,
                        vec3Of(hitX, hitY, hitZ)))
               ?: super.onBlockActivated(worldIn, pos, state, playerIn, hand, side, hitX, hitY, hitZ)
    }

    override fun damageDropped(state: IBlockState): Int {
        return if (alwaysDropDefault) 0 else getMetaFromState(state)
    }

    override fun getPickBlock(state: IBlockState, target: RayTraceResult, world: World, pos: BlockPos,
                              player: EntityPlayer): ItemStack {
        val default = super.getPickBlock(state, target, world, pos, player)
        return pickBlock?.invoke(PickBlockArgs(state, target, world, pos, player, default)) ?: default
    }

    // Called in server and client
    override fun removedByPlayer(state: IBlockState, world: World, pos: BlockPos, player: EntityPlayer?,
                                 willHarvest: Boolean): Boolean {
        if (world.isClient) {
            world.getTile<TileBase>(pos)?.onBreak()
        }
        return super.removedByPlayer(state, world, pos, player, willHarvest)
    }

    // Only called in the server
    override fun breakBlock(worldIn: World, pos: BlockPos, state: IBlockState) {
        onBlockBreak?.invoke(BreakBlockArgs(worldIn, pos, state))
        worldIn.getTile<TileBase>(pos)?.onBreak()
        worldIn.removeTileEntity(pos)
        super.breakBlock(worldIn, pos, state)
    }

    override fun toString(): String = "BlockBase($registryName)"

    fun getCustomStateMapper(): IStateMapper? = object : StateMapperBase() {
        override fun getModelResourceLocation(state: IBlockState): ModelResourceLocation {
            stateMapper?.let { return it.invoke(state) }
            val variant = states.find { it.getBlockState(this@BlockBase) == state }?.stateName ?: "normal"
            return ModelResourceLocation(registryName, variant)
        }
    }

    override fun getStateForPlacement(world: World, pos: BlockPos, facing: EnumFacing,
                                      hitX: Float, hitY: Float, hitZ: Float, meta: Int,
                                      placer: EntityLivingBase?, hand: EnumHand): IBlockState {

        val state = super.getStateForPlacement(world, pos, facing, hitX, hitY, hitZ, meta, placer, hand)
        onBlockPlaced?.let {
            return it.invoke(
                    OnBlockPlacedArgs(world, pos, facing, vec3Of(hitX, hitY, hitZ), meta, placer, hand, defaultState)
            )
        }
        return state
    }

    override fun canPlaceBlockOnSide(worldIn: World, pos: BlockPos, side: EnumFacing): Boolean {
        val default = super.canPlaceBlockOnSide(worldIn, pos, side)
        return canPlaceBlockOnSide?.invoke(CanPlaceBlockOnSideArgs(worldIn, pos, side, default)) ?: default
    }

    override fun neighborChanged(state: IBlockState, worldIn: World, pos: BlockPos, blockIn: Block,
                                 fromPos: BlockPos) {
        onNeighborChanged?.invoke(OnNeighborChangedArgs(state, worldIn, pos, blockIn, fromPos))
    }

    override fun isFullBlock(state: IBlockState?): Boolean = !translucent_
    override fun isOpaqueCube(state: IBlockState?) = enableOcclusionOptimization
    override fun isFullCube(state: IBlockState?) = !translucent_

    override fun <T : Any?> getCapability(capability: Capability<T>, facing: EnumFacing?): T? =
            capabilityProvider?.getCapability(capability, facing)

    override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean =
            capabilityProvider?.hasCapability(capability, facing) ?: false

    fun getBlockStatesToPlace(worldIn: World, pos: BlockPos,
                              facing: EnumFacing, hitX: Float, hitY: Float, hitZ: Float,
                              meta: Int, player: EntityPlayer, hand: EnumHand): List<Pair<BlockPos, IBlockState>> {

        val default = getStateForPlacement(worldIn, pos, facing, hitX, hitY, hitZ, meta, player, hand)
        return blockStatesToPlace?.invoke(
                BlockStatesToPlaceArgs(worldIn, pos, facing, vec3Of(hitX, hitY, hitZ), meta, player, hand, default)
        ) ?: listOf(BlockPos.ORIGIN to default)
    }

    override fun getDrops(drops: NonNullList<ItemStack>, world: IBlockAccess, pos: BlockPos, state: IBlockState,
                          fortune: Int) {
        onDrop?.let {
            val default = NonNullList.create<ItemStack>()
            super.getDrops(default, world, pos, state, fortune)

            val list = it.invoke(DropsArgs(world, pos, state, fortune, default))
            drops.addAll(list)
            return
        }
        super.getDrops(drops, world, pos, state, fortune)

    }
}

data class BoundingBoxArgs(val state: IBlockState, val source: IBlockAccess, val pos: BlockPos)

data class OnActivatedArgs(val worldIn: World, val pos: BlockPos, val state: IBlockState, val playerIn: EntityPlayer,
                           val hand: EnumHand, val heldItem: ItemStack, val side: EnumFacing, val hit: IVector3)

data class OnBlockPlacedArgs(val world: World, val pos: BlockPos, val facing: EnumFacing,
                             val hit: IVector3, val itemMetadata: Int,
                             val placer: EntityLivingBase?, val hand: EnumHand, val defaultValue: IBlockState)

data class PickBlockArgs(val state: IBlockState, val target: RayTraceResult, val world: World, val pos: BlockPos,
                         val player: EntityPlayer, val default: ItemStack)

data class CanPlaceBlockOnSideArgs(val worldIn: World, val pos: BlockPos, val side: EnumFacing, val default: Boolean)

data class OnNeighborChangedArgs(val state: IBlockState, val worldIn: World, val pos: BlockPos, val blockIn: Block,
                                 val fromPos: BlockPos)

data class BlockStatesToPlaceArgs(val worldIn: World, val pos: BlockPos, val facing: EnumFacing, val hit: IVector3,
                                  val meta: Int, val player: EntityPlayer, val hand: EnumHand, val default: IBlockState)

data class BreakBlockArgs(val worldIn: World, val pos: BlockPos, val state: IBlockState)

data class DropsArgs(val world: IBlockAccess, val pos: BlockPos, val state: IBlockState, val fortune: Int,
                     val default: List<ItemStack>)

data class OnBlockPostPlacedArgs(val world: World, val pos: BlockPos, val facing: EnumFacing,
                                 val hit: IVector3, val placer: EntityLivingBase?, val hand: EnumHand,
                                 val relPos: BlockPos)