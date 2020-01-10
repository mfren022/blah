package io.github.silonyx.name.test;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Test extends JavaPlugin {
	
	@Override
    public void onEnable() {
		getLogger().info("onEnable has been invoked!");
		
    }
    
    @Override
    public void onDisable() {
    	getLogger().info("onDisable has been invoked!");
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
      
    	if(label.equalsIgnoreCase("ignite")) {
    		
    		if(sender instanceof Player) {
    			Player player = (Player)sender;
    			player.sendMessage("woo i did it");
    			return true;
    		}
    		else {
    			sender.sendMessage("wat");
    			return false;
    		}
    	}
    	return false;
    }
}
