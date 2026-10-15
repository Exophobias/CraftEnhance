package com.dutchjelly.craftenhance.commandhandling;

import com.dutchjelly.craftenhance.CraftEnhance;
import com.dutchjelly.craftenhance.commands.ceh.*;
import com.dutchjelly.craftenhance.commands.edititem.*;
import com.dutchjelly.craftenhance.messaging.Messenger;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RealCommandHelpTest {
    private CraftEnhance plugin;
    private CustomCmdHandler handler;
    private CommandSender console;
    private final List<String> output = new ArrayList<>();
    @BeforeEach void setup() {
        plugin = mock(CraftEnhance.class);
        var description = mock(PluginDescriptionFile.class);
        when(description.getCommands()).thenReturn(Collections.emptyMap());
        when(plugin.getDescription()).thenReturn(description);
        var config = new YamlConfiguration();
        config.set("perms.recipe-viewer", "craftenhance.view");
        config.set("perms.recipe-editor", "craftenhance.edit");
        config.set("perms.item-editor", "craftenhance.edititems");
        config.set("global-prefix", "");
        config.set("messages.commands.few-arguments", "Expected [PLACEHOLDER] arguments");
        config.set("messages.commands.only-for-players", "Only players may execute this command");
        config.set("messages.global.no-perms", "Permission denied");
        when(plugin.getConfig()).thenReturn(config);
        Messenger.Init(plugin);
        handler = new CustomCmdHandler(plugin);
        console = mock(CommandSender.class);
        capture(console);
    }
    private void capture(CommandSender sender) {
        doAnswer(call -> { output.add(ChatColor.stripColor(call.getArgument(0, String.class))); return null; })
                .when(sender).sendMessage(anyString());
    }
    private void registerActualCommands() {
        handler.loadCommandClasses(List.of(new DisplayNameCmd(handler), new DurabilityCmd(handler),
                new EnchantCmd(handler), new ItemFlagCmd(handler), new LocalizedNameCmd(handler), new LoreCmd(handler),
                new CreateRecipeCmd(handler), new RecipesCmd(handler), new SpecsCommand(handler),
                new ChangeKeyCmd(handler), new CleanItemFileCmd(handler), new SetPermissionCmd(handler),
                new ReloadCmd(), new Disabler(handler), new RemoveRecipeCmd(handler), new CustomTable()));
    }
    @Test void realOrdinaryHelpIncludesTheCraftingTableWithoutNullDescriptionOrStaffLeak() {
        registerActualCommands();
        when(console.hasPermission("craftenhance.view")).thenReturn(true);
        assertDoesNotThrow(() -> handler.handleCommand(console, "ceh", new String[0]));
        assertEquals(4, output.size());
        assertTrue(output.get(1).contains("/recipes [page <number>|category <category> [recipe]]"));
        assertTrue(output.get(2).contains("/ceh customtable - open the enhanced crafting table"));
        assertFalse(String.join("\n", output).contains("Staff"));
        assertFalse(String.join("\n", output).contains("specs"));
    }
    @Test void actualStaffHelpDocumentsRequiredArgumentsAndUnsupportedLegacyFeatureAccurately() {
        registerActualCommands();
        when(console.hasPermission(anyString())).thenReturn(true);
        var allPages = new ArrayList<String>();
        for (String page : List.of("1", "2", "3")) {
            output.clear();
            handler.handleCommand(console, "ceh", new String[]{"help", page});
            assertTrue(output.size() <= 10);
            allPages.addAll(output);
        }
        String help = String.join("\n", allPages);
        assertTrue(help.contains("/ceh specs <key> - show a recipe key and permission"));
        assertTrue(help.contains("/ceh setpermission <key> <permission> - change a recipe permission"));
        assertTrue(help.contains("/ceh removerecipe <key> - remove an existing custom recipe"));
        assertTrue(help.contains("/ceh createrecipe [type key [permission]]"));
        assertTrue(help.contains("/edititem lore <line> [text...]"));
        assertTrue(help.contains("/edititem localizedname - localized names are unavailable"));
        assertFalse(output.getLast().contains("for more"));
        verify(plugin, never()).getCacheRecipes();
    }
    @Test void realRequiredArgumentAndPermissionHandlersRemainIndependentOfHelpMetadata() {
        handler.loadCommandClasses(List.of(new SpecsCommand(handler), new SetPermissionCmd(handler)));
        Player player = mock(Player.class);
        capture(player);
        when(player.hasPermission("craftenhance.edit")).thenReturn(true);
        handler.handleCommand(player, "ceh", new String[]{"specs"});
        assertEquals("Expected 1 arguments", output.getLast());
        handler.handleCommand(player, "ceh", new String[]{"setpermission", "recipe-key"});
        assertEquals("Expected 2 arguments", output.getLast());
        output.clear();
        handler.handleCommand(player, "ceh", new String[]{"specs", "help"});
        assertEquals(3, output.size());
        assertTrue(output.get(1).contains("/ceh specs <key> - show a recipe key and permission"));
        when(player.hasPermission("craftenhance.edit")).thenReturn(false);
        output.clear();
        handler.handleCommand(player, "ceh", new String[]{"specs", "recipe-key"});
        assertEquals(List.of("Permission denied"), output);
        verify(plugin, never()).getCacheRecipes();
    }
    @Test void consoleRecipeCompletionDoesNotEnterPlayerOnlyVisibilityOrInventoryContext() {
        handler.loadCommandClasses(List.of(new RecipesCmd(handler)));
        when(console.hasPermission("craftenhance.view")).thenReturn(true);
        assertEquals(List.of(), handler.onTabComplete(console, null, "ceh", new String[]{"viewer", "category", "any", ""}));
        handler.handleCommand(console, "recipes", new String[0]);
        assertEquals(List.of("Only players may execute this command"), output);
        verify(plugin, never()).getCacheRecipes();
    }
}
