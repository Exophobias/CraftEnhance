package com.dutchjelly.craftenhance.commands.edititem;

import com.dutchjelly.craftenhance.messaging.Messenger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.dutchjelly.craftenhance.commandhandling.ICommand;
import com.dutchjelly.craftenhance.commandhandling.CommandRoute;
import com.dutchjelly.craftenhance.commandhandling.CustomCmdHandler;

@CommandRoute(cmdPath="edititem.localizedname", perms="perms.item-editor")
public class LocalizedNameCmd implements ICommand {

	
	private CustomCmdHandler handler;
	
	public LocalizedNameCmd(CustomCmdHandler handler){
		this.handler = handler;
	}
	
	@Override
	public String getDescription() {
		return "localized names are unavailable on this server";
	}

	@Override
	public void handlePlayerCommand(Player p, String[] args) {
		Messenger.Message("This command is not supported in this version of the plugin.");
		/*
		ItemCreator creator = new ItemCreator(p.getInventory().getItemInMainHand(), args);
		ParseResult result = creator.setLocalizedName();
		p.getInventory().setItemInMainHand(creator.getItem());
		handler.getMain().getMessenger().message(result.getMessage(), p);*/
	}

	@Override
	public void handleConsoleCommand(CommandSender sender, String[] args) {
		Messenger.MessageFromConfig("messages.commands.only-for-players", sender);
	}

}
