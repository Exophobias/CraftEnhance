package com.dutchjelly.craftenhance.commandhandling;

import com.dutchjelly.craftenhance.CraftEnhance;
import com.dutchjelly.craftenhance.messaging.Messenger;
import java.util.*;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CommandHelpTest {
    private CustomCmdHandler handler;
    private CommandSender sender;
    private final List<String> output = new ArrayList<>();
    @BeforeEach void setup() {
        CraftEnhance plugin = mock(CraftEnhance.class);
        PluginDescriptionFile description = mock(PluginDescriptionFile.class);
        when(description.getCommands()).thenReturn(Collections.emptyMap());
        when(plugin.getDescription()).thenReturn(description);
        YamlConfiguration config = new YamlConfiguration();
        config.set("perms.recipe-viewer", "craftenhance.view");
        config.set("perms.recipe-editor", "craftenhance.edit");
        config.set("global-prefix", "");
        when(plugin.getConfig()).thenReturn(config);
        handler = new CustomCmdHandler(plugin);
        sender = mock(CommandSender.class);
        doAnswer(call -> { output.add(ChatColor.stripColor(call.getArgument(0, String.class))); return null; }).when(sender).sendMessage(anyString());
        Messenger.Init(plugin);
    }
    static class Stub implements ICommand {
        int calls;
        public String getDescription() { return "show the feature"; }
        public void handlePlayerCommand(Player p, String[] args) { calls++; }
        public void handleConsoleCommand(CommandSender p, String[] args) { calls++; }
    }
    @CommandRoute(cmdPath={"ceh.viewer", "recipes"}, perms="perms.recipe-viewer") static class View extends Stub { }
    @CommandRoute(cmdPath={"ceh.reload", "ceh.rl"}, perms="perms.recipe-editor") static class Reload extends Stub { }
    @CommandRoute(cmdPath="ceh.test", perms="ceh.debug") static class Debug extends Stub { }
    @CommandRoute(cmdPath="ceh.staff0", perms="perms.recipe-editor") static class Staff0 extends Stub { }
    @CommandRoute(cmdPath="ceh.staff1", perms="perms.recipe-editor") static class Staff1 extends Stub { }
    @CommandRoute(cmdPath="ceh.staff2", perms="perms.recipe-editor") static class Staff2 extends Stub { }
    @CommandRoute(cmdPath="ceh.staff3", perms="perms.recipe-editor") static class Staff3 extends Stub { }
    @CommandRoute(cmdPath="ceh.staff4", perms="perms.recipe-editor") static class Staff4 extends Stub { }
    @CommandRoute(cmdPath="ceh.staff5", perms="perms.recipe-editor") static class Staff5 extends Stub { }
    @CommandRoute(cmdPath="ceh.staff6", perms="perms.recipe-editor") static class Staff6 extends Stub { }
    @CommandRoute(cmdPath="ceh.staff7", perms="perms.recipe-editor") static class Staff7 extends Stub { }
    @CommandRoute(cmdPath="ceh.staff8", perms="perms.recipe-editor") static class Staff8 extends Stub { }
    @Test void ordinaryRootClampingAndTabsDoNotRevealStaff() {
        handler.loadCommandClasses(List.of(new View(), new Reload(), new Debug()));
        when(sender.hasPermission("craftenhance.view")).thenReturn(true);
        for (String[] args : List.of(new String[0], new String[]{"help"}, new String[]{"help","99"}, new String[]{"help","0"}, new String[]{"help","banana"})) {
            output.clear(); handler.handleCommand(sender,"ceh",args);
            assertEquals(3,output.size()); assertTrue(output.get(1).contains("/ceh viewer"));
            assertFalse(String.join("\n",output).contains("Staff"));
        }
        assertEquals(Set.of("viewer","help"),new HashSet<>(handler.onTabComplete(sender,null,"ceh",new String[]{""})));
        assertTrue(handler.onTabComplete(sender,null,"ceh",new String[]{"help",""}).isEmpty());
        output.clear();handler.handleCommand(sender,"ceh",new String[]{"help","staff"});
        assertEquals(List.of("You do not have permission to do that."),output);
    }
    @Test void partialStaffUsesSeparatePagesAndDeniedAliasesNeverExecute() {
        View view=new View(); Reload reload=new Reload(); Debug debug=new Debug();
        handler.loadCommandClasses(List.of(view,reload,debug));
        when(sender.hasPermission("craftenhance.view")).thenReturn(true);
        when(sender.hasPermission("craftenhance.edit")).thenReturn(true);
        handler.handleCommand(sender,"ceh",new String[0]);
        assertTrue(output.get(0).contains("(1/2)"));assertFalse(output.get(0).contains("Staff"));
        assertTrue(output.get(2).contains("help 2"));
        output.clear();handler.handleCommand(sender,"ceh",new String[]{"help","staff"});
        assertTrue(output.get(0).contains("Staff"));assertTrue(output.get(1).contains("reload"));
        assertFalse(String.join("\n",output).contains("test"));
        when(sender.hasPermission("craftenhance.edit")).thenReturn(false);
        handler.handleCommand(sender,"ceh",new String[]{"rl"});assertEquals(0,reload.calls);
        handler.handleCommand(sender,"ceh",new String[]{"test"});assertEquals(0,debug.calls);
        handler.handleCommand(sender,"recipes",new String[0]);assertEquals(1,view.calls);
    }
    @Test void staffPaginationHasEightRowsAndUnknownShowsFilteredHelp() {
        when(sender.hasPermission("craftenhance.edit")).thenReturn(true);
        handler.loadCommandClasses(List.of(new Staff0(),new Staff1(),new Staff2(),new Staff3(),new Staff4(),new Staff5(),new Staff6(),new Staff7(),new Staff8()));
        handler.handleCommand(sender,"ceh",new String[0]);assertEquals(10,output.size());
        assertTrue(output.get(0).contains("Staff"));assertTrue(output.get(9).contains("help 2"));
        output.clear();handler.handleCommand(sender,"ceh",new String[]{"help","99"});assertEquals(3,output.size());
        assertFalse(output.get(2).contains("for more"));
        output.clear();handler.handleCommand(sender,"ceh",new String[]{"unknown"});
        assertEquals("Unknown command.",output.get(0));assertTrue(output.get(1).contains("CraftEnhance"));
    }
}
