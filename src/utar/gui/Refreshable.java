package utar.gui;

/**
 * Refreshable.java - A screen that can reload its data from the services.
 *
 * MainFrame calls refresh() every time the user opens a tab, so a tab
 * always shows the latest data, even if another tab changed it (for
 * example, approving a booking changes the numbers on the Reports tab).
 *
 * OOP concepts:
 *   - Interface: each panel decides for itself what "refresh" means.
 *   - Polymorphism: MainFrame only knows it has "a Refreshable" and
 *     calls refresh() without knowing which panel it is.
 */
interface Refreshable {
    void refresh();
}
