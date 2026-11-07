package com.jblors.rlm.plugins.mobile;

import java.awt.Point;

/**
 * Where the big menu is: the game's menu enlarged k times. Made by the overlay each frame and
 * used by the mouse listener, so a point on the big menu maps back exactly by dividing by k.
 */
final class BigMenuLayout
{
	// The game's menu: a 19 px "Choose Option" header, then 15 px rows, newest entry on top
	static final int NATIVE_HEADER = 19, NATIVE_ROW = 15;

	final int menuX, menuY, menuW, menuH, entries;
	/** Width as drawn (at least the game's; wider if our font needs it) */
	final int drawW;
	final int x, y, k;

	BigMenuLayout(int menuX, int menuY, int menuW, int drawW, int menuH, int entries, int x, int y, int k)
	{
		this.drawW = drawW;
		this.menuX = menuX;
		this.menuY = menuY;
		this.menuW = menuW;
		this.menuH = menuH;
		this.entries = entries;
		this.x = x;
		this.y = y;
		this.k = k;
	}

	boolean contains(int px, int py)
	{
		return px >= x && px < x + drawW * k && py >= y && py < y + menuH * k;
	}

	/**
	 * The point on the game's menu matching a point on the big menu: the middle of the same
	 * option's row (or of the header), so rounding can't land it on a neighbouring row.
	 */
	Point toNative(int px, int py)
	{
		int nx = menuX + menuW / 2;
		int dy = (py - y) / k - NATIVE_HEADER;
		int row = dy < 0 ? -1 : Math.min(entries - 1, dy / NATIVE_ROW);
		int ny = row < 0 ? menuY + NATIVE_HEADER / 2 : menuY + NATIVE_HEADER + row * NATIVE_ROW + NATIVE_ROW / 2;
		return new Point(nx, ny);
	}

	boolean sameMenu(int menuX, int menuY, int entries)
	{
		return this.menuX == menuX && this.menuY == menuY && this.entries == entries;
	}
}
