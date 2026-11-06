package com.jblors.rlm.plugins.mobile;

import java.awt.Point;

/**
 * Where the big menu is drawn and how its rows map onto the game's own menu. Made by the
 * overlay each frame; used by the mouse listener, so taps land where the menu was drawn.
 */
final class BigMenuLayout
{
	// The game's menu: a 19 px "Choose Option" header, then 15 px rows, newest entry on top
	static final int NATIVE_HEADER = 19, NATIVE_ROW = 15;

	final int menuX, menuY, menuW, entries;
	final int x, y, w, header, rowH;

	BigMenuLayout(int menuX, int menuY, int menuW, int entries, int x, int y, int w, int header, int rowH)
	{
		this.menuX = menuX;
		this.menuY = menuY;
		this.menuW = menuW;
		this.entries = entries;
		this.x = x;
		this.y = y;
		this.w = w;
		this.header = header;
		this.rowH = rowH;
	}

	int height()
	{
		return header + entries * rowH;
	}

	boolean contains(int px, int py)
	{
		return px >= x && px < x + w && py >= y && py < y + height();
	}

	/** Row (0 = top) under a point, -1 for the header. */
	int rowAt(int py)
	{
		return py < y + header ? -1 : Math.min(entries - 1, (py - y - header) / rowH);
	}

	/** The point on the game's menu matching a point on the big menu. */
	Point toNative(int px, int py)
	{
		int nx = menuX + menuW / 2;
		int row = rowAt(py);
		int ny = row < 0 ? menuY + NATIVE_HEADER / 2 : menuY + NATIVE_HEADER + row * NATIVE_ROW + NATIVE_ROW / 2;
		return new Point(nx, ny);
	}

	boolean sameMenu(int menuX, int menuY, int entries)
	{
		return this.menuX == menuX && this.menuY == menuY && this.entries == entries;
	}
}
