package com.dutchjelly.craftenhance.crafthandling;

import com.dutchjelly.craftenhance.CraftEnhance;
import com.dutchjelly.craftenhance.cache.CacheRecipes;
import com.dutchjelly.craftenhance.crafthandling.livedata.RecipeWrapper;
import com.dutchjelly.craftenhance.crafthandling.recipes.EnhancedRecipe;
import com.dutchjelly.craftenhance.crafthandling.recipes.WBRecipe;
import com.dutchjelly.craftenhance.crafthandling.recipes.utility.RecipeType;
import com.dutchjelly.craftenhance.files.CategoryData;
import com.dutchjelly.craftenhance.files.CategoryDataCache;
import com.dutchjelly.craftenhance.messaging.Messenger;
import com.dutchjelly.craftenhance.updatechecking.VersionChecker;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeLoaderReloadTest {
	private static final List<String> CUSTOM_KEYS = Arrays.asList(
			"gunpowder", "brewingstand", "calcitetoquartzblock", "dioritetocalcite",
			"mossfromacacialeaves", "glowstonedust", "mossfromoakleaves",
			"cobblestonetogravel", "mossfromjungleleaves", "trident", "graveltosand");
	private static final Material[] CUSTOM_INPUTS = {
			Material.STONE, Material.DIRT, Material.CALCITE, Material.DIORITE,
			Material.ACACIA_LEAVES, Material.REDSTONE, Material.OAK_LEAVES,
			Material.COBBLESTONE, Material.JUNGLE_LEAVES, Material.PRISMARINE,
			Material.GRAVEL
	};

	private final Map<NamespacedKey, Recipe> serverRecipes = new LinkedHashMap<>();
	private MockedStatic<CraftEnhance> craftEnhance;
	private MockedStatic<Bukkit> bukkit;
	private MockedStatic<Messenger> messenger;
	private Server server;
	private CacheRecipes cache;
	private CategoryDataCache categories;
	private CategoryData category;

	@BeforeEach
	void setUp() {
		server = mock(Server.class);
		CraftEnhance plugin = mock(CraftEnhance.class);
		VersionChecker version = mock(VersionChecker.class);
		cache = mock(CacheRecipes.class);
		categories = mock(CategoryDataCache.class);
		category = CategoryData.of(item(Material.CRAFTING_TABLE), "default", "Crafting");
		when(categories.get(anyString())).thenReturn(category);
		when(plugin.getVersionChecker()).thenReturn(version);
		when(plugin.getCacheRecipes()).thenReturn(cache);
		when(plugin.getCategoryDataCache()).thenReturn(categories);
		when(plugin.getLogger()).thenReturn(Logger.getLogger("RecipeLoaderReloadTest"));
		when(version.newerThan(any(VersionChecker.ServerVersion.class))).thenReturn(true);
		when(server.recipeIterator()).thenAnswer(invocation -> serverRecipes.values().iterator());
		when(server.getRecipe(any(NamespacedKey.class)))
				.thenAnswer(invocation -> serverRecipes.get(invocation.getArgument(0)));
		when(server.addRecipe(any(Recipe.class))).thenAnswer(invocation -> {
			Recipe recipe = invocation.getArgument(0);
			return serverRecipes.putIfAbsent(keyOf(recipe), recipe) == null;
		});
		when(server.removeRecipe(any(NamespacedKey.class)))
				.thenAnswer(invocation -> serverRecipes.remove(invocation.getArgument(0)) != null);

		craftEnhance = mockStatic(CraftEnhance.class);
		craftEnhance.when(CraftEnhance::self).thenReturn(plugin);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::recipeIterator).thenAnswer(invocation -> server.recipeIterator());
		bukkit.when(() -> Bukkit.getRecipe(any(NamespacedKey.class)))
				.thenAnswer(invocation -> server.getRecipe(invocation.getArgument(0)));
		bukkit.when(() -> Bukkit.removeRecipe(any(NamespacedKey.class)))
				.thenAnswer(invocation -> server.removeRecipe(invocation.getArgument(0)));
		messenger = mockStatic(Messenger.class);
	}

	@AfterEach
	void tearDown() {
		messenger.close();
		bukkit.close();
		craftEnhance.close();
	}

	@Test
	void restoresElevenStableKeysAndFiveDisabledRecipesAfterAResourceReset() {
		List<EnhancedRecipe> customRecipes = customRecipes();
		List<Recipe> disabledRecipes = disabledRecipes();
		List<String> disabledIdentifiers = identifiers(disabledRecipes);
		Recipe foreignRecipe = shapeless("patriampost:patriampost_corn_recipe",
				Material.WHEAT, Material.PAPER);
		Recipe foreignCehPrefix = shapeless("thirdparty:cehrecipebonus", Material.STICK, Material.PAPER);
		putAll(disabledRecipes);
		put(foreignRecipe);
		put(foreignCehPrefix);
		RecipeLoader loader = new RecipeLoader(server, categories);
		loader.rebuildRecipes(customRecipes, disabledIdentifiers);
		assertRestored(loader, customRecipes, disabledIdentifiers, foreignRecipe);

		// Vanilla reload replaces the manager: custom keys vanish and disabled keys return.
		serverRecipes.clear();
		putAll(disabledRecipes());
		put(foreignRecipe);
		put(foreignCehPrefix);
		loader.rebuildRecipes(customRecipes, disabledIdentifiers);

		assertRestored(loader, customRecipes, disabledIdentifiers, foreignRecipe);
		assertSame(foreignCehPrefix, serverRecipes.get(keyOf(foreignCehPrefix)));
		assertEquals(13, serverRecipes.size());
		verify(server, never()).clearRecipes();
		verify(server, never()).resetRecipes();
		verify(server, never()).reloadData();
	}

	@Test
	void repeatedRebuildKeepsDisabledBookkeepingAndDoesNotDuplicateLookupRecipes() {
		List<EnhancedRecipe> customRecipes = customRecipes();
		List<Recipe> disabledRecipes = disabledRecipes();
		List<String> disabledIdentifiers = identifiers(disabledRecipes);
		putAll(disabledRecipes);
		RecipeLoader loader = new RecipeLoader(server, categories);

		for (int rebuild = 0; rebuild < 3; rebuild++) {
			loader.rebuildRecipes(customRecipes, disabledIdentifiers);
			assertEquals(11, serverRecipes.size());
			assertEquals(5, loader.getDisabledServerRecipes().size());
			assertEquals(Set.copyOf(disabledIdentifiers),
					Set.copyOf(identifiers(loader.getDisabledServerRecipes())));
			for (EnhancedRecipe customRecipe : customRecipes) {
				List<RecipeWrapper> matches = loader.findMatchingRecipe(
						RecipeType.WORKBENCH, customRecipe.getContent());
				assertEquals(1, matches.size());
				assertEquals(customRecipe.getKey(), matches.get(0).getRecipeKey());
			}
		}
		assertEquals(11, category.getRecipeCoreData().size());
	}

	@Test
	void removedAndInvalidRecipesAreNotRegisteredDuringRebuild() {
		WBRecipe active = customRecipe("active", Material.STONE);
		WBRecipe removed = customRecipe("removed", Material.DIRT);
		WBRecipe invalid = customRecipe("invalid", Material.GRAVEL);
		when(removed.isRemove()).thenReturn(true);
		when(invalid.validate()).thenReturn("Missing result");
		// A stale registration must also disappear when its cached recipe was deleted.
		put(removed.getServerRecipe());
		RecipeLoader loader = new RecipeLoader(server, categories);

		loader.rebuildRecipes(Arrays.asList(active, removed, invalid), Collections.emptyList());

		assertSame(active.getServerRecipe(), serverRecipes.get(keyOf(active.getServerRecipe())));
		assertNull(serverRecipes.get(keyOf(removed.getServerRecipe())));
		assertNull(serverRecipes.get(keyOf(invalid.getServerRecipe())));
		assertTrue(loader.findMatchingRecipe(RecipeType.WORKBENCH, removed.getContent()).isEmpty());
		assertTrue(loader.findMatchingRecipe(RecipeType.WORKBENCH, invalid.getContent()).isEmpty());
		assertEquals(1, serverRecipes.size());
	}

	@Test
	void missingOrMalformedDisabledKeysDoNotRemoveRecipesWithTheSameOutput() {
		Recipe foreignRecipe = shapeless("thirdparty:paper", Material.WHEAT, Material.PAPER);
		put(foreignRecipe);
		RecipeLoader loader = new RecipeLoader(server, categories);

		loader.rebuildRecipes(Collections.emptyList(),
				Arrays.asList("minecraft:missing", "minecraft:bad key"));

		assertSame(foreignRecipe, serverRecipes.get(keyOf(foreignRecipe)));
		assertTrue(loader.getDisabledServerRecipes().isEmpty());
		assertEquals(1, loader.getLoadedServerRecipes().size());
	}

	@Test
	void refreshesVanillaLookupWhenARecipeChangesIngredientsDuringResourceReload() {
		Recipe oldRecipe = shapeless("minecraft:planks", Material.OAK_LOG, Material.OAK_PLANKS);
		put(oldRecipe);
		RecipeLoader loader = new RecipeLoader(server, categories);
		assertSame(oldRecipe, loader.findMatchingRecipe(RecipeType.WORKBENCH,
				new ItemStack[]{item(Material.OAK_LOG)}).get(0).getRecipe());
		Recipe newRecipe = shapeless("minecraft:planks", Material.BIRCH_LOG, Material.OAK_PLANKS);
		serverRecipes.clear();
		put(newRecipe);

		loader.rebuildRecipes(Collections.emptyList(), Collections.emptyList());

		assertTrue(loader.findMatchingRecipe(RecipeType.WORKBENCH,
				new ItemStack[]{item(Material.OAK_LOG)}).isEmpty());
		List<RecipeWrapper> matches = loader.findMatchingRecipe(RecipeType.WORKBENCH,
				new ItemStack[]{item(Material.BIRCH_LOG)});
		assertEquals(1, matches.size());
		assertSame(newRecipe, matches.get(0).getRecipe());
		assertEquals(Collections.singletonList(newRecipe), loader.getLoadedServerRecipes());
	}

	@Test
	void disablesAnExactServerKeyEvenWhenItsRecipeWasExcludedFromTheLookupCache() {
		Recipe disabledRecipe = shapeless("minecraft:brewing_stand", Material.BLAZE_ROD,
				Material.BREWING_STAND);
		Recipe foreignRecipe = shapeless("thirdparty:brewing_stand", Material.STICK,
				Material.BREWING_STAND);
		when(cache.isCustomRecipe(disabledRecipe)).thenReturn(true);
		put(disabledRecipe);
		put(foreignRecipe);
		RecipeLoader loader = new RecipeLoader(server, categories);
		assertFalse(loader.getLoadedServerRecipes().contains(disabledRecipe));

		loader.rebuildRecipes(Collections.emptyList(),
				Collections.singletonList("minecraft:brewing_stand"));

		assertNull(serverRecipes.get(keyOf(disabledRecipe)));
		assertSame(foreignRecipe, serverRecipes.get(keyOf(foreignRecipe)));
		assertEquals(Collections.singletonList(disabledRecipe), loader.getDisabledServerRecipes());
	}

	private void assertRestored(final RecipeLoader loader, final List<EnhancedRecipe> customRecipes,
	                            final List<String> disabledIdentifiers, final Recipe foreignRecipe) {
		for (EnhancedRecipe customRecipe : customRecipes) {
			assertSame(customRecipe.getServerRecipe(),
					serverRecipes.get(keyOf(customRecipe.getServerRecipe())));
		}
		for (String disabledIdentifier : disabledIdentifiers)
			assertNull(serverRecipes.get(NamespacedKey.fromString(disabledIdentifier)));
		assertEquals(5, loader.getDisabledServerRecipes().size());
		assertSame(foreignRecipe, serverRecipes.get(keyOf(foreignRecipe)));
	}

	private List<EnhancedRecipe> customRecipes() {
		List<EnhancedRecipe> recipes = new ArrayList<>();
		for (int index = 0; index < CUSTOM_KEYS.size(); index++)
			recipes.add(customRecipe(CUSTOM_KEYS.get(index), CUSTOM_INPUTS[index]));
		return recipes;
	}

	private static WBRecipe customRecipe(final String key, final Material ingredient) {
		WBRecipe recipe = mock(WBRecipe.class);
		Recipe serverRecipe = shapeless("craftenhance:cehrecipe" + key, ingredient, Material.GUNPOWDER);
		ItemStack ingredientStack = item(ingredient);
		ItemStack resultStack = serverRecipe.getResult();
		when(recipe.getKey()).thenReturn(key);
		when(recipe.getType()).thenReturn(RecipeType.WORKBENCH);
		when(recipe.getRecipeCategory()).thenReturn("default");
		when(recipe.getGroup()).thenReturn("Patriam");
		when(recipe.getContent()).thenReturn(new ItemStack[]{ingredientStack});
		when(recipe.getResult()).thenReturn(resultStack);
		when(recipe.getServerRecipe()).thenReturn(serverRecipe);
		when(recipe.isShapeless()).thenReturn(true);
		return recipe;
	}

	private static List<Recipe> disabledRecipes() {
		List<Recipe> recipes = new ArrayList<>();
		for (int index = 0; index < 5; index++)
			recipes.add(shapeless("minecraft:disabled_" + index, Material.STRING, Material.WHITE_WOOL));
		return recipes;
	}

	private static Recipe shapeless(final String identifier, final Material ingredient,
	                               final Material result) {
		ShapelessRecipe recipe = mock(ShapelessRecipe.class);
		RecipeChoice choice = mock(RecipeChoice.class);
		ItemStack ingredientStack = item(ingredient);
		ItemStack resultStack = item(result);
		when(choice.getItemStack()).thenReturn(ingredientStack);
		when(recipe.getKey()).thenReturn(NamespacedKey.fromString(identifier));
		when(recipe.getChoiceList()).thenReturn(Collections.singletonList(choice));
		when(recipe.getResult()).thenReturn(resultStack);
		return recipe;
	}

	private static ItemStack item(final Material material) {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getType()).thenReturn(material);
		return stack;
	}

	private static NamespacedKey keyOf(final Recipe recipe) {
		return ((Keyed) recipe).getKey();
	}

	private static List<String> identifiers(final List<Recipe> recipes) {
		return recipes.stream().map(recipe -> keyOf(recipe).toString()).collect(Collectors.toList());
	}

	private void put(final Recipe recipe) {
		serverRecipes.put(keyOf(recipe), recipe);
	}

	private void putAll(final List<Recipe> recipes) {
		recipes.forEach(this::put);
	}
}
