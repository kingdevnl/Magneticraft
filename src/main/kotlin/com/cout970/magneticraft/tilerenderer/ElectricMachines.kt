package com.cout970.magneticraft.tilerenderer

import com.cout970.magneticraft.block.ElectricMachines
import com.cout970.magneticraft.misc.tileentity.RegisterRenderer
import com.cout970.magneticraft.tileentity.TileBattery
import com.cout970.magneticraft.tileentity.TileWindTurbine
import com.cout970.magneticraft.tilerenderer.core.BaseTileRenderer
import com.cout970.magneticraft.tilerenderer.core.PIXEL
import com.cout970.magneticraft.tilerenderer.core.Utilities
import net.minecraft.util.EnumFacing

/**
 * Created by cout970 on 2017/08/10.
 */

@RegisterRenderer(TileBattery::class)
object TileRendererBattery : BaseTileRenderer<TileBattery>() {

    override fun init() {
        createModel(ElectricMachines.battery)
    }

    override fun render(te: TileBattery) {
        Utilities.rotateFromCenter(te.facing, 180f)
        renderModel("default")
    }
}

@RegisterRenderer(TileWindTurbine::class)
object TileRendererWindTurbine : BaseTileRenderer<TileWindTurbine>() {

    override fun init() {
        createModel(ElectricMachines.windTurbine)
    }

    override fun render(te: TileWindTurbine) {
        if (!te.windTurbineModule.hasTurbineHitbox) return
        Utilities.rotateFromCenter(te.facing, 180f)
        translate(0, -5, 1)

        var angle = te.windTurbineModule.rotation
        angle += te.windTurbineModule.rotationSpeed * ticks

        if (te.facing.axisDirection == EnumFacing.AxisDirection.NEGATIVE) {
            angle = -angle
        }

        translate(0.5, 5.5, 0)
        rotate(angle, 0, 0, 1)
        translate(-0.5, -5.5 + 2 * PIXEL, 0)
        renderModel("default")
    }
}