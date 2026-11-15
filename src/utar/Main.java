package utar;

import utar.model.*;
import utar.ui.*;
import java.util.Scanner;

/**
 * Main.java — Application entry point.
 *
 * This is where the program starts running. It does three jobs:
 *   1. Show the login / register screen (AuthMenu)
 *   2. Read which type of user just logged in (Admin, Maintenance, or normal user)
 *   3. Open the correct menu for that type of user
 *
 * The whole thing is wrapped in a while-loop so that after a user logs out,
 * the program goes back to the login screen instead of closing.
 */
public class Main {

    public static void main(String[] args) {
        // One Scanner is shared by every menu so System.in is only opened once.
        Scanner sc = new Scanner(System.in);

        // Outer loop — keeps the program alive until the user picks "Exit"
        // from the AuthMenu (which calls System.exit() inside).
        while (true) {

            // STEP 1: show the login / register screen.
            // auth.show() returns the logged-in User, or null if the user
            // cancelled / login failed (in which case we just loop again).
            AuthMenu auth = new AuthMenu(sc);
            User user = auth.show();

            if (user == null) {
                continue; // back to the login screen
            }

            // STEP 2 & 3: route the user to the correct menu based on role.
            // This is Polymorphism in action — the variable type is "User"
            // but the actual object can be Admin, MaintenancePersonnel, or
            // CampusUser, and each one gets a different menu.
            if (user.getRole() == User.UserRole.ADMIN) {
                new AdminMenu(sc, user).show();              // admin features
            } else if (user.getRole() == User.UserRole.MAINTENANCE) {
                new MaintenanceMenu(sc, user).show();        // maintenance staff features
            } else {
                new UserMenu(sc, user).show();               // student / staff features
            }
            // When the user logs out, we fall back to the top of the while
            // loop and the AuthMenu is shown again.
        }
    }
}
