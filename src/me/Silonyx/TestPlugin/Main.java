package me.Silonyx.TestPlugin;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
	
	@Override
	public void onEnable() {	
	this.getCommand("hello").setExecutor(new TestCommand());
	}
	
	@Override
	public void onDisable() {
		
	}

}
