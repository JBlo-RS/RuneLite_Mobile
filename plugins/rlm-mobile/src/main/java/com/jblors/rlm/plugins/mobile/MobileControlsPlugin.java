package com.jblors.rlm.plugins.mobile;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Tells the RuneLite Mobile app which side panel tab is open and which keys open the inventory
 * and prayer tabs (the game's own keybinds), for the app's Pray/Inv button. Shared through
 * system properties, which the app reads from the JVM.
 */
@PluginDescriptor(
	name = "Mobile Controls",
	description = "Lets RuneLite Mobile's Pray/Inv button press your own keybinds for the prayer and inventory tabs",
	tags = {"mobile", "android", "prayer", "inventory", "keybind", "rlm"}
)
public class MobileControlsPlugin extends Plugin
{
	static final String TAB = "rlm.tab", KEY_INV = "rlm.key.inv", KEY_PRAYER = "rlm.key.prayer";

	@Inject
	private Client client;

	@Inject
	private MobileControlsConfig config;

	private boolean wasLoggedIn;

	/**
	 * Logging out (back to the login screen; world hops don't go there) ends the session: close
	 * RuneLite so the app returns to its launch screen. System.exit runs RuneLite's shutdown
	 * hooks (settings are saved); the app's JVM process then ends.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			wasLoggedIn = true;
		}
		else if (e.getGameState() == GameState.LOGIN_SCREEN && wasLoggedIn && config.closeOnLogout())
		{
			wasLoggedIn = false;
			System.out.println("rlm-mobile: logged out; closing RuneLite");
			Thread t = new Thread(() ->
			{
				try
				{
					Thread.sleep(500); // let the logout finish
				}
				catch (InterruptedException ignored)
				{
					// exit anyway
				}
				System.exit(0);
			}, "rlm-logout-exit");
			t.setDaemon(true);
			t.start();
		}
	}

	@Provides
	MobileControlsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MobileControlsConfig.class);
	}

	@Override
	protected void shutDown()
	{
		System.clearProperty(TAB);
		System.clearProperty(KEY_INV);
		System.clearProperty(KEY_PRAYER);
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		publish();
	}

	/** Also refreshed every frame-ish via the client tick, so a tab switch shows up quickly. */
	@Subscribe
	public void onClientTick(net.runelite.api.events.ClientTick e)
	{
		if (client.getGameCycle() % 10 == 0)
		{
			publish();
		}
	}

	private void publish()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			System.clearProperty(TAB);
			return;
		}
		// Keybind varbits: 0 = none, 1-12 = F1-F12, 13 = Esc
		System.setProperty(KEY_INV, Integer.toString(client.getVarbitValue(VarbitID.STONE_INV_KEY)));
		System.setProperty(KEY_PRAYER, Integer.toString(client.getVarbitValue(VarbitID.STONE_PRAYER_KEY)));
		// 3 = inventory, 5 = prayer
		System.setProperty(TAB, Integer.toString(client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL)));
	}
}
