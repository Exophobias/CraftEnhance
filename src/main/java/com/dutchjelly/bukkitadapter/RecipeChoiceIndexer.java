package com.dutchjelly.bukkitadapter;

import com.dutchjelly.bukkitadapter.Adapter.RecipeContext;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Builds safe material-count upper bounds for the vanilla crafting recipe cache.
 * A choice may allow several materials, but it still occupies only one slot.
 */
final class RecipeChoiceIndexer {
	private RecipeChoiceIndexer() {
	}

	static RecipeContext index(final Recipe recipe) {
		final RecipeContext context = new RecipeContext();
		int slots = 0;

		if (recipe instanceof ShapedRecipe) {
			final ShapedRecipe shaped = (ShapedRecipe) recipe;
			final Map<Character, RecipeChoice> choices = shaped.getChoiceMap();
			for (String row : shaped.getShape()) {
				for (int column = 0; column < row.length(); column++) {
					final char symbol = row.charAt(column);
					if (symbol == ' ') continue;
					final RecipeChoice choice = choices.get(symbol);
					if (choice == null) continue;
					mergeChoice(context, choice);
					slots++;
				}
			}
		} else if (recipe instanceof ShapelessRecipe) {
			for (RecipeChoice choice : ((ShapelessRecipe) recipe).getChoiceList()) {
				if (choice == null) continue;
				mergeChoice(context, choice);
				slots++;
			}
		} else {
			throw new IllegalArgumentException("Expected a shaped or shapeless recipe");
		}

		context.setTotalSlotCount(slots);
		return context;
	}

	private static void mergeChoice(final RecipeContext context, final RecipeChoice choice) {
		final Set<Material> materials = EnumSet.noneOf(Material.class);
		if (choice instanceof RecipeChoice.MaterialChoice) {
			materials.addAll(((RecipeChoice.MaterialChoice) choice).getChoices());
		} else if (choice instanceof RecipeChoice.ExactChoice) {
			for (ItemStack stack : ((RecipeChoice.ExactChoice) choice).getChoices())
				materials.add(stack.getType());
		} else {
			materials.add(choice.getItemStack().getType());
		}
		for (Material material : materials)
			context.mergeItem(material, 1);
	}
}
