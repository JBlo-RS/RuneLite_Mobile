package com.jblors.rlm;

import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import javax.swing.JTabbedPane;

/**
 * RuneLite's sidebar is a JTabbedPane with tabs on the right (ClientUI); each tab's tooltip is
 * its navigation button's name ("Configuration" for the wrench). Selecting a tab fires
 * RuneLite's own change listener, which activates that panel just like a click.
 */
public final class Sidebar {
    private Sidebar() {}

    /** Selects the sidebar tab with this tooltip (event thread); no-op if not found. */
    public static void selectTab(String tooltip) {
        EventQueue.invokeLater(() -> {
            JTabbedPane tabs = find();
            if (tabs == null) return;
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if (tooltip.equals(tabs.getToolTipTextAt(i))) {
                    if (tabs.getSelectedIndex() != i) tabs.setSelectedIndex(i);
                    System.out.println("rlm-sidebar: selected '" + tooltip + "'");
                    return;
                }
            }
            System.out.println("rlm-sidebar: no tab '" + tooltip + "'");
        });
    }

    private static JTabbedPane find() {
        for (Frame f : Frame.getFrames()) {
            if (!f.isShowing()) continue;
            JTabbedPane t = find(f);
            if (t != null) return t;
        }
        return null;
    }

    private static JTabbedPane find(Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof JTabbedPane && ((JTabbedPane) k).getTabPlacement() == JTabbedPane.RIGHT) {
                return (JTabbedPane) k;
            }
            if (k instanceof Container) {
                JTabbedPane t = find((Container) k);
                if (t != null) return t;
            }
        }
        return null;
    }
}
