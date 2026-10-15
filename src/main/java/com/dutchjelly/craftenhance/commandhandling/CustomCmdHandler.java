package com.dutchjelly.craftenhance.commandhandling;

import com.dutchjelly.craftenhance.CraftEnhance;
import com.dutchjelly.craftenhance.messaging.Debug;
import com.dutchjelly.craftenhance.messaging.Messenger;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.lang.annotation.Annotation;
import java.util.*;

public class CustomCmdHandler implements TabCompleter{


	private Map<ICommand, CommandRoute> commandClasses;
	//private static final String cmdPackagePath = "com.dutchjelly.craftenhance.commands";

	private CraftEnhance main;

	//Pass main instance and register tab completion on all commands.
	public CustomCmdHandler(CraftEnhance main){
		commandClasses = new LinkedHashMap<>();
		this.main = main;
		main.getDescription().getCommands().keySet().forEach(x ->{
			main.getCommand(x).setTabCompleter(this);
		});
	}

	//Load all command classes in baseClasses so they can handle commands.
	public void loadCommandClasses(List<ICommand> baseClasses){
		if(baseClasses == null) return;
		baseClasses.forEach(x -> loadCommandClass(x));
	}

	//Load the command class baseClass by finding the annotation and
	//storing it in the commandClasses map.
	public void loadCommandClass(ICommand baseClass){
		if(baseClass == null) return;
		CommandRoute annotation = null;
		for(Annotation annotationItem : baseClass.getClass().getAnnotations()){
			if(annotationItem instanceof CommandRoute){
				annotation = (CommandRoute) annotationItem;
				break;
			}
		}
		if(annotation == null){
			Debug.error("Could not load the command class " + baseClass.getClass().getName());
			return;
		}
		commandClasses.put(baseClass, annotation);
	}


	//Get the main plugin instance.
	public CraftEnhance getMain(){
		return main;
	}

	//Assign command to a class and check for permissions.
	public boolean handleCommand(CommandSender sender, String label, String[] args){
		if (args.length == 0 && (label.equalsIgnoreCase("ceh") || label.equalsIgnoreCase("edititem"))
                || args.length > 0 && args[0].equalsIgnoreCase("help")) {
            showHelp(sender, args.length > 0 ? Arrays.copyOfRange(args, 1, args.length) : args);
            return true;
        }
        ICommand executor;
		args = pushLabelArg(label, args);

		executor = getExecutor(args);
		if(executor == null) {
            sender.sendMessage(ChatColor.RED + "Unknown command.");
            showHelp(sender, new String[0]);
            return true;
		}

		CommandRoute annotation = commandClasses.get(executor);
		if(!hasPermission(sender, annotation)){
			Messenger.MessageFromConfig("messages.global.no-perms", sender);
			return true;
		}

		String[] commandLabels = getMatchingPath(args, annotation).split("\\.");
		args = popArguments(commandLabels.length, args);

		if(args.length > 0 && args[0].equalsIgnoreCase("help")){
			Messenger.Message(executor.getDescription(), sender);
			return true;
		}

		if(sender instanceof Player)
			executor.handlePlayerCommand((Player)sender, args);
		else
			executor.handleConsoleCommand(sender, args);
		return true;
	}

	//Send options that could complete the given arguments to sender.
	private void sendOptions(String[] args, CommandSender sender){
		String[] emptyLast = new String[args.length+1];
		for(int i = 0; i < args.length; i++) {
			emptyLast[i] = args[i];
		}
		emptyLast[args.length] = "";
		String completions = String.join(", ", getTabCompleteMatches(sender, emptyLast));
		if(completions.equals("")) {
			Messenger.Message("That is not a command.", sender);
			return;
		}
		Messenger.MessageFromConfig("messages.commands.show-options", sender, completions);;
	}

	//Push the label argument to index 0 of the array of arguments.
	private String[] pushLabelArg(String label, String[] args){
		String[] pushed = new String[args.length+1];
		for(int i = 1; i < pushed.length; i++)
			pushed[i] = args[i-1];
		pushed[0] = label;
		return pushed;
	}

	//Handle tab completion by returning all tab completions.
	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label,String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            if (args.length == 2 && hasStaffHelp(sender) && "staff".startsWith(args[1].toLowerCase(Locale.ROOT)))
                return Collections.singletonList("staff");
            return Collections.emptyList();
        }
        List<String> tabCompletion = new ArrayList<>();

		ICommand executor = getExecutor(label, args.length > 0 ? args[0] : "");
		if (executor != null) {
			List<String> handleTabCompletion = executor.handleTabCompletion(sender, args);
			if (handleTabCompletion != null) {
				CommandRoute annotation = commandClasses.get(executor);
				if (hasPermission(sender, annotation)) {
					return handleTabCompletion;
				}
			}
		}
//		ICommand suitableExecutor = getExecutor(args);
//		if(suitableExecutor != null && suitableExecutor instanceof ICompletionProvider)
//			tabCompletion.addAll(((ICompletionProvider)suitableExecutor).getCompletions(args));
		tabCompletion.addAll(getTabCompleteMatches(sender, pushLabelArg(label, args)));
        if (args.length == 1 && "help".startsWith(args[0].toLowerCase(Locale.ROOT))) tabCompletion.add("help");
        return tabCompletion;
	}

	//Pop amount arguments recursively from args.
	private String[] popArguments(int amount, String[] args){
		if(amount == 0 || amount > args.length || args.length < 1) return args;
		String[] popped = new String[args.length-1];
		for(int i = 0; i < args.length-1; i++){
			popped[i] = args[i+1];
		}
		return popArguments(amount-1, popped);
	}

	//Get the executor object from given args.
	public ICommand getExecutor(String... args){
		int maxMatching = -1, currentMatch;
		ICommand bestMatch = null;
		for(ICommand cmdClass : commandClasses.keySet()){
			CommandRoute annotation = commandClasses.get(cmdClass);
			String matchingPath = getMatchingPath(args, annotation);
			if(matchingPath == null) continue;
			currentMatch = matchingPath.split("\\.").length;
			if(currentMatch > maxMatching){
				maxMatching = currentMatch;
				bestMatch = cmdClass;
			}
		}
		return bestMatch;
	}

	//Get the best matching path with args of all paths in the annotation.
	private String getMatchingPath(String [] args, CommandRoute annotation){
		int bestMatch = -1, currentMatch;
		String bestMatchingPath = null;
		for(String path : annotation.cmdPath()){
			currentMatch = cmdPathMatches(args, path);
			if(currentMatch > bestMatch){
				bestMatch = currentMatch;
				bestMatchingPath = path;
			}
		}
		return bestMatchingPath;
	}

	//Check if the array of strings match with the path by returning the length
	//of the found path. Returns -1 if it doesn't match.
	private int cmdPathMatches(String[] args, String path){
		String[] splitPath = path.split("\\.");
		if(splitPath.length > args.length) return -1;
		for(int i = 0; i < splitPath.length; i++)
			if(!args[i].equalsIgnoreCase(splitPath[i]))
				return -1;
		return splitPath.length;
	}

	/*
	private boolean aliasMatches(String argument, String pathSection){

	}
	*/

	//Returns if the sender has permission for command class with annotation.
	private boolean hasPermission(CommandSender sender, CommandRoute annotation){
		if( annotation.perms() == null || annotation.perms().equals(""))
			return true;
        String perms = annotation.perms().startsWith("perms.")
                ? main.getConfig().getString(annotation.perms()) : annotation.perms();
        if (perms == null) return false;
        return perms.trim().isEmpty() || sender.hasPermission(perms);
	}

	//Find all possible tab completions for args.
	private List<String> getTabCompleteMatches(CommandSender sender, String[] args){
		List<String> completions = new ArrayList<String>();
		String completion;
		for(ICommand cmdClass : commandClasses.keySet()){
			CommandRoute annotation = commandClasses.get(cmdClass);
            if (!hasPermission(sender, annotation)) continue;
			for(String path : annotation.cmdPath()){
				completion = getCompletion(args, path);
				if(completion == null) continue;
				completions.add(completion);
			}
		}
		return completions;
	}

	//Returns the possible completion of args to result in path. So returns null
	//if there is none.
	private String getCompletion(String[] args, String path){
		String[] splitPath = path.split("\\.");
		if(args.length > splitPath.length) return null;
		for(int i = 0; i < args.length-1; i++){
			if(!args[i].equalsIgnoreCase(splitPath[i])) return null;
		}
		if(!splitPath[args.length-1].toLowerCase().startsWith(args[args.length-1].toLowerCase()))
			return null;
		return splitPath[args.length-1];
	}



    private boolean staff(CommandRoute route) {
        return !route.perms().equals("perms.recipe-viewer");
    }

    private boolean hasStaffHelp(CommandSender sender) {
        return commandClasses.values().stream().anyMatch(route -> staff(route) && hasPermission(sender, route));
    }

    private void showHelp(CommandSender sender, String[] args) {
        List<Map.Entry<ICommand, CommandRoute>> ordinary = new ArrayList<>();
        List<Map.Entry<ICommand, CommandRoute>> staff = new ArrayList<>();
        for (Map.Entry<ICommand, CommandRoute> entry : commandClasses.entrySet()) {
            if (hasPermission(sender, entry.getValue()))
                (staff(entry.getValue()) ? staff : ordinary).add(entry);
        }
        staff.sort(Comparator.comparing(entry -> entry.getValue().cmdPath()[0].endsWith(".test")));
        boolean directStaff = args.length > 0 && args[0].equalsIgnoreCase("staff");
        if (directStaff && staff.isEmpty() || ordinary.isEmpty() && staff.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to do that.");
            return;
        }
        int ordinaryPages = (ordinary.size() + 7) / 8;
        int totalPages = ordinaryPages + (staff.size() + 7) / 8;
        int requested = 1;
        try { requested = Integer.parseInt(args[directStaff ? 1 : 0]); }
        catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) { }
        int page = directStaff ? ordinaryPages + Math.max(1, Math.min((staff.size() + 7) / 8, requested))
                : Math.max(1, Math.min(totalPages, requested));
        boolean staffPage = page > ordinaryPages;
        List<Map.Entry<ICommand, CommandRoute>> entries = staffPage ? staff : ordinary;
        int offset = ((staffPage ? page - ordinaryPages : page) - 1) * 8;
        sender.sendMessage(ChatColor.DARK_GRAY + "---------- " + ChatColor.GREEN + "CraftEnhance"
                + (staffPage ? " Staff" : "") + (totalPages > 1 ? ChatColor.DARK_GRAY + " ("
                + ChatColor.WHITE + page + "/" + totalPages + ChatColor.DARK_GRAY + ")" : "")
                + ChatColor.DARK_GRAY + " ----------");
        for (int i = offset; i < Math.min(entries.size(), offset + 8); i++) {
            Map.Entry<ICommand, CommandRoute> entry = entries.get(i);
            String description = entry.getKey().getDescription().replaceAll("[\\r\\n]+", " ");
            int sentenceEnd = description.indexOf(". ");
            if (sentenceEnd >= 0) description = description.substring(0, sentenceEnd);
            if (description.isEmpty()) description = "change the recipe permission";
            sender.sendMessage(ChatColor.GREEN + "/" + entry.getValue().cmdPath()[0].replace('.', ' ')
                    + ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + description);
        }
        sender.sendMessage(ChatColor.DARK_GRAY + (page < totalPages
                ? "---------- /ceh help " + (page + 1) + " for more ----------"
                : "----------------------------------------"));
    }

}