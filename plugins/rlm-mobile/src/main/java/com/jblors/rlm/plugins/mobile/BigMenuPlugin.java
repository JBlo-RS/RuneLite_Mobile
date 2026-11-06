package com.jblors.rlm.plugins.mobile;

import com.google.inject.Provides;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * A large, tap-friendly copy of the right-click menu. A tap on one of its rows becomes a click
 * on the same row of the game's menu (like stretched mode scales clicks): one tap, one click.
 */
@PluginDescriptor(
	name = "Big Menu",
	description = "Draws the right-click menu large enough to tap, and sends your tap to the matching option",
	tags = {"mobile", "menu", "right click", "touch", "big", "rlm"}
)
public class BigMenuPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private BigMenuOverlay overlay;

	/** The menu as last drawn, or null when no menu is open */
	private volatile BigMenuLayout layout;

	private final MouseListener listener = new MouseListener()
	{
		@Override
		public MouseEvent mouseClicked(MouseEvent e)
		{
			return remap(e);
		}

		@Override
		public MouseEvent mousePressed(MouseEvent e)
		{
			return remap(e);
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent e)
		{
			return remap(e);
		}

		@Override
		public MouseEvent mouseEntered(MouseEvent e)
		{
			return e;
		}

		@Override
		public MouseEvent mouseExited(MouseEvent e)
		{
			return e;
		}

		@Override
		public MouseEvent mouseDragged(MouseEvent e)
		{
			return remap(e);
		}

		@Override
		public MouseEvent mouseMoved(MouseEvent e)
		{
			return remap(e);
		}
	};

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		mouseManager.registerMouseListener(0, listener);
	}

	@Override
	protected void shutDown()
	{
		mouseManager.unregisterMouseListener(listener);
		overlayManager.remove(overlay);
		layout = null;
	}

	void setLayout(BigMenuLayout layout)
	{
		this.layout = layout;
	}

	BigMenuLayout getLayout()
	{
		return layout;
	}

	/**
	 * Events inside the big menu go to the matching place on the game's menu. That includes
	 * mouse moves: the game closes its menu when the cursor leaves it, and the big menu is
	 * larger than the game's.
	 */
	private MouseEvent remap(MouseEvent e)
	{
		BigMenuLayout l = layout;
		if (l == null || !client.isMenuOpen() || !l.contains(e.getX(), e.getY())
			|| !l.sameMenu(client.getMenuX(), client.getMenuY(), client.getMenu().getMenuEntries().length))
		{
			return e;
		}
		Point p = l.toNative(e.getX(), e.getY());
		int dx = p.x - e.getX(), dy = p.y - e.getY();
		return new MouseEvent((Component) e.getSource(), e.getID(), e.getWhen(), e.getModifiersEx(),
			p.x, p.y, e.getXOnScreen() + dx, e.getYOnScreen() + dy, e.getClickCount(), e.isPopupTrigger(), e.getButton());
	}

	@Provides
	BigMenuConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BigMenuConfig.class);
	}
}
