package utar.service;

import utar.model.*;
import java.io.*;
import java.util.ArrayList;

/**
 * DataStore.java — Singleton class that owns ALL data and ALL file I/O.
 *
 * Why a Singleton?
 *   We want exactly one shared store. If two parts of the program had
 *   their own copies of the data, edits in one place would not be visible
 *   in the other. The pattern used here is the classic lazy Singleton:
 *     • private constructor               → no one can call "new DataStore()"
 *     • private static instance field     → holds the only copy
 *     • public  static getInstance()      → returns that copy (creating it
 *                                            on the first call only)
 *
 * What it does:
 *   • Holds four ArrayLists in memory: users, facilities, bookings, reports.
 *   • On first start, seeds them with sample data; on later starts, reloads
 *     them from .txt files inside the "data/" folder.
 *   • Whenever something changes, saveAll() is called so the .txt files
 *     stay up to date.
 *   • Generates new IDs (STU001, STAFF02, BK0001, MR0001, MAINT01) using
 *     a small counter for each entity type.
 *
 * OOP concepts:
 *   • Singleton design pattern
 *   • File I/O — BufferedReader, PrintWriter, FileWriter
 *   • ArrayList collections
 *   • String operations — splitByPipe uses indexOf/substring only
 *   • Exception handling — every read/write block has try/catch
 */
public class DataStore {

    // ── Singleton instance ───────────────────────────────────────────────────
    // The single shared copy. Starts as null and is created on first call
    // to getInstance(). After that, every call returns the same object.
    private static DataStore instance;

    // ── File paths (constants) ───────────────────────────────────────────────
    // All four .txt files live inside the "data/" folder (created at startup).
    private static final String DATA_DIR  = "data";
    private static final String USERS_FILE = DATA_DIR + "/users.txt";
    private static final String FACILITY_FILE = DATA_DIR + "/facilities.txt";
    private static final String BOOKING_FILE = DATA_DIR + "/bookings.txt";
    private static final String REPORT_FILE = DATA_DIR + "/reports.txt";

    // ── In-memory storage (ArrayLists) ─────────────────────────────────
    // These four lists are the program's "database" while it is running.
    // They are kept in sync with the .txt files via saveAll() / loadAll().
    private ArrayList<User> users = new ArrayList<>();
    private ArrayList<Facility> facilities = new ArrayList<>();
    private ArrayList<Booking> bookings  = new ArrayList<>();
    private ArrayList<MaintenanceReport> reports = new ArrayList<>();

    // ── ID counters ──────────────────────────────────────────────────────────
    // Used by nextStudentId(), nextStaffId(), etc. to make sure each new
    // record gets a unique ID like STU001, STAFF02, BK0003.
    private int studentCounter = 1;
    private int staffCounter = 1;
    private int maintenanceCounter = 1;
    private int bookingCounter = 1;
    private int reportCounter = 1;

    /**
     * Static list of UTAR Sungai Long faculties.
     * The order here MUST match the outer index of PROGRAMMES_BY_FACULTY
     * below — that's how registration finds the right list of programmes.
     */
    public static final String[] FACULTIES = {
            "Lee Kong Chian Faculty of Engineering and Science (LKC FES)",
            "Faculty of Accountancy and Management (FAM)",
            "Faculty of Creative Industries (FCI)",
            "Faculty of Medicine and Health Sciences (FMHS)",
            "Institute of Chinese Studies (ICS)",
            "Centre for Foundation Studies (CFS)"
    };

    // Programmes offered by each faculty. The outer index MUST match the FACULTIES
    // array order above. Used during student registration for programme selection.
    public static final String[][] PROGRAMMES_BY_FACULTY = {
            // [0] LKC FES
            {
                    "Biomedical Engineering",
                    "Chemical Engineering",
                    "Civil Engineering",
                    "Electrical and Electronic Engineering",
                    "Materials Engineering",
                    "Mechanical Engineering",
                    "Mechatronics Engineering",
                    "Software Engineering",
                    "Telecommunications Engineering",
                    "Actuarial Science",
                    "Applied Mathematics with Computing",
                    "Architecture",
                    "Financial Mathematics",
                    "Physics",
                    "Quantity Surveying"
            },
            // [1] FAM
            {
                    "Accounting",
                    "Building and Property Management",
                    "Economics (Global Economics)",
                    "Finance (Financial Technology)",
                    "International Business"
            },
            // [2] FCI
            {
                    "Digital Animation",
                    "Game Design",
                    "Graphic Design and Multimedia",
                    "Journalism in Chinese Media",
                    "Broadcasting",
                    "Corporate Communication",
                    "Media and Creative Studies",
                    "Game Development"
            },
            // [3] FMHS
            {
                    "Chinese Medicine",
                    "Medicine and Surgery (MBBS)",
                    "Nursing",
                    "Physiotherapy"
            },
            // [4] ICS
            {
                    "Applied Chinese Language",
                    "Chinese Studies"
            },
            // [5] CFS
            {
                    "Foundation in Science (Biological Stream)",
                    "Foundation in Science (Physical Stream)",
                    "Foundation in Arts (Management and Accountancy)",
                    "Foundation in Arts (Arts and Social Science)"
            }
    };

    /**
     * Private constructor — Singleton pattern.
     * Runs once on the very first getInstance() call.
     * Steps:
     *   1. Create the "data/" folder if it doesn't exist.
     *   2. If users.txt already exists → load all four files from disk.
     *   3. Otherwise → this is a fresh run: fill the lists with seed data
     *      and write everything to disk so subsequent runs reload it.
     */
    private DataStore() {
        new File(DATA_DIR).mkdirs();   // create the data folder if missing

        if (new File(USERS_FILE).exists()) {
            // Existing data — reload everything from .txt files
            loadAll();
        }
        else {
            // First-time run — populate with sample data and save it
            seedData();
            saveAll();
        }
    }

    /**
     * getInstance — the only way to get a DataStore.
     * Creates the instance lazily (on the first call) — Singleton pattern.
     */
    public static DataStore getInstance() {
        if (instance == null) {
            instance = new DataStore();
        }
        return instance;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FILE HELPERS — small reusable methods for reading / writing files
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * writeLine — overwrite the given file with the supplied lines.
     * Uses try-with-resources so the writer is closed automatically even
     * if an error occurs.  Any IOException is reported to System.err and
     * swallowed (the program keeps running with the in-memory data).
     */
    private void writeLine(String path, ArrayList<String> lines) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            for (String l : lines) {
                pw.println(l);
            }
        } catch (IOException e) {
            // Don't crash — just log and continue
            System.err.println("Write error: " + e.getMessage());
        }
    }

    /**
     * readLines — read every non-blank, non-comment line from the file.
     * Lines that start with '#' are treated as comments and skipped, so
     * the .txt files can include human-friendly header rows.
     * Returns an empty list if the file doesn't exist.
     */
    private ArrayList<String> readLines(String path) {
        ArrayList<String> out = new ArrayList<>();
        File f = new File(path);

        if (!f.exists()) {
            return out;   // nothing to read
        }

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();

                if (!line.isEmpty() && !line.startsWith("#")) {
                    out.add(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Read error: " + e.getMessage());
        }

        return out;
    }

    /**
     * safe — escape a string before writing it to a pipe-delimited file.
     *   • null   becomes the literal text "NULL"
     *   • '|'    is escaped to "\|" so it doesn't break field boundaries
     */
    private static String safe(String s) {
        return s == null ? "NULL" : s.replace("|", "\\|");
    }

    /**
     * load — reverse of safe(); turns a stored value back into a real String.
     *   • "NULL" becomes a real null
     *   • "\|"   becomes back to '|'
     */
    private static String load(String s) {
        return "NULL".equals(s) ? null : s.replace("\\|", "|");
    }

    /**
     * splitByPipe — split a line on '|' without using regex.
     *
     * Uses only indexOf() and substring() (ch06 Strings). Empty trailing
     * fields are preserved on purpose — the field COUNT must stay
     * consistent with the file format so loadXxx() can index reliably.
     */
    private static String[] splitByPipe(String line) {
        ArrayList<String> parts = new ArrayList<>();
        int start = 0;
        int pipe;

        // Walk through the string, finding each '|' and slicing off the
        // text in between
        while ((pipe = line.indexOf('|', start)) != -1) {
            parts.add(line.substring(start, pipe));
            start = pipe + 1;
        }
        // Whatever is left after the last '|' is the final field
        parts.add(line.substring(start));

        return parts.toArray(new String[0]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SAVE — write the in-memory ArrayLists out to .txt files
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * saveAll — convenience method that saves every list to its file.
     * Called whenever data changes (add / update / delete).
     */
    public void saveAll() {
        saveUsers();
        saveFacilities();
        saveBookings();
        saveReports();
    }

    /**
     * saveUsers — write every User to users.txt, one user per line, with
     * fields separated by '|'.
     *
     * Each row has 8 columns:
     *   userId | name | email | password | contact | faculty | role | extra
     *
     * The 'extra' column means:
     *   • CampusUser           → programme (student) or department (staff)
     *   • MaintenancePersonnel → specialization
     *   • Admin                → empty
     */
    private void saveUsers() {
        ArrayList<String> lines = new ArrayList<>();
        // Header row — starts with '#' so readLines() skips it on load
        lines.add("# userId|name|email|password|contact|faculty|role|extra");

        for (User u : users) {
            // Pick the correct value to put in the 'extra' column
            String extra = "";
            if (u instanceof CampusUser) {
                extra = ((CampusUser) u).getExtra();             // programme/department
            }
            else if (u instanceof MaintenancePersonnel) {
                extra = ((MaintenancePersonnel) u).getSpecialization();
            }
            // Admin has no extra — leave it as ""

            // Build the pipe-delimited line in one go
            lines.add(String.join("|", safe(u.getUserId()), safe(u.getName()), safe(u.getEmail()), safe(u.getPassword()), safe(u.getContactNumber()), safe(u.getFaculty()), u.getRole().name(), safe(extra)));
        }

        writeLine(USERS_FILE, lines);
    }

    private void saveFacilities() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("# facilityId|name|type|capacity|location|status|maintCount");

        for (Facility f : facilities) {
            lines.add(String.join("|", safe(f.getFacilityId()), safe(f.getName()), f.getType().name(), String.valueOf(f.getCapacity()), safe(f.getLocation()), f.getStatus().name(), String.valueOf(f.getMaintenanceCount())));
        }

        writeLine(FACILITY_FILE, lines);
    }

    private void saveBookings() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("# bookingId|userId|facilityId|date|start|end|purpose|desc|status|remark|reminderSent");

        for (Booking b : bookings) {
            lines.add(String.join("|", safe(b.getBookingId()), safe(b.getUserId()), safe(b.getFacilityId()), safe(b.getDate()), safe(b.getStartTime()), safe(b.getEndTime()), b.getPurpose().name(), safe(b.getDescription()), b.getStatus().name(), safe(b.getAdminRemark()), String.valueOf(b.isReminderSent())));
        }

        writeLine(BOOKING_FILE, lines);
    }

    private void saveReports() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("# reportId|facilityId|reportedBy|desc|category|status|assignedTo|reportedDate|resolvedDate|notes");

        for (MaintenanceReport r : reports) {
            lines.add(String.join("|", safe(r.getReportId()), safe(r.getFacilityId()), safe(r.getReportedByUserId()), safe(r.getIssueDescription()), r.getCategory().name(), r.getStatus().name(), safe(r.getAssignedTo()), safe(r.getReportedDate()), safe(r.getResolvedDate()), safe(r.getResolutionNotes())));
        }

        writeLine(REPORT_FILE, lines);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LOAD — read .txt files back into the in-memory ArrayLists at startup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * loadAll — load every .txt file into its ArrayList.
     * Called from the constructor when users.txt already exists.
     */
    private void loadAll() {
        loadUsers();
        loadFacilities();
        loadBookings();
        loadReports();
    }

    /**
     * loadUsers — read users.txt and rebuild the users ArrayList.
     *
     * For each row we look at the role column and create the right
     * subclass (CampusUser, MaintenancePersonnel, or Admin).  We also
     * keep the ID counters one above the highest existing number so the
     * next nextStudentId() / nextStaffId() / nextMaintenanceId() call
     * doesn't accidentally reuse an existing ID.
     *
     * If a single line is corrupt, only that line is skipped — the
     * rest of the file still loads.
     */
    private void loadUsers() {
        users.clear();   // start fresh

        for (String line : readLines(USERS_FILE)) {
            String[] p = splitByPipe(line);

            // Skip lines that don't have all 8 columns
            if (p.length < 8) {
                continue;
            }

            try {
                // Parse the columns into typed values
                String id = load(p[0]);
                String name = load(p[1]);
                String email = load(p[2]);
                String pass = load(p[3]);
                String contact = load(p[4]);
                String faculty = load(p[5]);
                User.UserRole role = User.UserRole.valueOf(p[6]);
                String extra = load(p[7]);

                // Build the right subclass based on the role
                if (role == User.UserRole.STUDENT) {
                    users.add(new CampusUser(id, name, email, pass, contact,
                                              faculty, User.UserRole.STUDENT, extra));

                    // Bump the student counter past any existing STUxxx ID
                    if (id.startsWith("STU")) {
                        try {
                            int n = Integer.parseInt(id.substring(3));
                            if (n >= studentCounter) {
                                studentCounter = n + 1;
                            }
                        } catch (Exception ignored) { /* not a numeric STUxxx id — skip */ }
                    }
                }
                else if (role == User.UserRole.STAFF) {
                    users.add(new CampusUser(id, name, email, pass, contact,
                                              faculty, User.UserRole.STAFF, extra));

                    // Bump the staff counter past any existing STAFFxx ID
                    if (id.startsWith("STAFF")) {
                        try {
                            int n = Integer.parseInt(id.substring(5));
                            if (n >= staffCounter) {
                                staffCounter = n + 1;
                            }
                        } catch (Exception ignored) {}
                    }
                } else if (role == User.UserRole.MAINTENANCE) {
                    users.add(new MaintenancePersonnel(id, name, email, pass, contact,
                                                        faculty, extra));

                    // Bump the maintenance counter past any existing MAINTxx ID
                    if (id.startsWith("MAINT")) {
                        try {
                            int n = Integer.parseInt(id.substring(5));
                            if (n >= maintenanceCounter) {
                                maintenanceCounter = n + 1;
                            }
                        } catch (Exception ignored) {}
                    }
                } else {
                    users.add(new Admin(id, name, email, pass, contact));
                }
            } catch (Exception e) {
                System.err.println("Bad user line: " + line);
            }
        }
    }

    /**
     * loadFacilities — read facilities.txt back into the facilities ArrayList.
     * Each row has 7 columns:
     *   facilityId | name | type | capacity | location | status | maintCount
     * Status and maintenanceCount are saved on top of the constructor
     * defaults because they may have changed since the facility was created.
     */
    private void loadFacilities() {
        facilities.clear();

        for (String line : readLines(FACILITY_FILE)) {
            String[] p = splitByPipe(line);
            if (p.length < 7) {
                continue;   // skip malformed lines
            }

            try {
                // Build the facility from the first 5 columns
                Facility f = new Facility(load(p[0]), load(p[1]),
                                          Facility.FacilityType.valueOf(p[2]),
                                          Integer.parseInt(p[3]),
                                          load(p[4]));

                // Apply the saved status + maintenance count
                f.setStatus(Facility.FacilityStatus.valueOf(p[5]));
                f.setMaintenanceCount(Integer.parseInt(p[6]));

                facilities.add(f);
            }
            catch (Exception e) {
                // One bad line shouldn't stop the rest from loading
                System.err.println("Bad facility line: " + line);
            }
        }
    }

    /**
     * loadBookings — read bookings.txt and rebuild the bookings ArrayList.
     * Each row has 11 columns:
     *   bookingId | userId | facilityId | date | start | end |
     *   purpose   | description | status | adminRemark | reminderSent
     * Like loadUsers(), we also bump the bookingCounter past the highest
     * existing BKxxxx ID so we don't reuse any.
     */
    private void loadBookings() {
        bookings.clear();
        for (String line : readLines(BOOKING_FILE)) {
            String[] p = splitByPipe(line);
            if (p.length < 11) {
                continue;   // skip malformed lines
            }
            try {
                // Build the booking with its 8 constructor fields
                Booking b = new Booking(load(p[0]), load(p[1]), load(p[2]),
                                         load(p[3]), load(p[4]), load(p[5]),
                                         Booking.BookingPurpose.valueOf(p[6]),
                                         load(p[7]));

                // Apply the saved status + remark + reminder flag
                b.setStatus(Booking.BookingStatus.valueOf(p[8]));
                b.setAdminRemark(load(p[9]));
                b.setReminderSent(Boolean.parseBoolean(p[10]));
                bookings.add(b);

                // Bump the booking counter past any existing BKxxxx ID
                if (load(p[0]).startsWith("BK")) {
                    try {
                        int n = Integer.parseInt(load(p[0]).substring(2));
                        if (n >= bookingCounter) {
                            bookingCounter = n + 1;
                        }
                    } catch (Exception ignored) {
                        // not a numeric BK id — skip
                    }
                }
            } catch (Exception e) {
                System.err.println("Bad booking line: " + line);
            }
        }
    }

    /**
     * loadReports — read reports.txt and rebuild the reports ArrayList.
     * Each row has 10 columns:
     *   reportId | facilityId | reportedBy | description | category |
     *   status   | assignedTo | reportedDate | resolvedDate | notes
     * Note: assignedTo / resolvedDate / notes may all be "NULL" for reports
     * that have not yet been assigned or resolved. We only restore those
     * fields when assignedTo is non-NULL.
     */
    private void loadReports() {
        reports.clear();

        for (String line : readLines(REPORT_FILE)) {
            String[] p = splitByPipe(line);

            if (p.length < 10) {
                continue;   // skip malformed lines
            }

            try {
                // Build the report with its 6 constructor fields
                MaintenanceReport r = new MaintenanceReport(
                        load(p[0]), load(p[1]), load(p[2]), load(p[3]),
                        MaintenanceReport.IssueCategory.valueOf(p[4]),
                        load(p[7]));

                // Apply the saved status (constructor default is REPORTED)
                r.setStatus(MaintenanceReport.ReportStatus.valueOf(p[5]));

                // The "assigned" parts are only meaningful if assignedTo is set
                if (!"NULL".equals(p[6])) {
                    r.setAssignedTo(load(p[6]));
                    r.setResolvedDate(load(p[8]));
                    r.setResolutionNotes(load(p[9]));
                    reports.add(r);

                    // Bump the report counter past any existing MRxxxx ID
                    if (load(p[0]).startsWith("MR")) {
                        try {
                            int n = Integer.parseInt(load(p[0]).substring(2));
                            if (n >= reportCounter) {
                                reportCounter = n + 1;
                            }
                        } catch (Exception ignored) {
                            // not a numeric MR id — skip
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Bad report line: " + line);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ID GENERATORS — produce the next unique ID for each entity type
    //  Format examples:  STU017, STAFF03, MAINT02, BK0008, MR0004
    //  Each method bumps its counter so the same ID is never given out twice.
    // ─────────────────────────────────────────────────────────────────────────
    public String nextStudentId() {
        return String.format("STU%03d", studentCounter++);
    }
    public String nextStaffId() {
        return String.format("STAFF%02d", staffCounter++);
    }
    public String nextMaintenanceId() {
        return String.format("MAINT%02d", maintenanceCounter++);
    }
    public String nextBookingId() {
        return String.format("BK%04d", bookingCounter++);
    }
    public String nextReportId() {
        return String.format("MR%04d", reportCounter++);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CRUD — Users
    //  All add* methods follow the same "upsert" pattern:
    //    if a record with the same ID exists  → replace it
    //    otherwise                            → add it as new
    //  Then save everything to disk so the file stays in sync.
    // ─────────────────────────────────────────────────────────────────────────

    /** addUser — insert OR replace (matched by userId), then persist to disk. */
    public void addUser(User u) {
        // Look for an existing entry with the same ID and replace it
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUserId().equals(u.getUserId())) {
                users.set(i, u);
                saveAll();
                return;
            }
        }
        // No match → it's a brand-new user
        users.add(u);
        saveAll();
    }

    /** getUserById — find by ID (case-insensitive); null if not found. */
    public User getUserById(String id) {
        for (User u : users)
            if (u.getUserId().equalsIgnoreCase(id)) {
                return u;
            }
        return null;
    }

    /** getUserByEmail — find by email (case-insensitive); used during login. */
    public User getUserByEmail(String email) {
        for (User u : users)
            if (u.getEmail().equalsIgnoreCase(email)) {
                return u;
            }
        return null;
    }

    /**
     * getAllUsers — return a *copy* of the list so external code can't
     * accidentally modify our internal storage.  Same protection applied
     * to the other getAll*() methods.
     */
    public ArrayList<User> getAllUsers() {
        return new ArrayList<>(users);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CRUD — Facilities
    // ─────────────────────────────────────────────────────────────────────────

    /** addFacility — upsert by facilityId, then persist. */
    public void addFacility(Facility f) {
        for (int i = 0; i < facilities.size(); i++) {
            if (facilities.get(i).getFacilityId().equals(f.getFacilityId())) {
                facilities.set(i, f);
                saveAll();
                return;
            }
        }
        facilities.add(f);
        saveAll();
    }

    /** getFacilityById — find by ID; returns null if not found. */
    public Facility getFacilityById(String id) {
        for (Facility f : facilities)
            if (f.getFacilityId().equals(id)) {
                return f;
            }
        return null;
    }

    /** getAllFacilities — defensive copy of all facilities. */
    public ArrayList<Facility> getAllFacilities() {
        return new ArrayList<>(facilities);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CRUD — Bookings
    // ─────────────────────────────────────────────────────────────────────────

    /** addBooking — upsert by bookingId, then persist. */
    public void addBooking(Booking b) {
        for (int i = 0; i < bookings.size(); i++) {
            if (bookings.get(i).getBookingId().equals(b.getBookingId())) {
                bookings.set(i, b);
                saveAll();
                return;
            }
        }
        bookings.add(b);
        saveAll();
    }

    /** getBookingById — find by ID; returns null if not found. */
    public Booking getBookingById(String id) {
        for (Booking b : bookings)
            if (b.getBookingId().equals(id)) {
                return b;
            }
        return null;
    }

    /** getAllBookings — defensive copy of all bookings. */
    public ArrayList<Booking> getAllBookings() {
        return new ArrayList<>(bookings);
    }

    /** getBookingsByUser — return only the bookings owned by the given user. */
    public ArrayList<Booking> getBookingsByUser(String userId) {
        ArrayList<Booking> result = new ArrayList<>();
        for (Booking b : bookings)
            if (b.getUserId().equals(userId)) {
                result.add(b);
            }
        return result;
    }

    /** getBookingsByFacility — return only the bookings for the given facility. */
    public ArrayList<Booking> getBookingsByFacility(String facilityId) {
        ArrayList<Booking> result = new ArrayList<>();
        for (Booking b : bookings)
            if (b.getFacilityId().equals(facilityId)) {
                result.add(b);
            }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CRUD — Maintenance Reports
    // ─────────────────────────────────────────────────────────────────────────

    /** addReport — upsert by reportId, then persist. */
    public void addReport(MaintenanceReport r) {
        for (int i = 0; i < reports.size(); i++) {
            if (reports.get(i).getReportId().equals(r.getReportId())) {
                reports.set(i, r);
                saveAll();
                return;
            }
        }
        reports.add(r);
        saveAll();
    }

    /** getReportById — find by ID; returns null if not found. */
    public MaintenanceReport getReportById(String id) {
        for (MaintenanceReport r : reports)
            if (r.getReportId().equals(id)) {
                return r;
            }
        return null;
    }

    /** getAllReports — defensive copy of all reports. */
    public ArrayList<MaintenanceReport> getAllReports() {
        return new ArrayList<>(reports);
    }

    /** getReportsByFacility — every report attached to one facility. */
    public ArrayList<MaintenanceReport> getReportsByFacility(String facilityId) {
        ArrayList<MaintenanceReport> result = new ArrayList<>();
        for (MaintenanceReport r : reports)
            if (r.getFacilityId().equals(facilityId)) {
                result.add(r);
            }
        return result;
    }

    /**
     * getReportsAssignedTo — every report assigned to one maintenance worker.
     * Used by the worker's own menu so they can see their own task list.
     */
    public ArrayList<MaintenanceReport> getReportsAssignedTo(String personnelId) {
        ArrayList<MaintenanceReport> result = new ArrayList<>();
        for (MaintenanceReport r : reports)
            if (r.getAssignedTo() != null && r.getAssignedTo().equalsIgnoreCase(personnelId)) {
                result.add(r);
            }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SEED DATA — sample data loaded on the very first run only
    //
    //  When users.txt does not exist (i.e. the program has never been run
    //  before), the constructor calls seedData() to populate the four
    //  ArrayLists with demo records. Then saveAll() writes them to disk so
    //  the next run loads them normally instead of seeding again.
    //
    //  Seeded records:
    //    • 1 Admin account               (admin@utar.edu.my / admin123)
    //    • 6 Staff users                 (1 demo + 5 real-looking)
    //    • 14 Student users              (1 demo + 13 real-looking)
    //    • 4 Maintenance Personnel       (1 demo + 3 real-looking)
    //    • Multiple Facilities of every type
    //    • A few sample Bookings (mix of statuses)
    //    • A few sample Maintenance Reports (mix of statuses)
    //
    //  Demo accounts make it easy for the lecturer to test logging in
    //  without having to register first.
    // ─────────────────────────────────────────────────────────────────────────
    private void seedData() {
        // ── Admin (1 account) ───────────────────────────────────────────────
        users.add(new Admin("ADMIN01", "Admin Office", "admin@utar.edu.my", "admin123", "03-90860288"));

        // ── Staff users (6 — first one is a demo account for easy testing) ──
        String[][] staffData = {
                {"Demo Staff", "staff@utar.edu.my",     "pass123","010-0000001", "Lee Kong Chian Faculty of Engineering and Science (LKC FES)","Engineering"},
                {"Dr. Wong Chee Seng",    "wong.cs@utar.edu.my",   "pass123","019-1122334", "Lee Kong Chian Faculty of Engineering and Science (LKC FES)","Engineering"},
                {"Dr. Suraya binti Miskon","suraya.m@utar.edu.my", "pass123","013-2233445", "Lee Kong Chian Faculty of Engineering and Science (LKC FES)","Computing"},
                {"Mr. Rajendran Pillai",  "raj.p@utar.edu.my",     "pass123","016-3344556", "Faculty of Accountancy and Management (FAM)","Accounting"},
                {"Ms. Chua Siew Fong",    "chua.sf@utar.edu.my",   "pass123","012-4455667", "Faculty of Creative Industries (FCI)","Design"},
                {"Assoc. Prof. Dr. Lau Kok Lim","lau.kl@utar.edu.my","pass123","011-5566778", "Lee Kong Chian Faculty of Engineering and Science (LKC FES)","Electronics"},
        };
        for (String[] d : staffData) {
            users.add(new CampusUser(nextStaffId(), d[0], d[1], d[2], d[3], d[4], User.UserRole.STAFF, d[5]));
        }

        // Students (16 — includes 1 demo account for easy testing)
        String[][] stuData = {
                {"Demo Student", "student@utar.edu.my", "pass123","010-0000002", "Lee Kong Chian Faculty of Engineering and Science (LKC FES)","Software Engineering"},
                {"Lee Xiao Ming", "lee.xm@utar.edu.my", "pass123","011-2233445", "Lee Kong Chian Faculty of Engineering and Science (LKC FES)","Computer Science"},
                {"Priya a/p Nair", "priya.n@utar.edu.my", "pass123","017-8877665", "Lee Kong Chian Faculty of Engineering and Science (LKC FES)","Software Engineering"},
                {"Muhammad Hafiz bin Aziz", "hafiz.a@utar.edu.my",  "pass123","013-5544332", "Faculty of Accountancy and Management (FAM)","Accounting"},
                {"Nur Aisyah binti Rosli", "aisyah.r@utar.edu.my", "pass123","014-6677889", "Faculty of Creative Industries (FCI)","Graphic Design"},
                {"Chen Wei Jie", "chen.wj@utar.edu.my",  "pass123","016-1122334", "Lee Kong Chian Faculty of Engineering and Science (LKC FES)","Electrical Engineering"},
                {"Kavitha a/p Subramaniam", "kavitha.s@utar.edu.my","pass123","012-9988776", "Faculty of Medicine and Health Sciences (FMHS)","Biomedical Science"},
                {"Lim Jun Kai", "lim.jk@utar.edu.my", "pass123","019-3344556", "Faculty of Accountancy and Management (FAM)","Finance"},
                {"Siti Nabilah binti Hamdan","nabilah.h@utar.edu.my","pass123","011-5566778", "Faculty of Creative Industries (FCI)","Mass Communication"},
                {"Yew Kian Hong", "yew.kh@utar.edu.my", "pass123","017-2233445", "Lee Kong Chian Faculty of Engineering and Science (LKC FES)","Civil Engineering"},
                {"Amirul Hakim bin Saad", "amirul.s@utar.edu.my", "pass123","013-7788990", "Lee Kong Chian Faculty of Engineering and Science (LKC FES)","Mechatronics"},
                {"Tan Mei Ling", "tan.ml@utar.edu.my", "pass123","016-4455667", "Faculty of Accountancy and Management (FAM)","Business Administration"},
                {"Harpreet Singh", "harpreet.s@utar.edu.my","pass123","012-6677889", "Institute of Chinese Studies (ICS)","Chinese Studies"},
                {"Noor Hazwani binti Kamal","hazwani.k@utar.edu.my","pass123","019-1122334", "Centre for Foundation Studies (CFS)","Foundation in Science"},
                {"Ong Shi Hao", "ong.sh@utar.edu.my",   "pass123","011-8899001", "Lee Kong Chian Faculty of Engineering and Science (LKC FES)","Information Systems"},
                {"Farah Hani binti Zainal", "farah.z@utar.edu.my",  "pass123","017-5544332", "Faculty of Accountancy and Management (FAM)","Marketing"},
        };
        for (String[] d : stuData) {
            users.add(new CampusUser(nextStudentId(), d[0], d[1], d[2], d[3], d[4], User.UserRole.STUDENT, d[5]));
        }

        // Facilities — real KA & KB Block rooms, UTAR Sungai Long Campus
        String[][] fdata = {

                // ── KA Block: Lecture Theatres (Ground Floor) ──────────────────
                {"F001","Lecture Theatre KG01",                    "LECTURE_HALL",     "250","KA Block, Ground Floor"},
                {"F002","Lecture Theatre KG02",                    "LECTURE_HALL",     "250","KA Block, Ground Floor"},
                {"F003","Lecture Theatre KG03",                    "LECTURE_HALL",     "250","KA Block, Ground Floor"},

                // ── KA Block: Lecture Rooms (Level 4, 5, 7, 8) ────────────────
                {"F004","Lecture Room K401",                       "LECTURE_HALL",     "80", "KA Block, Level 4"},
                {"F005","Lecture Room K402",                       "LECTURE_HALL",     "80", "KA Block, Level 4"},
                {"F006","Lecture Room K506",                       "LECTURE_HALL",     "80", "KA Block, Level 5"},
                {"F007","Lecture Room K512",                       "LECTURE_HALL",     "80", "KA Block, Level 5"},
                {"F008","Lecture Hall K7",                         "LECTURE_HALL",     "200","KA Block, Level 7"},
                {"F009","Lecture Room K804",                       "LECTURE_HALL",     "80", "KA Block, Level 8"},
                {"F010","Lecture Room K805",                       "LECTURE_HALL",     "80", "KA Block, Level 8"},

                // ── KA Block: Tutorial Rooms ───────────────────────────────────
                {"F011","Tutorial Room K209",                      "DISCUSSION_ROOM",  "30", "KA Block, Level 2"},
                {"F012","Tutorial Room K210",                      "DISCUSSION_ROOM",  "30", "KA Block, Level 2"},
                {"F013","Tutorial Room K211",                      "DISCUSSION_ROOM",  "30", "KA Block, Level 2"},
                {"F014","Tutorial Room K400A",                     "DISCUSSION_ROOM",  "30", "KA Block, Level 4"},
                {"F015","Tutorial Room K400B",                     "DISCUSSION_ROOM",  "30", "KA Block, Level 4"},
                {"F016","Tutorial Room K400C",                     "DISCUSSION_ROOM",  "30", "KA Block, Level 4"},
                {"F017","Tutorial Room K501",                      "DISCUSSION_ROOM",  "25", "KA Block, Level 5"},
                {"F018","Tutorial Room K502",                      "DISCUSSION_ROOM",  "25", "KA Block, Level 5"},
                {"F019","Tutorial Room K503",                      "DISCUSSION_ROOM",  "25", "KA Block, Level 5"},
                {"F020","Tutorial Room K504",                      "DISCUSSION_ROOM",  "25", "KA Block, Level 5"},
                {"F021","Tutorial Room K505",                      "DISCUSSION_ROOM",  "25", "KA Block, Level 5"},
                {"F022","Tutorial Room K507",                      "DISCUSSION_ROOM",  "25", "KA Block, Level 5"},
                {"F023","Tutorial Room K508",                      "DISCUSSION_ROOM",  "25", "KA Block, Level 5"},
                {"F024","Tutorial Room K509",                      "DISCUSSION_ROOM",  "25", "KA Block, Level 5"},
                {"F025","Tutorial Room K510",                      "DISCUSSION_ROOM",  "25", "KA Block, Level 5"},
                {"F026","Tutorial Room K511",                      "DISCUSSION_ROOM",  "25", "KA Block, Level 5"},
                {"F027","Tutorial Room K800",                      "DISCUSSION_ROOM",  "30", "KA Block, Level 8"},
                {"F028","Tutorial Room K801",                      "DISCUSSION_ROOM",  "30", "KA Block, Level 8"},
                {"F029","Tutorial Room K802",                      "DISCUSSION_ROOM",  "30", "KA Block, Level 8"},
                {"F030","Tutorial Room K803",                      "DISCUSSION_ROOM",  "30", "KA Block, Level 8"},

                // ── KA Block: Computer & Internet Labs (Level 4) ──────────────
                {"F031","Computer and Internet Lab K403",          "COMPUTER_LAB",     "40", "KA Block, Level 4"},
                {"F032","Computer and Internet Lab K404",          "COMPUTER_LAB",     "40", "KA Block, Level 4"},
                {"F033","Computer and Internet Lab K405",          "COMPUTER_LAB",     "40", "KA Block, Level 4"},
                {"F034","Computer and Internet Lab K406",          "COMPUTER_LAB",     "40", "KA Block, Level 4"},
                {"F035","Computer and Internet Lab K407",          "COMPUTER_LAB",     "40", "KA Block, Level 4"},
                {"F036","Computer and Internet Lab K408",          "COMPUTER_LAB",     "40", "KA Block, Level 4"},
                {"F037","Computer and Internet Lab K409",          "COMPUTER_LAB",     "40", "KA Block, Level 4"},
                {"F038","Computer and Internet Lab K410",          "COMPUTER_LAB",     "40", "KA Block, Level 4"},
                {"F039","Computer and Internet Lab K411",          "COMPUTER_LAB",     "40", "KA Block, Level 4"},

                // ── KA Block: Sports & Meeting Rooms ──────────────────────────
                {"F040","Gymnasium",                               "SPORTS_COURT",     "50", "KA Block, Level 3"},
                {"F041","Meeting Room (Mezzanine)",                "DISCUSSION_ROOM",  "20", "KA Block, Mezzanine Floor"},
                {"F042","Meeting Room K6",                         "DISCUSSION_ROOM",  "20", "KA Block, Level 6"},

                // ── KB Block: Lecture Theatres ────────────────────────────────
                {"F043","Lecture Theatre KB100",                   "LECTURE_HALL",     "250","KB Block, Level 1"},
                {"F044","Lecture Theatre KB103",                   "LECTURE_HALL",     "200","KB Block, Level 1"},
                {"F045","Lecture Theatre KB104",                   "LECTURE_HALL",     "200","KB Block, Level 1"},
                {"F046","Lecture Theatre KB105",                   "LECTURE_HALL",     "200","KB Block, Level 1"},
                {"F047","Lecture Theatre KB207",                   "LECTURE_HALL",     "200","KB Block, Level 2"},
                {"F048","Lecture Theatre KB208",                   "LECTURE_HALL",     "200","KB Block, Level 2"},
                {"F049","Lecture Theatre KB209",                   "LECTURE_HALL",     "200","KB Block, Level 2"},
                {"F050","Lecture Theatre KB213",                   "LECTURE_HALL",     "200","KB Block, Level 2"},
                {"F051","Lecture Theatre KB214",                   "LECTURE_HALL",     "200","KB Block, Level 2"},

                // ── KB Block: Lecture Rooms ───────────────────────────────────
                {"F052","Lecture Room KB106",                      "LECTURE_HALL",     "80", "KB Block, Level 1"},
                {"F053","Lecture Room KB107",                      "LECTURE_HALL",     "80", "KB Block, Level 1"},
                {"F054","Lecture Room KB200",                      "LECTURE_HALL",     "80", "KB Block, Level 2"},
                {"F055","Lecture Room KB201",                      "LECTURE_HALL",     "80", "KB Block, Level 2"},
                {"F056","Lecture Room KB202",                      "LECTURE_HALL",     "80", "KB Block, Level 2"},
                {"F057","Lecture Room KB203",                      "LECTURE_HALL",     "80", "KB Block, Level 2"},
                {"F058","Lecture Room KB204",                      "LECTURE_HALL",     "80", "KB Block, Level 2"},
                {"F059","Lecture Room KB205",                      "LECTURE_HALL",     "80", "KB Block, Level 2"},
                {"F060","Lecture Room KB206",                      "LECTURE_HALL",     "80", "KB Block, Level 2"},
                {"F061","Lecture Room KB210",                      "LECTURE_HALL",     "80", "KB Block, Level 2"},
                {"F062","Lecture Room KB211",                      "LECTURE_HALL",     "80", "KB Block, Level 2"},
                {"F063","Lecture Room KB300",                      "LECTURE_HALL",     "80", "KB Block, Level 3"},
                {"F064","Lecture Room KB301",                      "LECTURE_HALL",     "80", "KB Block, Level 3"},
                {"F065","Lecture Room KB315",                      "LECTURE_HALL",     "80", "KB Block, Level 3"},
                {"F066","Lecture Room KB316",                      "LECTURE_HALL",     "80", "KB Block, Level 3"},
                {"F067","Lecture Room KB322",                      "LECTURE_HALL",     "80", "KB Block, Level 3"},
                {"F068","Lecture Room KB323",                      "LECTURE_HALL",     "80", "KB Block, Level 3"},
                {"F069","Lecture Room KB324",                      "LECTURE_HALL",     "80", "KB Block, Level 3"},

                // ── KB Block: Tutorial Rooms ──────────────────────────────────
                {"F070","Tutorial Room KB101",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 1"},
                {"F071","Tutorial Room KB102",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 1"},
                {"F072","Tutorial Room KB110",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 1"},
                {"F073","Tutorial Room KB112",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 1"},
                {"F074","Tutorial Room KB303",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F075","Tutorial Room KB304",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F076","Tutorial Room KB305",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F077","Tutorial Room KB306",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F078","Tutorial Room KB307",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F079","Tutorial Room KB308",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F080","Tutorial Room KB309",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F081","Tutorial Room KB310",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F082","Tutorial Room KB311",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F083","Tutorial Room KB312",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F084","Tutorial Room KB313",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F085","Tutorial Room KB314",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F086","Tutorial Room KB318",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F087","Tutorial Room KB319",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F088","Tutorial Room KB320",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F089","Tutorial Room KB321",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 3"},
                {"F090","Tutorial Room KB701",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 7"},
                {"F091","Tutorial Room KB702",                     "DISCUSSION_ROOM",  "30", "KB Block, Level 7"},

                // ── KB Block: Computer Teaching Labs (Level 6) ────────────────
                {"F092","LKC FES Computer Teaching Lab KB604",     "COMPUTER_LAB",     "40", "KB Block, Level 6"},
                {"F093","LKC FES Computer Teaching Lab KB605",     "COMPUTER_LAB",     "40", "KB Block, Level 6"},
                {"F094","LKC FES Computer Teaching Lab KB606",     "COMPUTER_LAB",     "40", "KB Block, Level 6"},
                {"F095","LKC FES Computer Teaching Lab KB607",     "COMPUTER_LAB",     "40", "KB Block, Level 6"},
                {"F096","LKC FES Computer Teaching Lab KB608",     "COMPUTER_LAB",     "40", "KB Block, Level 6"},

                // ── KB Block: Sports, Multipurpose & Meeting Rooms ────────────
                {"F097","Sports Court (KB Block)",                 "SPORTS_COURT",     "60", "KB Block, Level 1"},
                {"F098","Student Activity Centre",                 "SPORTS_COURT",     "100","KB Block, Level 1"},
                {"F099","Multipurpose Hall (KB Ground)",           "MULTIPURPOSE_HALL","400","KB Block, Ground Floor"},
                {"F100","CFS Multipurpose Hall",                   "MULTIPURPOSE_HALL","300","KB Block, Level 5"},
                {"F101","Multipurpose Area (KB Level 1)",          "MULTIPURPOSE_HALL","150","KB Block, Level 1"},
                {"F102","Meeting Room (KB Ground)",                "DISCUSSION_ROOM",  "20", "KB Block, Ground Floor"},
        };
        for (String[] d : fdata) {
            facilities.add(new Facility(d[0], d[1],
                    Facility.FacilityType.valueOf(d[2]),
                    Integer.parseInt(d[3]), d[4]));
        }
        // KB303 (F074) marked under maintenance as sample
        getFacilityById("F074").setStatus(Facility.FacilityStatus.UNDER_MAINTENANCE);

        // Sample bookings (facility IDs updated to match new list)
        addSeedBooking("BK0001","STU001","F001","20/04/2026","09:00","11:00", Booking.BookingPurpose.ACADEMIC,"Project presentation",Booking.BookingStatus.APPROVED);
        addSeedBooking("BK0002","STU002","F011","21/04/2026","14:00","16:00", Booking.BookingPurpose.ACADEMIC,"FYP discussion",Booking.BookingStatus.PENDING);
        addSeedBooking("BK0003","STAFF01","F031","22/04/2026","10:00","12:00", Booking.BookingPurpose.ACADEMIC,"Lab session",Booking.BookingStatus.APPROVED);
        addSeedBooking("BK0004","STU003","F099","23/04/2026","08:00","12:00", Booking.BookingPurpose.CO_CURRICULAR,"Club event",Booking.BookingStatus.PENDING);
        bookingCounter = 5;

        // Maintenance Personnel (3 — includes 1 demo account for easy testing)
        users.add(new MaintenancePersonnel("MAINT01", "Demo Maintenance",
                "maintenance@utar.edu.my", "pass123", "010-0000003",
                "Facility Management Office", "General"));
        users.add(new MaintenancePersonnel("MAINT02", "Ali bin Hassan",
                "ali.h@utar.edu.my", "pass123", "011-1234567",
                "Facility Management Office", "Electrical"));
        users.add(new MaintenancePersonnel("MAINT03", "Kumar Selvam",
                "kumar.s@utar.edu.my", "pass123", "016-9876543",
                "Facility Management Office", "HVAC"));
        maintenanceCounter = 4;

        // Sample maintenance reports (facility IDs updated to match new list)
        MaintenanceReport mr1 = new MaintenanceReport("MR0001", "F074", "STU001", "Air conditioning not working",
                MaintenanceReport.IssueCategory.AIR_CONDITIONING, "15/04/2026");
        mr1.setAssignedTo("MAINT01");
        mr1.setStatus(MaintenanceReport.ReportStatus.IN_PROGRESS);
        reports.add(mr1);

        MaintenanceReport mr2 = new MaintenanceReport("MR0002", "F031", "STAFF01", "Two computers have broken keyboards", MaintenanceReport.IssueCategory.EQUIPMENT_DAMAGE, "16/04/2026");
        mr2.setAssignedTo("MAINT03");
        mr2.setStatus(MaintenanceReport.ReportStatus.RESOLVED);
        mr2.setResolvedDate("18/04/2026");
        mr2.setResolutionNotes("Keyboards replaced.");
        reports.add(mr2);

        MaintenanceReport mr3 = new MaintenanceReport("MR0003", "F040", "STU005", "Broken equipment in gymnasium", MaintenanceReport.IssueCategory.EQUIPMENT_DAMAGE, "17/04/2026");
        reports.add(mr3);
        reportCounter = 4;
        getFacilityById("F074").setMaintenanceCount(1);
        getFacilityById("F031").setMaintenanceCount(1);
        getFacilityById("F040").setMaintenanceCount(1);
    }

    /**
     * addSeedBooking — small helper used only by seedData().
     *
     * Creates a Booking, sets the status (so we can seed APPROVED, REJECTED,
     * etc., not just the default PENDING), and adds it to the bookings list.
     */
    private void addSeedBooking(String id, String uid, String fid, String date,
                                 String start, String end, Booking.BookingPurpose purpose,
                                 String desc, Booking.BookingStatus status) {
        Booking b = new Booking(id, uid, fid, date, start, end, purpose, desc);
        b.setStatus(status);   // override the default PENDING
        bookings.add(b);
    }
}
