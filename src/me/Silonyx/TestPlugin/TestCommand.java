package me.Silonyx.TestPlugin;

import java.util.ArrayList;
import java.util.Queue;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

public class TestCommand implements CommandExecutor {
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            
            
            ArrayList q = (ArrayList) player.getWorld().getPlayers();
            Player p2 = null;
            
            for(int i=0; i<q.size(); i++) {
            	
	            if(q.get(i).equals(player)) {
	            }
	            else {
	            	p2 =(Player)(q.get(i));
	            }
            }
	            
            Block block = player.getTargetBlock(null, 250);
            Location loc = block.getLocation();
            p2.teleport(loc);
            player.getWorld().spawnEntity(loc, EntityType.CREEPER);
            //BIG CHANGES TO BE MADE
            
            return true;
        }
        return false;
	}

}
