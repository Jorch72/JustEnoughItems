package mezz.jei.gui.recipes;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiIngredientGroup;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.gui.Focus;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.ingredients.GuiFluidStackGroup;
import mezz.jei.gui.ingredients.GuiIngredient;
import mezz.jei.gui.ingredients.GuiIngredientGroup;
import mezz.jei.gui.ingredients.GuiItemStackGroup;
import mezz.jei.util.Ingredients;
import mezz.jei.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public class RecipeLayout implements IRecipeLayoutDrawable {
	private static final int RECIPE_BUTTON_SIZE = 12;
	public static final int recipeTransferButtonIndex = 100;

	private final int ingredientCycleOffset = (int) (Math.random() * 10000);
	private final IRecipeCategory recipeCategory;
	private final GuiItemStackGroup guiItemStackGroup;
	private final GuiFluidStackGroup guiFluidStackGroup;
	private final Map<Class, GuiIngredientGroup> guiIngredientGroups;
	@Nullable
	private final RecipeTransferButton recipeTransferButton;
	private final IRecipeWrapper recipeWrapper;
	private final IFocus<?> focus;

	private int posX;
	private int posY;

	public <T extends IRecipeWrapper> RecipeLayout(int index, IRecipeCategory<T> recipeCategory, T recipeWrapper, IFocus focus, int posX, int posY) {
		this.recipeCategory = recipeCategory;
		this.focus = focus;

		ItemStack itemStackFocus = null;
		FluidStack fluidStackFocus = null;
		Object focusValue = focus.getValue();
		if (focusValue instanceof ItemStack) {
			itemStackFocus = (ItemStack) focusValue;
		} else if (focusValue instanceof FluidStack) {
			fluidStackFocus = (FluidStack) focusValue;
		}
		this.guiItemStackGroup = new GuiItemStackGroup(new Focus<ItemStack>(focus.getMode(), itemStackFocus), ingredientCycleOffset);
		this.guiFluidStackGroup = new GuiFluidStackGroup(new Focus<FluidStack>(focus.getMode(), fluidStackFocus), ingredientCycleOffset);

		this.guiIngredientGroups = new HashMap<Class, GuiIngredientGroup>();
		this.guiIngredientGroups.put(ItemStack.class, this.guiItemStackGroup);
		this.guiIngredientGroups.put(FluidStack.class, this.guiFluidStackGroup);

		if (index >= 0) {
			this.recipeTransferButton = new RecipeTransferButton(recipeTransferButtonIndex + index, 0, 0, RECIPE_BUTTON_SIZE, RECIPE_BUTTON_SIZE, "+", this);
		} else {
			this.recipeTransferButton = null;
		}

		setPosition(posX, posY);

		this.recipeWrapper = recipeWrapper;

		try {
			try {
				IIngredients ingredients = new Ingredients();
				recipeWrapper.getIngredients(ingredients);
				recipeCategory.setRecipe(this, recipeWrapper, ingredients);
			} catch (AbstractMethodError ignored) { // legacy
				recipeCategory.setRecipe(this, recipeWrapper);
			}
		} catch (RuntimeException e) {
			Log.error("Error caught from Recipe Category: {}", recipeCategory.getClass().getCanonicalName(), e);
		} catch (LinkageError e) {
			Log.error("Error caught from Recipe Category: {}", recipeCategory.getClass().getCanonicalName(), e);
		}
	}

	@Override
	public void setPosition(int posX, int posY) {
		this.posX = posX;
		this.posY = posY;

		if (this.recipeTransferButton != null) {
			int width = recipeCategory.getBackground().getWidth();
			int height = recipeCategory.getBackground().getHeight();
			this.recipeTransferButton.xPosition = posX + width + 2;
			this.recipeTransferButton.yPosition = posY + height - RECIPE_BUTTON_SIZE;
		}
	}

	@Override
	public void draw(Minecraft minecraft, final int mouseX, final int mouseY) {
		IDrawable background = recipeCategory.getBackground();

		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GlStateManager.disableLighting();
		GlStateManager.enableAlpha();

		final int recipeMouseX = mouseX - posX;
		final int recipeMouseY = mouseY - posY;

		GlStateManager.pushMatrix();
		GlStateManager.translate(posX, posY, 0.0F);
		{
			background.draw(minecraft);
			recipeCategory.drawExtras(minecraft);
			recipeCategory.drawAnimations(minecraft);
			recipeWrapper.drawAnimations(minecraft, background.getWidth(), background.getHeight());
			recipeWrapper.drawInfo(minecraft, background.getWidth(), background.getHeight(), recipeMouseX, recipeMouseY);
		}
		GlStateManager.popMatrix();

		GuiIngredient hoveredIngredient = null;
		for (GuiIngredientGroup guiIngredientGroup : guiIngredientGroups.values()) {
			GuiIngredient hovered = guiIngredientGroup.draw(minecraft, posX, posY, mouseX, mouseY);
			if (hovered != null) {
				hoveredIngredient = hovered;
			}
		}
		if (recipeTransferButton != null) {
			recipeTransferButton.drawButton(minecraft, mouseX, mouseY);
		}
		GlStateManager.disableBlend();
		GlStateManager.disableLighting();

		if (hoveredIngredient != null) {
			hoveredIngredient.drawHovered(minecraft, posX, posY, recipeMouseX, recipeMouseY);
		} else if (isMouseOver(mouseX, mouseY)) {
			List<String> tooltipStrings = new ArrayList<String>();
			try {
				//noinspection unchecked
				tooltipStrings.addAll(recipeCategory.getTooltipStrings(recipeMouseX, recipeMouseY));
			} catch (AbstractMethodError ignored) {
				// legacy recipe categories do not have this method
			}
			List<String> wrapperTooltip = recipeWrapper.getTooltipStrings(recipeMouseX, recipeMouseY);
			if (wrapperTooltip != null) {
				tooltipStrings.addAll(wrapperTooltip);
			}
			if (!tooltipStrings.isEmpty()) {
				TooltipRenderer.drawHoveringText(minecraft, tooltipStrings, mouseX, mouseY);
			}
		}

		GlStateManager.disableAlpha();
	}

	@Override
	public boolean isMouseOver(int mouseX, int mouseY) {
		final int recipeMouseY = mouseY - posY;
		final IDrawable background = recipeCategory.getBackground();
		return recipeMouseY >= 0 && recipeMouseY < background.getHeight();
	}

	@Override
	@Nullable
	public Object getIngredientUnderMouse(int mouseX, int mouseY) {
		for (GuiIngredientGroup<?> guiIngredientGroup : guiIngredientGroups.values()) {
			Object clicked = guiIngredientGroup.getIngredientUnderMouse(posX, posY, mouseX, mouseY);
			if (clicked != null) {
				return clicked;
			}
		}

		return null;
	}

	public boolean handleClick(Minecraft minecraft, int mouseX, int mouseY, int mouseButton) {
		return recipeWrapper.handleClick(minecraft, mouseX - posX, mouseY - posY, mouseButton);
	}

	@Override
	public GuiItemStackGroup getItemStacks() {
		return guiItemStackGroup;
	}

	@Override
	public IGuiFluidStackGroup getFluidStacks() {
		return guiFluidStackGroup;
	}

	@Override
	public <T> IGuiIngredientGroup<T> getIngredientsGroup(Class<T> ingredientClass) {
		//noinspection unchecked
		GuiIngredientGroup<T> guiIngredientGroup = guiIngredientGroups.get(ingredientClass);
		if (guiIngredientGroup == null) {
			T value = null;
			Object focusValue = this.focus.getValue();
			if (ingredientClass.isInstance(focusValue)) {
				//noinspection unchecked
				value = (T) focusValue;
			}
			IFocus<T> focus = new Focus<T>(this.focus.getMode(), value);
			guiIngredientGroup = new GuiIngredientGroup<T>(ingredientClass, focus, ingredientCycleOffset);
			guiIngredientGroups.put(ingredientClass, guiIngredientGroup);
		}
		return guiIngredientGroup;
	}

	@Override
	public void setRecipeTransferButton(int posX, int posY) {
		if (recipeTransferButton != null) {
			recipeTransferButton.xPosition = posX + this.posX;
			recipeTransferButton.yPosition = posY + this.posY;
		}
	}

	@Override
	public IFocus<?> getFocus() {
		return focus;
	}

	@Nullable
	public RecipeTransferButton getRecipeTransferButton() {
		return recipeTransferButton;
	}

	public IRecipeWrapper getRecipeWrapper() {
		return recipeWrapper;
	}

	public IRecipeCategory getRecipeCategory() {
		return recipeCategory;
	}

	public int getPosX() {
		return posX;
	}

	public int getPosY() {
		return posY;
	}
}
