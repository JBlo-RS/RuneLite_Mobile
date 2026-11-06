package com.jblors.rlm.plugins.mobile;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
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
	description = "Lets RuneLite Mobile's Pray/Inv button press your own keybinds, and tells the app where the minimap, inventory and chat are for its interface scale",
	tags = {"mobile", "android", "prayer", "inventory", "keybind", "rlm"}
)
public class MobileControlsPlugin extends Plugin
{
	static final String TAB = "rlm.tab", KEY_INV = "rlm.key.inv", KEY_PRAYER = "rlm.key.prayer";
	/** Interface panels the app may draw enlarged: "x,y,w,h,ANCHOR;..." in canvas pixels */
	static final String UI_REGIONS = "rlm.ui.regions";

	/** {map, side panel, chat} containers of the two resizable layouts, and their anchor corners */
	private static final int[][] PANELS = {
		{InterfaceID.ToplevelPreEoc.MAP_CONTAINER, InterfaceID.ToplevelPreEoc.SIDE_CONTAINER, InterfaceID.ToplevelPreEoc.CHAT_CONTAINER},
		{InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER, InterfaceID.ToplevelOsrsStretch.SIDE_CONTAINER, InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER},
	};
	private static final int[] MODALS = {InterfaceID.ToplevelPreEoc.MAINMODAL, InterfaceID.ToplevelOsrsStretch.MAINMODAL};
	private static final String[] ANCHORS = {"TR", "BR", "BL"};

	@Inject
	private Client client;

	@Override
	protected void shutDown()
	{
		System.clearProperty(TAB);
		System.clearProperty(KEY_INV);
		System.clearProperty(KEY_PRAYER);
		System.clearProperty(UI_REGIONS);
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
		publishRegions();
	}

	/**
	 * Where the minimap, side panel and chatbox are, for the app's "interface scale": it draws
	 * them enlarged with crisp pixels and maps taps back. Nothing while a right-click menu or a
	 * main interface (bank, shop) is open, so those show and work at their real positions.
	 */
	private void publishRegions()
	{
		if (client.getGameState() != GameState.LOGGED_IN || !client.isResized() || client.isMenuOpen())
		{
			System.setProperty(UI_REGIONS, "");
			return;
		}
		for (int layout = 0; layout < PANELS.length; layout++)
		{
			Widget root = client.getWidget(PANELS[layout][1]);
			if (root == null || root.isHidden())
			{
				continue;
			}
			Widget modal = client.getWidget(MODALS[layout]);
			if (modal != null && !modal.isHidden() && modal.getNestedChildren() != null && modal.getNestedChildren().length > 0)
			{
				System.setProperty(UI_REGIONS, "");
				return;
			}
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 3; i++)
			{
				Widget w = client.getWidget(PANELS[layout][i]);
				if (w == null || w.isHidden() || w.getWidth() <= 0 || w.getHeight() <= 0)
				{
					continue;
				}
				java.awt.Rectangle b = w.getBounds();
				sb.append(b.x).append(',').append(b.y).append(',').append(b.width).append(',').append(b.height)
					.append(',').append(ANCHORS[i]).append(';');
			}
			System.setProperty(UI_REGIONS, sb.toString());
			return;
		}
		System.setProperty(UI_REGIONS, "");
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
