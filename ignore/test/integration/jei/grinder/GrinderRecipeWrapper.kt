package integration.jei.grinder

import com.cout970.magneticraft.api.registries.machines.grinder.IGrinderRecipe
import mezz.jei.api.ingredients.IIngredients
import mezz.jei.api.recipe.IRecipeWrapper
import net.minecraft.client.Minecraft
import net.minecraftforge.fluids.FluidStack

/**
 * Created by cout970 on 23/07/2016.
 */
class GrinderRecipeWrapper(val recipe: IGrinderRecipe) : IRecipeWrapper {

    override fun drawAnimations(minecraft: Minecraft, recipeWidth: Int, recipeHeight: Int) = Unit

    override fun drawInfo(minecraft: Minecraft, recipeWidth: Int, recipeHeight: Int, mouseX: Int, mouseY: Int) = Unit

    override fun getTooltipStrings(mouseX: Int, mouseY: Int): MutableList<String>? = mutableListOf()

    override fun getFluidInputs(): MutableList<FluidStack>? = mutableListOf()

    override fun handleClick(minecraft: Minecraft, mouseX: Int, mouseY: Int, mouseButton: Int): Boolean = false

    override fun getOutputs(): MutableList<Any?>? = mutableListOf(recipe.primaryOutput, recipe.secondaryOutput)

    override fun getFluidOutputs(): MutableList<FluidStack>? = mutableListOf()

    override fun getInputs(): MutableList<Any?>? = mutableListOf(recipe.input)

    override fun getIngredients(ingredients: IIngredients?) {}
}