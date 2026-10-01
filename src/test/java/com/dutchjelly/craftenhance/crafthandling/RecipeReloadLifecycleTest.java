package com.dutchjelly.craftenhance.crafthandling;

import com.dutchjelly.craftenhance.CraftEnhance;
import com.dutchjelly.craftenhance.cache.CacheRecipes;
import com.dutchjelly.craftenhance.crafthandling.recipes.EnhancedRecipe;
import com.dutchjelly.craftenhance.crafthandling.recipes.WBRecipe;
import com.dutchjelly.craftenhance.updatechecking.VersionChecker;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ShapelessRecipe;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecipeReloadLifecycleTest {
	@Test
	void earlyResourceReloadLeavesRegistrationToStartup() {
		CraftEnhance plugin = mock(CraftEnhance.class);
		doCallRealMethod().when(plugin).restoreRecipesAfterResourceReload();
		try (MockedStatic<RecipeLoader> loaders = mockStatic(RecipeLoader.class);
		     MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			new RecipeReloadListener(plugin).onResourcesReloaded(event());
			loaders.verifyNoInteractions();
			verify(plugin, never()).reLearnRecipes();
		}
	}

	@Test
	void restoresBeforeClientSyncAndOnlineDiscoveryUsingTheCurrentDisabledKeys() throws Exception {
		CraftEnhance plugin = mock(CraftEnhance.class);
		CacheRecipes cache = mock(CacheRecipes.class);
		RecipeLoader loader = mock(RecipeLoader.class);
		RecipeInjector injector = mock(RecipeInjector.class);
		Player player = mock(Player.class);
		YamlConfiguration config = new YamlConfiguration();
		config.set("learn-recipes", true);
		ShapelessRecipe disabled = mock(ShapelessRecipe.class);
		when(disabled.getKey()).thenReturn(NamespacedKey.minecraft("brewing_stand"));
		VersionChecker version = mock(VersionChecker.class);
		when(version.newerThan(any(VersionChecker.ServerVersion.class))).thenReturn(true);
		when(plugin.getVersionChecker()).thenReturn(version);
		when(plugin.getConfig()).thenReturn(config);
		when(plugin.getLogger()).thenReturn(Logger.getLogger("RecipeReloadLifecycleTest"));
		when(loader.getDisabledServerRecipes()).thenReturn(Collections.singletonList(disabled));
		List<EnhancedRecipe> recipes = Collections.singletonList(mock(WBRecipe.class));
		when(cache.getListOfRecipes()).thenReturn(recipes);
		setField(plugin, "cacheRecipes", cache);
		setField(plugin, "injector", injector);
		setField(plugin, "recipesLoaded", true);
		doCallRealMethod().when(plugin).restoreRecipesAfterResourceReload();
		doCallRealMethod().when(plugin).reLearnRecipes();
		List<String> order = new ArrayList<>();
		doAnswer(invocation -> { order.add("registered"); return null; })
				.when(loader).rebuildRecipes(anyList(), anyList());
		doAnswer(invocation -> { order.add("discovered"); return null; })
				.when(injector).discoverRecipes(player);
		try (MockedStatic<CraftEnhance> self = mockStatic(CraftEnhance.class);
		     MockedStatic<RecipeLoader> loaders = mockStatic(RecipeLoader.class);
		     MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			self.when(CraftEnhance::self).thenReturn(plugin);
			loaders.when(RecipeLoader::getInstance).thenReturn(loader);
			bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
			bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Collections.singletonList(player));
			bukkit.when(Bukkit::updateRecipes).thenAnswer(invocation -> { order.add("synced"); return null; });
			new RecipeReloadListener(plugin).onResourcesReloaded(event());
			assertEquals(List.of("registered", "synced", "discovered"), order);
			verify(loader).rebuildRecipes(recipes, Collections.singletonList("minecraft:brewing_stand"));
			verify(cache).setGroupCacheDirty(true);
			verify(plugin, never()).reloadConfig();
		}
	}

	@Test
	void rediscoveryPreservesVisibilityAndPermissionPoliciesAndOtherNamespaces() {
		CraftEnhance plugin = mock(CraftEnhance.class);
		CacheRecipes cache = mock(CacheRecipes.class);
		RecipeInjector injector = mock(RecipeInjector.class);
		Player player = mock(Player.class);
		YamlConfiguration config = new YamlConfiguration();
		config.set("learn-recipes", true);
		config.set("only-show-available", true);
		config.set("perms.recipe-editor", "craftenhance.edit");
		VersionChecker version = mock(VersionChecker.class);
		when(version.newerThan(any(VersionChecker.ServerVersion.class))).thenReturn(true);
		when(plugin.getVersionChecker()).thenReturn(version);
		when(plugin.getConfig()).thenReturn(config);
		when(plugin.getCacheRecipes()).thenReturn(cache);
		WBRecipe allowed = recipe("allowed", "recipe.allowed", false, false);
		WBRecipe denied = recipe("denied", "recipe.denied", false, false);
		WBRecipe hidden = recipe("hidden", "recipe.allowed", true, false);
		WBRecipe removed = recipe("removed", "recipe.allowed", false, true);
		when(cache.getListOfRecipes()).thenReturn(List.of(allowed, denied, hidden, removed));
		when(player.hasPermission("recipe.allowed")).thenReturn(true);
		NamespacedKey own = NamespacedKey.fromString("craftenhance:old");
		NamespacedKey foreign = NamespacedKey.fromString("othercraftenhance:keep");
		when(player.getDiscoveredRecipes()).thenReturn(Set.of(own, foreign));
		doCallRealMethod().when(injector).discoverRecipes(player);
		try (MockedStatic<CraftEnhance> self = mockStatic(CraftEnhance.class)) {
			self.when(CraftEnhance::self).thenReturn(plugin);
			injector.discoverRecipes(player);
			verify(player).undiscoverRecipe(own);
			verify(player, never()).undiscoverRecipe(foreign);
			verify(player).discoverRecipe(key(allowed));
			verify(player, never()).discoverRecipe(key(denied));
			verify(player, never()).discoverRecipe(key(hidden));
			verify(player, never()).discoverRecipe(key(removed));
		}
	}

	@Test
	void disabledAutomaticLearningLeavesThePlayersRecipeBookAlone() {
		CraftEnhance plugin = mock(CraftEnhance.class);
		RecipeInjector injector = mock(RecipeInjector.class);
		Player player = mock(Player.class);
		when(plugin.getConfig()).thenReturn(new YamlConfiguration());
		doCallRealMethod().when(injector).discoverRecipes(player);
		try (MockedStatic<CraftEnhance> self = mockStatic(CraftEnhance.class)) {
			self.when(CraftEnhance::self).thenReturn(plugin);
			injector.discoverRecipes(player);
			verify(player, never()).getDiscoveredRecipes();
			verify(player, never()).discoverRecipe(any(NamespacedKey.class));
		}
	}

	private static WBRecipe recipe(final String name, final String permission, final boolean hidden,
	                               final boolean removed) {
		WBRecipe enhanced = mock(WBRecipe.class);
		ShapelessRecipe serverRecipe = mock(ShapelessRecipe.class);
		when(serverRecipe.getKey()).thenReturn(NamespacedKey.fromString("craftenhance:" + name));
		when(enhanced.getServerRecipe()).thenReturn(serverRecipe);
		when(enhanced.getPermission()).thenReturn(permission);
		when(enhanced.isHidden()).thenReturn(hidden);
		when(enhanced.isRemove()).thenReturn(removed);
		return enhanced;
	}

	private static NamespacedKey key(final WBRecipe recipe) {
		return ((ShapelessRecipe) recipe.getServerRecipe()).getKey();
	}

	private static ServerResourcesReloadedEvent event() {
		return new ServerResourcesReloadedEvent(ServerResourcesReloadedEvent.Cause.COMMAND);
	}

	private static void setField(final CraftEnhance plugin, final String name, final Object value) throws Exception {
		Field field = CraftEnhance.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}
}
