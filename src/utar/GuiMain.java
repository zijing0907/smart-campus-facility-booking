package utar;

import utar.gui.LoginFrame;
import javax.swing.SwingUtilities;

/**
 * GuiMain.java - Entry point for the desktop (Swing) version.
 *
 * Main.java starts the console version; this class starts the GUI.
 * Both front ends call the same service classes (BookingService,
 * UserService, MaintenanceService, AnalyticsService) and share the same
 * data/ folder, so a booking made in one shows up in the other.
 *
 * Swing is not thread-safe: every window must be created and changed on
 * the Event Dispatch Thread, which is what invokeLater() does.
 */
public class GuiMain {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> LoginFrame.open());
    }
}
