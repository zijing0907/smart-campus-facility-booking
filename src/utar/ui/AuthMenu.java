package utar.ui;

import utar.model.*;
import utar.service.*;
import utar.util.*;
import java.util.Scanner;

/**
 * AuthMenu.java — The first screen the user sees when the program starts.
 *
 * Provides three options:
 *   1. Login                    — sign in with email + password
 *   2. Register as New User     — create a new Student / Staff account
 *   0. Exit                     — quit the program
 *
 * After a successful login, the User object is returned to Main.java
 * which then opens the correct menu (UserMenu / AdminMenu / MaintenanceMenu).
 */
public class AuthMenu {

    private Scanner sc;                                 // shared keyboard input
    private UserService userService = new UserService(); // business logic for users

    public AuthMenu(Scanner sc) {
        this.sc = sc;
    }

    /**
     * show — main loop for the auth screen.
     * Keeps showing the menu until the user logs in successfully (returns
     * the User), or chooses Exit (which calls System.exit() and never
     * returns).  Choosing Register stays in this loop afterwards.
     */
    public User show() {
        while (true) {
            // Re-draw the screen each iteration so it always looks fresh
            ConsoleUI.clearScreen();
            ConsoleUI.printHeader("UTAR SMART CAMPUS FACILITY SYSTEM");
            System.out.println();
            ConsoleUI.printInfo("Universiti Tunku Abdul Rahman — Sungai Long Campus");
            System.out.println();
            ConsoleUI.printMenuItem(1, "Login");
            ConsoleUI.printMenuItem(2, "Register as New User");
            ConsoleUI.printMenuItem(0, "Exit");
            System.out.println();

            // Read a number 0..2 — promptInt() handles bad input internally
            int choice = ConsoleUI.promptInt(sc, "Select option", 0, 2);

            switch (choice) {
                case 1: {
                    // Login — return the User if successful, otherwise loop
                    User u = doLogin();
                    if (u != null) return u;
                    break;
                }
                case 2:
                    // Register — always falls back to the menu after
                    doRegister();
                    break;
                case 0:
                    // Exit — print farewell and stop the JVM
                    System.out.println(ConsoleUI.CYAN
                            + "\n  Thank you for using UTAR Smart Campus System. Goodbye!\n"
                            + ConsoleUI.RESET);
                    System.exit(0);
            }
        }
    }

    /**
     * doLogin — ask for email + password, look up the account, and
     * return the matching User (or null if anything went wrong).
     *
     * Distinguishes "unknown email" from "wrong password" so the user
     * gets a more useful error message.
     */
    private User doLogin() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.printSection("LOGIN");
        System.out.println();

        // ── Email ───────────────────────────────────────────────────────────
        String email = ConsoleUI.prompt(sc, "Email (0 to cancel)");
        if (email.equals("0")) return null;        // user cancelled
        if (email.isBlank()) {
            ConsoleUI.printError("Email cannot be empty.");
            ConsoleUI.pressEnter(sc);
            return null;
        }

        // ── Password ────────────────────────────────────────────────────────
        String pass = ConsoleUI.prompt(sc, "Password");
        if (pass.isBlank()) {
            ConsoleUI.printError("Password cannot be empty.");
            ConsoleUI.pressEnter(sc);
            return null;
        }

        // ── Verify with UserService ─────────────────────────────────────────
        User u = userService.login(email, pass);
        if (u == null) {
            // null can mean two things — we check which one to give a clearer message
            if (!userService.emailExists(email))
                ConsoleUI.printError("Invalid email. No account found with this email.");
            else
                ConsoleUI.printError("Invalid password. Please try again.");
            ConsoleUI.pressEnter(sc);
            return null;
        }

        // Successful login — show a welcome and pass the User back to Main
        ConsoleUI.printSuccess("Welcome, " + u.getName() + "! [" + u.getRoleLabel() + "]");
        ConsoleUI.pressEnter(sc);
        return u;
    }

    /**
     * doRegister — collect new user details and call UserService.register().
     *
     * The flow uses retry-loops so the user keeps trying until they enter
     * a valid value (or types 0 to cancel).  Validation is delegated to
     * InputValidator (utility class).
     *
     * Steps:
     *   1) Pick a role (Student or Staff)
     *   2) Enter name, email, password, contact number (each validated)
     *   3) Pick a faculty
     *   4) Pick a programme (Student) OR type department (Staff)
     *   5) Submit to UserService — catches errors like duplicate email
     */
    private void doRegister() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.printSection("NEW USER REGISTRATION");
        System.out.println();

        // ── STEP 1: Pick a role ─────────────────────────────────────────────
        // Both Student and Staff are CampusUser internally — only the role enum differs
        System.out.println("  [0] Cancel   [1] Student   [2] Staff");
        int roleChoice = ConsoleUI.promptInt(sc, "Select role", 0, 2);
        if (roleChoice == 0) return;
        User.UserRole role = roleChoice == 2 ? User.UserRole.STAFF : User.UserRole.STUDENT;

        System.out.println();
        ConsoleUI.printInfo("Registering as " + (roleChoice == 1 ? "Student" : "Staff") + " (CampusUser)...");
        System.out.println();

        // ── STEP 2: Personal details ────────────────────────────────────────
        // Each field has a "while (true) { ... break }" loop so the user
        // is asked again whenever the validation fails.
        String name, email, pass, phone;

        while (true) {
            name = ConsoleUI.prompt(sc, "Full Name (0 to cancel)");
            if (name.equals("0")) return;
            if (name.isBlank())                          { ConsoleUI.printError("Name cannot be empty."); }
            else if (!InputValidator.isValidName(name))  { ConsoleUI.printError("Name must contain letters only."); }
            else break;
        }

        while (true) {
            email = ConsoleUI.prompt(sc, "Email (must be @utar.edu.my)");
            if (email.isBlank())                          { ConsoleUI.printError("Email cannot be empty."); }
            else if (!InputValidator.isValidEmail(email)) { ConsoleUI.printError("Invalid email format."); }
            else if (!InputValidator.isUtarEmail(email))  { ConsoleUI.printError("Only UTAR emails are allowed (@utar.edu.my)."); }
            else break;
        }

        while (true) {
            pass = ConsoleUI.prompt(sc, "Password (min 6 characters)");
            if (pass.isBlank())                            { ConsoleUI.printError("Password cannot be empty."); }
            else if (!InputValidator.isValidPassword(pass)){ ConsoleUI.printError("Password must be at least 6 characters."); }
            else break;
        }

        while (true) {
            phone = ConsoleUI.prompt(sc, "Contact Number (e.g. 012-3456789)");
            if (!InputValidator.isValidPhone(phone)) { ConsoleUI.printError("Invalid phone number."); }
            else break;
        }

        // ── STEP 3: Faculty ─────────────────────────────────────────────────
        // List the faculties from DataStore.FACULTIES so the user just picks one.
        System.out.println();
        ConsoleUI.printInfo("Select your Faculty:");
        String[] faculties = DataStore.FACULTIES;
        for (int i = 0; i < faculties.length; i++)
            System.out.printf("  %s[%d]%s %s%n", ConsoleUI.YELLOW, i + 1, ConsoleUI.RESET, faculties[i]);
        System.out.printf("  %s[0]%s Cancel%n", ConsoleUI.YELLOW, ConsoleUI.RESET);
        int facChoice = ConsoleUI.promptInt(sc, "Select faculty", 0, faculties.length);
        if (facChoice == 0) return;
        String faculty = faculties[facChoice - 1];

        // ── STEP 4: Programme (Student) OR Department (Staff) ───────────────
        // 'extra' is the value that goes into the CampusUser.extra field.
        String extra = "";
        if (roleChoice == 1) {
            // Student → pick from a fixed list of programmes for the chosen faculty
            String[] programmes = DataStore.PROGRAMMES_BY_FACULTY[facChoice - 1];
            System.out.println();
            ConsoleUI.printInfo("Select your Programme:");
            System.out.printf("  %s[0]%s Cancel%n", ConsoleUI.YELLOW, ConsoleUI.RESET);
            for (int i = 0; i < programmes.length; i++)
                System.out.printf("  %s[%d]%s %s%n",
                        ConsoleUI.YELLOW, i + 1, ConsoleUI.RESET, programmes[i]);
            int progChoice = ConsoleUI.promptInt(sc, "Select programme", 0, programmes.length);
            if (progChoice == 0) return;
            extra = programmes[progChoice - 1];
        } else {
            // Staff → free-text department name
            extra = ConsoleUI.prompt(sc, "Department");
        }

        // ── STEP 5: Submit to UserService ───────────────────────────────────
        // UserService.register() throws if the email is already taken — we
        // catch that and show it to the user as a friendly error message.
        try {
            User u = userService.register(name, email, pass, phone, faculty, role, extra);
            System.out.println();
            ConsoleUI.printSuccess("Registration successful!");
            ConsoleUI.printField("Your User ID", u.getUserId());
            ConsoleUI.printField("Role", u.getRoleLabel());
            ConsoleUI.printInfo("You can now login with your email and password.");
        } catch (Exception e) {
            // Exception Handling (ch09) — duplicate-email, etc.
            ConsoleUI.printError(e.getMessage());
        }
        ConsoleUI.pressEnter(sc);
    }
}
