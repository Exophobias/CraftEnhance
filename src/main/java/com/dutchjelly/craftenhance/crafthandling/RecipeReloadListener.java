package com.dutchjelly.craftenhance.crafthandling;

import com.dutchjelly.craftenhance.CraftEnhance;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Paper replaces the recipe manager during a datapack reload. Restore our runtime registrations. */
public final class RecipeReloadListener implements Listener {
	private final CraftEnhance plugin;

	public RecipeReloadListener(final CraftEnhance plugin) {
		this.plugin = plugin;
	}

	// Index other plugins' restored recipes after their normal-priority registration handlers.
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onResourcesReloaded(final ServerResourcesReloadedEvent event) {
		plugin.restoreRecipesAfterResourceReload();
	}
}
