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
    private static volatile long lastOpenAttempt;

    private Sidebar() {}

    /**
     * Opens RuneLite's sidebar if it is closed. The mobile layout keeps it open (off screen,
     * shown by the slide-in panel) so the game canvas keeps the phone's shape; a closed sidebar
     * makes the canvas wider and the phone shows black bars. Calls RuneLite's own
     * ClientUI.toggleSidebar(true, false), so ClientUI does its usual work (remembers it's open,
     * activates the panel). No key is sent: a sidebar hotkey (Ctrl+F11) that RuneLite didn't take
     * went on to the game, where F11 opens a tab (the emotes), every 2 s. Retries at most every 2 s.
     */
    public static void ensureOpen() {
        EventQueue.invokeLater(() -> {
            JTabbedPane tabs = find();
            if (tabs == null || tabs.isVisible()) return;
            long now = System.currentTimeMillis();
            if (now - lastOpenAttempt < 2000) return;
            lastOpenAttempt = now;
            java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(tabs);
            String how;
            try {
                openWithClientUi(w);
                how = "opened it through RuneLite";
            } catch (ReflectiveOperationException | RuntimeException ex) {
                // Last resort: show it ourselves (ClientUI's state catches up on its next toggle)
                tabs.setVisible(true);
                if (tabs.getParent() != null) tabs.getParent().revalidate();
                how = "opened it directly (" + ex + ")";
            }
            System.out.println("rlm-sidebar: was closed, " + how + (tabs.isVisible() ? "" : ", but it's still closed"));
        });
    }

    /** RuneLite's ClientUI (a singleton in RuneLite's injector) opens its sidebar. */
    private static void openWithClientUi(java.awt.Window w) throws ReflectiveOperationException {
        ClassLoader cl = w != null ? w.getClass().getClassLoader() : Thread.currentThread().getContextClassLoader();
        Class<?> runelite = Class.forName("net.runelite.client.RuneLite", false, cl);
        java.lang.reflect.Field f = runelite.getDeclaredField("injector");
        f.setAccessible(true);
        Object injector = f.get(null);
        if (injector == null) throw new IllegalStateException("RuneLite has no injector yet");
        Class<?> clientUi = Class.forName("net.runelite.client.ui.ClientUI", false, runelite.getClassLoader());
        // Through Guice's public Injector interface (the injector's own class isn't public)
        Class<?> injectorType = Class.forName("com.google.inject.Injector", false, runelite.getClassLoader());
        Object ui = injectorType.getMethod("getInstance", Class.class).invoke(injector, clientUi);
        java.lang.reflect.Method toggle = clientUi.getDeclaredMethod("toggleSidebar", boolean.class, boolean.class);
        toggle.setAccessible(true);
        toggle.invoke(ui, true, false);
    }

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
