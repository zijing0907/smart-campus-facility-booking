package utar.ui;

import utar.model.*;
import utar.service.*;
import utar.util.*;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * MaintenanceMenu.java — Menu shown after a Maintenance Personnel logs in.
 *
 * Shows three options:
 *   1. View My Assigned Tasks   — list reports waiting for this worker
 *   2. Update Task Status        — flip a task to IN_PROGRESS or RESOLVED
 *   3. View My Completed Tasks  — history of resolved tasks
 *   0. Logout
 *
 * Each maintenance worker only sees the reports assigned to them — the
 * filter is done by DataStore.getReportsAssignedTo(userId).
 */
public class MaintenanceMenu {

    // ── Dependencies ─────────────────────────────────────────────────────────
    private Scanner            sc;            // shared keyboard input
    private User               personnel;     // who is logged in
    private MaintenanceService maintService = new MaintenanceService();
    private DataStore          ds           = DataStore.getInstance();
    private NotificationService notifier    = new NotificationService();

    public MaintenanceMenu(Scanner sc, User personnel) {
        this.sc        = sc;
        this.personnel = personnel;
    }

    /**
     * show — main loop for the maintenance panel.  Re-paints the menu
     * each time and dispatches to the correct helper.  Stops on logout.
     */
    public void show() {
        while (true) {
            ConsoleUI.clearScreen();
            ConsoleUI.printHeader("UTAR SMART CAMPUS — MAINTENANCE PANEL");
            System.out.println();
            ConsoleUI.printField("Personnel", personnel.getName());
            // We know personnel is a MaintenancePersonnel because Main.java
            // routes only those users here — safe to cast.
            ConsoleUI.printField("Specialization",
                    ((MaintenancePersonnel) personnel).getSpecialization());
            System.out.println();
            showNotifications(personnel.getUserId());   // e.g. a newly assigned task

            ConsoleUI.printSection("MY TASKS");
            ConsoleUI.printMenuItem(1, "View My Assigned Tasks");
            ConsoleUI.printMenuItem(2, "Update Task Status");
            ConsoleUI.printMenuItem(3, "View My Completed Tasks");
            ConsoleUI.printMenuItem(0, "Logout");
            System.out.println();

            int choice = ConsoleUI.promptInt(sc, "Select option", 0, 3);
            switch (choice) {
                case 1: viewAssignedTasks();  break;
                case 2: updateTaskStatus();   break;
                case 3: viewCompletedTasks(); break;
                case 0:
                    ConsoleUI.printInfo("Logged out.");
                    ConsoleUI.pressEnter(sc);
                    return;
            }
        }
    }

    /**
     * showNotifications — print any notifications this user hasn't seen
     * yet (e.g. "Your booking BK0100 was approved"), then mark them as read.
     */
    private void showNotifications(String userId) {
        for (Notification n : notifier.getUnread(userId)) {
            ConsoleUI.printAlert("NEW: " + n.getMessage());
        }
        notifier.markAllRead(userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VIEW MY ASSIGNED TASKS — option 1
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * viewAssignedTasks — show every un-resolved report assigned to me.
     * For each task we print the facility, status, issue, category, and
     * date reported so the worker has all the context they need.
     */
    private void viewAssignedTasks() {
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("MY ASSIGNED TASKS");

        ArrayList<MaintenanceReport> myTasks = getMyOpenTasks();
        if (myTasks.isEmpty()) {
            ConsoleUI.printInfo("You have no unresolved tasks assigned.");
            ConsoleUI.pressEnter(sc);
            return;
        }

        // Print each task as a small block separated by a divider
        ConsoleUI.printDivider();
        for (MaintenanceReport r : myTasks) {
            Facility f = ds.getFacilityById(r.getFacilityId());
            System.out.printf("  %-8s %-28s [%s]%n",
                    r.getReportId(),
                    f != null ? f.getName() : r.getFacilityId(),
                    r.getStatus());
            System.out.printf("    Issue   : %s%n", r.getIssueDescription());
            System.out.printf("    Category: %s%n", r.getCategory());
            System.out.printf("    Reported: %s%n", r.getReportedDate());
            if (r.hasAttachment()) {
                System.out.printf("    Attached: %s%n", ds.getAttachmentFile(r.getAttachment()).getPath());
            }
            ConsoleUI.printDivider();
        }
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  UPDATE TASK STATUS — option 2
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * updateTaskStatus — let the worker mark a task as IN_PROGRESS
     * or RESOLVED.  When marking RESOLVED the worker is also asked
     * for resolution notes and the date the work was completed.
     */
    private void updateTaskStatus() {
        ConsoleUI.flushInput(sc);
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("UPDATE TASK STATUS");

        ArrayList<MaintenanceReport> myTasks = getMyOpenTasks();
        if (myTasks.isEmpty()) {
            ConsoleUI.printInfo("You have no unresolved tasks to update.");
            ConsoleUI.pressEnter(sc);
            return;
        }

        // ── List the tasks so the worker can pick one by index ──────────────
        System.out.printf("  %s[0]%s Back%n%n", ConsoleUI.YELLOW, ConsoleUI.RESET);
        for (int i = 0; i < myTasks.size(); i++) {
            MaintenanceReport r = myTasks.get(i);
            Facility f = ds.getFacilityById(r.getFacilityId());
            System.out.printf("  %s[%d]%s %-8s %-28s [%s]%n",
                    ConsoleUI.YELLOW, i + 1, ConsoleUI.RESET,
                    r.getReportId(),
                    f != null ? f.getName() : r.getFacilityId(),
                    r.getStatus());
        }

        // (1) pick a task
        int sel = ConsoleUI.promptInt(sc, "Select task", 0, myTasks.size());
        if (sel == 0) return;
        MaintenanceReport target = myTasks.get(sel - 1);

        // (2) pick a new status
        System.out.println("\n  [0] Back  [1] IN_PROGRESS  [2] RESOLVED");
        int s = ConsoleUI.promptInt(sc, "New status", 0, 2);
        if (s == 0) return;

        // Map menu number → enum value
        MaintenanceReport.ReportStatus newStatus = s == 2
                ? MaintenanceReport.ReportStatus.RESOLVED
                : MaintenanceReport.ReportStatus.IN_PROGRESS;

        // (3) optional notes + date — blank means "leave it"
        String notes = ConsoleUI.prompt(sc, "Resolution notes (ENTER to skip)");
        String date  = ConsoleUI.prompt(sc, "Date (dd/MM/yyyy)");

        // (4) save through MaintenanceService
        maintService.updateStatus(personnel.getUserId(), target.getReportId(), newStatus,
                notes.isBlank() ? null : notes,
                date.isBlank()  ? null : date);

        ConsoleUI.printSuccess("Status updated to " + newStatus + ".");
        ConsoleUI.pressEnter(sc);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  VIEW MY COMPLETED TASKS — option 3
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * viewCompletedTasks — show every RESOLVED report assigned to me.
     * Includes the resolved date and any resolution notes the worker added.
     */
    private void viewCompletedTasks() {
        ConsoleUI.clearScreen();
        ConsoleUI.printSection("MY COMPLETED TASKS");

        // Pull all my reports, then filter to just the resolved ones
        ArrayList<MaintenanceReport> all  = ds.getReportsAssignedTo(personnel.getUserId());
        ArrayList<MaintenanceReport> done = new ArrayList<>();
        for (MaintenanceReport r : all) {
            if (r.getStatus() == MaintenanceReport.ReportStatus.RESOLVED) {
                done.add(r);
            }
        }

        if (done.isEmpty()) {
            ConsoleUI.printInfo("You have no completed tasks yet.");
            ConsoleUI.pressEnter(sc);
            return;
        }

        // Print each completed task with its resolved date and notes
        ConsoleUI.printDivider();
        for (MaintenanceReport r : done) {
            Facility f = ds.getFacilityById(r.getFacilityId());
            System.out.printf("  %-8s %-28s Resolved: %s%n",
                    r.getReportId(),
                    f != null ? f.getName() : r.getFacilityId(),
                    r.getResolvedDate() != null ? r.getResolvedDate() : "-");

            if (r.getResolutionNotes() != null) {
                System.out.printf("    Notes: %s%n", r.getResolutionNotes());
            }
            ConsoleUI.printDivider();
        }
        ConsoleUI.pressEnter(sc);
    }

    /**
     * getMyOpenTasks — small helper used by viewAssignedTasks() and
     * updateTaskStatus().  Returns reports assigned to me that are NOT
     * yet RESOLVED (i.e. ASSIGNED or IN_PROGRESS).
     */
    private ArrayList<MaintenanceReport> getMyOpenTasks() {
        ArrayList<MaintenanceReport> result = new ArrayList<>();
        for (MaintenanceReport r : ds.getReportsAssignedTo(personnel.getUserId())) {
            if (r.getStatus() != MaintenanceReport.ReportStatus.RESOLVED) {
                result.add(r);
            }
        }
        return result;
    }
}
