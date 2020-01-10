package me.Silonyx.TestPlugin;

import java.util.HashSet;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TestCommand implements CommandExecutor {
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            
            HashSet<Material> transparent = new HashSet<Material>();
            transparent.add(Material.AIR);
            Block block = player.getTargetBlock(transparent, 250);
            player.getWorld().strikeLightning(block.getLocation());
            //BIG CHANGES TO BE MADE
            
            return true;
        }
        return false;
	}

}
