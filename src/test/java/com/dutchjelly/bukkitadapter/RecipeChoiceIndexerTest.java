package com.dutchjelly.bukkitadapter;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecipeChoiceIndexerTest {
	@Test
	void indexesEverySpruceLogAlternativeForOneShapelessSlot() {
		RecipeChoice.MaterialChoice logs = materialChoice(
				Material.SPRUCE_LOG, Material.SPRUCE_WOOD,
				Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_SPRUCE_WOOD);
		ShapelessRecipe recipe = shapeless(logs);

		Adapter.RecipeContext context = RecipeChoiceIndexer.index(recipe);

		assertEquals(1, context.getTotalSlotCount());
		assertEquals(1, context.getMap().get(Material.SPRUCE_LOG));
		assertEquals(1, context.getMap().get(Material.SPRUCE_WOOD));
		assertEquals(1, context.getMap().get(Material.STRIPPED_SPRUCE_LOG));
		assertEquals(1, context.getMap().get(Material.STRIPPED_SPRUCE_WOOD));
		assertEquals(4, context.getMap().size());
	}

	@Test
	void countsBothMushroomChoicesWithoutTreatingAlternativesAsExtraSlots() {
		RecipeChoice.MaterialChoice mushrooms = materialChoice(Material.BROWN_MUSHROOM, Material.RED_MUSHROOM);
		RecipeChoice.MaterialChoice bowl = materialChoice(Material.BOWL);
		ShapelessRecipe recipe = shapeless(mushrooms, mushrooms, bowl);

		Adapter.RecipeContext context = RecipeChoiceIndexer.index(recipe);

		assertEquals(3, context.getTotalSlotCount());
		assertEquals(2, context.getMap().get(Material.BROWN_MUSHROOM));
		assertEquals(2, context.getMap().get(Material.RED_MUSHROOM));
		assertEquals(1, context.getMap().get(Material.BOWL));
		assertEquals(3, context.getMap().size());
	}

	@Test
	void countsEveryCellWhenAShapedSymbolRepeats() {
		ShapedRecipe recipe = mock(ShapedRecipe.class);
		RecipeChoice.MaterialChoice planks = materialChoice(Material.OAK_PLANKS, Material.SPRUCE_PLANKS);
		when(recipe.getShape()).thenReturn(new String[]{"PPP", "P P", "PPP"});
		when(recipe.getChoiceMap()).thenReturn(Collections.singletonMap('P', planks));

		Adapter.RecipeContext context = RecipeChoiceIndexer.index(recipe);

		assertEquals(8, context.getTotalSlotCount());
		assertEquals(8, context.getMap().get(Material.OAK_PLANKS));
		assertEquals(8, context.getMap().get(Material.SPRUCE_PLANKS));
		assertEquals(2, context.getMap().size());
	}

	@Test
	void deduplicatesOneExactChoicesMaterialAndExcludesUnlistedMaterials() {
		ItemStack oakVariantOne = item(Material.OAK_LOG);
		ItemStack oakVariantTwo = item(Material.OAK_LOG);
		ItemStack spruce = item(Material.SPRUCE_LOG);
		RecipeChoice.ExactChoice exact = mock(RecipeChoice.ExactChoice.class);
		when(exact.getChoices()).thenReturn(Arrays.asList(oakVariantOne, oakVariantTwo, spruce));
		ShapelessRecipe recipe = shapeless(exact);

		Adapter.RecipeContext context = RecipeChoiceIndexer.index(recipe);

		assertEquals(1, context.getTotalSlotCount());
		assertEquals(1, context.getMap().get(Material.OAK_LOG));
		assertEquals(1, context.getMap().get(Material.SPRUCE_LOG));
		assertEquals(2, context.getMap().size());
		assertFalse(context.getMap().containsKey(Material.BIRCH_LOG));
		assertFalse(context.getMap().containsKey(Material.AIR));
	}

	private static RecipeChoice.MaterialChoice materialChoice(final Material... materials) {
		RecipeChoice.MaterialChoice choice = mock(RecipeChoice.MaterialChoice.class);
		when(choice.getChoices()).thenReturn(Arrays.asList(materials));
		return choice;
	}

	private static ShapelessRecipe shapeless(final RecipeChoice... choices) {
		ShapelessRecipe recipe = mock(ShapelessRecipe.class);
		when(recipe.getChoiceList()).thenReturn(Arrays.asList(choices));
		return recipe;
	}

	private static ItemStack item(final Material material) {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getType()).thenReturn(material);
		return stack;
	}
}
