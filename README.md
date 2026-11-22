# Smart Campus Facility Booking & Maintenance System

A Java application for booking campus facilities and tracking their maintenance at UTAR Sungai Long Campus. Students and staff find a free room, book it once or every week, and report broken equipment with a photo. Admin staff approve requests, assign repairs, follow a weekly timetable and export reports as a PDF. Maintenance staff see and close their tasks.

It runs as a **desktop app (Java Swing)** or in the **terminal**. Both use the same business rules, and all data is kept in plain text files.

![Java 17+](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white)
![GUI: Swing](https://img.shields.io/badge/GUI-Swing-5382A1)
![No dependencies](https://img.shields.io/badge/dependencies-none-2ea44f)
![Storage: text files](https://img.shields.io/badge/storage-.txt%20files-blue)

<p align="center">
  <img src="docs/screenshots/gui/student-timetable.png" alt="Weekly timetable for a lecture theatre" width="49%">
  <img src="docs/screenshots/gui/admin-reports.png" alt="Admin reports dashboard with charts" width="49%">
</p>

**Contents:** [Problem statement](#problem-statement) · [Objectives](#objectives) · [Project history](#project-history) · [Features](#features) · [Screenshots](#screenshots) · [Booking rules](#booking-rules) · [Design](#design) · [Getting started](#getting-started) · [Data files](#data-files) · [Bugs fixed](#bugs-fixed-after-submission)

## Problem statement

At UTAR Sungai Long, most facility bookings (lecture rooms, multipurpose halls, computer labs and science labs) are handled by hand.

- **Slow, manual checking.** Staff keep timetables on paper or in Excel and check every request against them by hand. Finding a record takes longer as the records grow, and manual entry leads to mistakes.
- **Bookings depend on one person.** Requests come by email, WhatsApp or in person, and the person in charge has to reply to approve them. If that person is out of the office or busy, nobody can book, and users who forget a required document have to come back.
- **No common process.** Each department has its own way of booking and its own person to contact.

## Objectives

1. **Automate facility booking and maintenance management.** Build a system that manages facility bookings and maintenance reports for the Sungai Long campus.
2. **Make reservations efficient.** Let students and staff search for free facilities, submit booking requests and manage their reservations without contacting the person in charge.
3. **Track and analyse facility usage.** Let users and administrators track booking records, monitor facility usage and produce reports that support better facility management.

## Project history

**Original coursework (Feb 2026).** This began as a group project for **UECS1144 Object-Oriented Application Development** at Universiti Tunku Abdul Rahman (UTAR). I led a team of four. The brief asked for an object-oriented Java system that uses `.txt` files as its database, is seeded with at least 20 UTAR users, and comes with a requirements specification and a UML class diagram. We submitted a console application: about 5,000 lines of plain Java in 20 classes, with no frameworks or libraries. The code exactly as submitted is in commit [`c3b6cd7`](https://github.com/zijing0907/smart-campus-facility-booking/tree/c3b6cd7).

**After submission.** I kept improving it. I added a Swing desktop interface on top of the same service layer, then weekly bookings, a timetable, notifications, an activity log, PDF reports, photo attachments, password hashing and login lock-out. I also fixed [8 bugs](#bugs-fixed-after-submission) in the original code. The console version is still included and follows the same rules. The project is now about 11,000 lines in 48 classes, still using only the JDK.

## Features

### Students and staff
- Register with a UTAR email address (`@utar.edu.my`), log in, update profile details and change password
- Search for free facilities by date, time slot, facility type and **number of people**; only rooms big enough are listed
- Book once, or **every week for up to 14 weeks**: all weeks are checked first, and nothing is booked if any week is taken
- See a **weekly timetable** for any facility, and click an empty slot to book it
- Change a booking while it is pending, or cancel it (one week or all remaining weeks) up to 24 hours before it starts
- Report a facility problem, optionally with a **photo or PDF**
- **Notifications** when a booking is approved or rejected, a report is assigned or fixed, or a booked facility is closed or put under maintenance
- A reminder for approved bookings that start within the next 2 hours

### Admin staff
- Approve or reject requests (a whole weekly booking at once), with a warning when a request overlaps an approved booking
- The weekly timetable, showing who booked each slot
- Add and edit facilities, and mark them Available, Under Maintenance or Closed. People with upcoming bookings there are notified.
- View maintenance reports and attached photos, and assign them to maintenance staff
- Reports with charts: peak hours, bookings per facility type, facility usage for a month, semester or year, and a maintenance summary. **Export everything as a PDF.**
- A list of all users with search, and **unlock** accounts locked after too many wrong passwords
- An **activity log** of who did what and when
- An alert when a facility has 3 or more unresolved issues

### Maintenance staff
- View assigned tasks and their photos, mark them In Progress or Resolved with notes, and get notified about new tasks

### Security
- Passwords are stored as salted **PBKDF2** hashes, never as plain text
- An account is locked for 15 minutes after 5 wrong passwords in a row
- Uploaded files are checked by type, size and file signature, so a renamed program is refused

The desktop app has every feature above. The console app has the original menus plus headcounts, notifications and attachments. Weekly bookings, the timetable, facility editing, PDF export, unlocking accounts and the activity log view are desktop only.

## Screenshots

These are taken from the running desktop app. Every screen of the [console version](#console-version-the-original-coursework) is shown after them. A sample exported report is in [`docs/sample-report.pdf`](docs/sample-report.pdf).

### Students and staff

<table>
<tr>
<td width="50%"><b>Find a free room for 35 people</b><br><img src="docs/screenshots/gui/student-book.png" alt="Search results for free computer labs"></td>
<td width="50%"><b>Book the same slot every week</b><br><img src="docs/screenshots/gui/student-book-weekly.png" alt="Weekly booking search"></td>
</tr>
<tr>
<td><b>Weekly timetable (your bookings are outlined)</b><br><img src="docs/screenshots/gui/student-timetable.png" alt="Weekly timetable"></td>
<td><b>My bookings</b><br><img src="docs/screenshots/gui/student-my-bookings.png" alt="List of the student's bookings"></td>
</tr>
<tr>
<td><b>Report an issue with a photo</b><br><img src="docs/screenshots/gui/student-report-issue.png" alt="Maintenance report form with a photo preview"></td>
<td><b>Notifications</b><br><img src="docs/screenshots/gui/student-notifications.png" alt="Notifications list"></td>
</tr>
</table>

### Admin

<table>
<tr>
<td width="50%"><b>Booking requests</b><br><img src="docs/screenshots/gui/admin-bookings.png" alt="Pending booking requests"></td>
<td width="50%"><b>Approve a whole weekly booking</b><br><img src="docs/screenshots/gui/admin-approve-weekly.png" alt="Approve all weeks dialog"></td>
</tr>
<tr>
<td><b>Timetable with names</b><br><img src="docs/screenshots/gui/admin-timetable.png" alt="Admin timetable"></td>
<td><b>Reports dashboard</b><br><img src="docs/screenshots/gui/admin-reports.png" alt="Charts of peak hours and bookings by type"></td>
</tr>
<tr>
<td><b>Exported PDF report</b><br><img src="docs/screenshots/gui/admin-pdf-report.png" alt="First page of the PDF report"></td>
<td><b>Maintenance reports</b><br><img src="docs/screenshots/gui/admin-maintenance.png" alt="Maintenance reports with an attachment column"></td>
</tr>
<tr>
<td><b>Viewing an attached photo</b><br><img src="docs/screenshots/gui/admin-attachment.png" alt="Attached photo"></td>
<td><b>Adding a facility</b><br><img src="docs/screenshots/gui/admin-add-facility.png" alt="Add facility form"></td>
</tr>
<tr>
<td><b>Users, with a locked account</b><br><img src="docs/screenshots/gui/admin-users.png" alt="Users list"></td>
<td><b>Activity log</b><br><img src="docs/screenshots/gui/admin-activity-log.png" alt="Activity log"></td>
</tr>
</table>

### Maintenance staff and login

<table>
<tr>
<td width="50%"><b>My tasks</b><br><img src="docs/screenshots/gui/maintenance-tasks.png" alt="Maintenance tasks"></td>
<td width="50%"><b>Login</b><br><img src="docs/screenshots/gui/login.png" alt="Login window"></td>
</tr>
</table>

### Console version (the original coursework)

The terminal app has one menu per role. These are all of its screens, captured from one session on the demo data. The numbers are the menu options. Click a group to open it.

<details>
<summary><b>Login and registration</b> (2 screens)</summary>
<br>

<table>
<tr>
<td width="50%" valign="top"><b>Register a new student account</b><br><img src="docs/screenshots/console/register.svg" alt="Register a new student account"></td>
<td width="50%" valign="top"><b>Log in</b><br><img src="docs/screenshots/console/login.svg" alt="Log in"></td>
</tr>
</table>
</details>

<details>
<summary><b>Students and staff</b> (10 screens)</summary>
<br>

<table>
<tr>
<td width="50%" valign="top"><b>Main menu, with new notifications</b><br><img src="docs/screenshots/console/user-portal.svg" alt="Main menu, with new notifications"></td>
<td width="50%" valign="top"><b>1 · Search for free computer labs</b><br><img src="docs/screenshots/console/search.svg" alt="Search for free computer labs"></td>
</tr>
<tr>
<td valign="top"><b>2 · Book a facility for 35 people</b><br><img src="docs/screenshots/console/book.svg" alt="Book a facility for 35 people"></td>
<td valign="top"><b>3 · My bookings</b><br><img src="docs/screenshots/console/my-bookings.svg" alt="My bookings"></td>
</tr>
<tr>
<td valign="top"><b>4 · Change a pending booking</b><br><img src="docs/screenshots/console/modify-booking.svg" alt="Change a pending booking"></td>
<td valign="top"><b>5 · Cancel a booking</b><br><img src="docs/screenshots/console/cancel-booking.svg" alt="Cancel a booking"></td>
</tr>
<tr>
<td valign="top"><b>6 · Report an issue with a photo</b><br><img src="docs/screenshots/console/report-issue.svg" alt="Report an issue with a photo"></td>
<td valign="top"><b>7 · Booking history and stats</b><br><img src="docs/screenshots/console/history-stats.svg" alt="Booking history and stats"></td>
</tr>
<tr>
<td valign="top"><b>8 · Update my profile</b><br><img src="docs/screenshots/console/profile.svg" alt="Update my profile"></td>
<td valign="top"><b>9 · Change password</b><br><img src="docs/screenshots/console/change-password.svg" alt="Change password"></td>
</tr>
</table>
</details>

<details>
<summary><b>Admin</b> (13 screens)</summary>
<br>

<table>
<tr>
<td width="50%" valign="top"><b>Main menu, with new notifications</b><br><img src="docs/screenshots/console/admin-panel.svg" alt="Main menu, with new notifications"></td>
<td width="50%" valign="top"><b>1 · Approve or reject bookings</b><br><img src="docs/screenshots/console/process-bookings.svg" alt="Approve or reject bookings"></td>
</tr>
<tr>
<td colspan="2" valign="top"><b>2 · All bookings</b><br><img src="docs/screenshots/console/all-bookings.svg" alt="All bookings"></td>
</tr>
<tr>
<td valign="top"><b>3 · Close a facility for maintenance</b><br><img src="docs/screenshots/console/facility-status.svg" alt="Close a facility for maintenance"></td>
<td valign="top"><b>4 · Maintenance reports</b><br><img src="docs/screenshots/console/maintenance-reports.svg" alt="Maintenance reports"></td>
</tr>
<tr>
<td colspan="2" valign="top"><b>5 · Assign a repair</b><br><img src="docs/screenshots/console/assign-task.svg" alt="Assign a repair"></td>
</tr>
<tr>
<td valign="top"><b>6 · Facility utilization report</b><br><img src="docs/screenshots/console/utilization.svg" alt="Facility utilization report"></td>
<td valign="top"><b>7 · Peak booking hours</b><br><img src="docs/screenshots/console/peak-hours.svg" alt="Peak booking hours"></td>
</tr>
<tr>
<td valign="top"><b>8 · Bookings by facility type</b><br><img src="docs/screenshots/console/type-usage.svg" alt="Bookings by facility type"></td>
<td valign="top"><b>9 · Maintenance summary</b><br><img src="docs/screenshots/console/maintenance-summary.svg" alt="Maintenance summary"></td>
</tr>
<tr>
<td colspan="2" valign="top"><b>10 · All users</b><br><img src="docs/screenshots/console/users.svg" alt="All users"></td>
</tr>
<tr>
<td valign="top"><b>11 · Send booking reminders</b><br><img src="docs/screenshots/console/reminders.svg" alt="Send booking reminders"></td>
<td valign="top"><b>12 · Maintenance schedule</b><br><img src="docs/screenshots/console/schedule.svg" alt="Maintenance schedule"></td>
</tr>
</table>
</details>

<details>
<summary><b>Maintenance staff</b> (4 screens)</summary>
<br>

<table>
<tr>
<td width="50%" valign="top"><b>Main menu, with a new task</b><br><img src="docs/screenshots/console/maintenance-panel.svg" alt="Main menu, with a new task"></td>
<td width="50%" valign="top"><b>1 · My assigned tasks</b><br><img src="docs/screenshots/console/maintenance-tasks.svg" alt="My assigned tasks"></td>
</tr>
<tr>
<td valign="top"><b>2 · Mark a task resolved</b><br><img src="docs/screenshots/console/maintenance-update.svg" alt="Mark a task resolved"></td>
<td valign="top"><b>3 · Completed tasks</b><br><img src="docs/screenshots/console/maintenance-completed.svg" alt="Completed tasks"></td>
</tr>
</table>
</details>

## Booking rules

| Rule | Where it lives |
|---|---|
| A facility can't be double-booked. Two bookings on the same date clash when `start1 < end2` and `start2 < end1`; cancelled and rejected bookings don't count. | `BookingService` |
| Bookings must fall between 08:00 and 22:00 and last at least 30 minutes. | `InputValidator` |
| Dates must be real calendar dates in the current or next year, and a slot that has already started can't be booked. | `InputValidator.isValidDate()`, `hasStarted()` |
| The number of people must fit the facility's capacity. | `BookingService.checkAttendees()` |
| A weekly booking lasts 2 to 14 weeks and is all or nothing: if any week is taken, nothing is booked. | `BookingService.createRecurringBooking()` |
| Only pending bookings can be changed. | `BookingService.modifyBooking()` |
| A booking can't be cancelled within 24 hours of its start time (for weekly bookings, per week). | `BookingService.cancelBooking()`, `cancelSeries()` |
| Facilities that are under maintenance or closed don't appear in search and can't be booked. | `BookingService.searchAvailable()` |
| A reminder is shown once for each approved booking that starts within 2 hours, only to the person who booked it. | `BookingService.getDueReminders()` |
| Admins are alerted when a facility has 3 or more unresolved maintenance reports. | `MaintenanceService.getFrequentIssueAlerts()` |
| 5 wrong passwords in a row lock the account for 15 minutes. | `UserService.login()` |
| Attachments must be JPG, PNG, GIF, BMP or PDF, at most 5 MB, and start with the right file signature. | `MaintenanceService.checkAttachment()` |

## Design

### Layers

```mermaid
flowchart TD
    GM["GuiMain<br/>desktop app"] --> GUI["gui<br/>LoginFrame · MainFrame · one panel per tab"]
    M["Main<br/>console app"] --> UI["ui<br/>AuthMenu · UserMenu · AdminMenu · MaintenanceMenu"]
    GUI & UI --> S["service<br/>UserService · BookingService · MaintenanceService · FacilityService<br/>AnalyticsService · ReportPdfService · NotificationService · ActivityLogService"]
    S --> DS["DataStore (singleton)<br/>in-memory lists + file I/O"]
    DS <--> F[("data/*.txt<br/>data/attachments/")]
```

- **`gui`** (Swing) and **`ui`** (console) only show data and collect input. They hold no business rules, so the two front ends always behave the same, and a booking made in one shows up in the other.
- **`service`** holds the rules: clash checks, capacity, weekly bookings, the cancellation policy, lock-out, notifications, the activity log and analytics.
- **`model`** holds the domain classes: users, facilities, bookings, maintenance reports, notifications and log entries.
- **`util`** has the console helpers, `InputValidator`, `PasswordHasher` and `PdfDocument`.

### OOP concepts used

| Concept | Where |
|---|---|
| Abstraction | `User` is an abstract class with the abstract method `getRoleLabel()`. `Reportable` and `Refreshable` are interfaces. |
| Inheritance | `CampusUser`, `Admin` and `MaintenancePersonnel` extend `User`. Every screen extends a Swing class, and `BarChartPanel` and `TimetableGrid` override `paintComponent()` to draw themselves. |
| Polymorphism | `DataStore` keeps every account in one `ArrayList<User>`, and each role gets a different menu or set of tabs. `MainFrame` calls `refresh()` on whichever tab is opened without knowing which panel it is. |
| Encapsulation | All fields are private, and setters exist only for fields that may change; IDs have none. `MaintenanceReport.setAssignedTo()` also moves the status to `ASSIGNED`, and `LogEntry` can't be changed at all. |
| Enums | `UserRole`, `FacilityType`, `FacilityStatus`, `BookingPurpose`, `BookingStatus`, `IssueCategory`, `ReportStatus`, `LogEntry.Action` |
| Design pattern | `DataStore` is a singleton, so every service reads and writes the same data. |
| Exception handling | Services throw `IllegalArgumentException` or `IllegalStateException` with a message, and both front ends catch them and show it. File I/O uses try-with-resources, and a corrupt line in a data file is skipped instead of stopping the load. |

### Implementation notes

- **No external libraries.** Everything uses the JDK only, including the parts that usually need one:
  - **PDF export:** `PdfDocument` writes the PDF format directly (objects, content streams and the cross-reference table), so charts and text stay sharp and selectable.
  - **Charts and timetable:** `BarChartPanel` and `TimetableGrid` are drawn with Java2D.
  - **Password hashing:** `PasswordHasher` uses PBKDF2-HMAC-SHA256 with 310,000 iterations and a random 16-byte salt per user, and compares hashes in constant time.
- **Old data keeps working.** New columns were added at the end of each file and are optional, so data folders from older versions still load. Plain-text passwords from before hashing are converted on the first start.
- **Weekly bookings are all or nothing.** Every week is checked before anything is saved, and the weeks share a series ID (`RS0001`) so they can be approved or cancelled together.
- **Uploads are checked by content, not just by name.** The first bytes must match the file type. Files are stored as `data/attachments/<reportId>.<ext>`, and folder names in stored paths are ignored.
- **The activity log is append-only.** New entries are added to the end of the file and never rewritten.

## Getting started

You need JDK 17 or newer. There are no other dependencies.

```bash
git clone https://github.com/zijing0907/smart-campus-facility-booking.git
cd smart-campus-facility-booking
javac -d out --source-path src src/utar/Main.java src/utar/GuiMain.java
```

Then start either version:

```bash
java -cp out utar.GuiMain    # desktop app
java -cp out utar.Main       # console app
```

The same commands work in Windows PowerShell. For the console version, use a terminal that shows ANSI colours, such as Windows Terminal, the macOS Terminal or the IntelliJ run window.

In IntelliJ IDEA, open the folder, mark `src` as the Sources Root, and run `utar.GuiMain` or `utar.Main`.

On first launch the program creates a `data/` folder with the sample records from [`sample-data/`](sample-data). This takes a couple of seconds while the sample passwords are hashed. Every change is saved straight away. Both versions use the same `data/` folder; delete it to reset everything.

### Demo accounts

| Role | Email | Password |
|---|---|---|
| Admin | `admin@utar.edu.my` | `admin123` |
| Student | `student@utar.edu.my` | `pass123` |
| Staff | `staff@utar.edu.my` | `pass123` |
| Maintenance | `maintenance@utar.edu.my` | `pass123` |

The sample data has 26 users (16 students, 6 staff, 3 maintenance personnel, 1 admin) and 102 facilities modelled on the Sungai Long campus, plus a few bookings and maintenance reports. Every seeded account uses `pass123` except the admin.

## Data files

Each kind of record has its own pipe-delimited file in `data/`, with one record per line and a `#` header line:

```text
# bookingId|userId|facilityId|date|start|end|purpose|desc|status|remark|reminderSent|attendees|seriesId
BK0102|STU001|F011|07/12/2026|14:00|16:00|ACADEMIC|Weekly FYP meeting|PENDING|NULL|false|6|RS0001
```

| File | Contents |
|---|---|
| `users.txt` | Accounts, with a password hash (never the password), failed-login count and lock time. The `extra` column is the programme (student), department (staff) or specialisation (maintenance). |
| `facilities.txt` | Facilities with type, capacity, location, status and number of reported issues |
| `bookings.txt` | Bookings with time slot, purpose, status, admin remark, number of people and weekly series ID |
| `reports.txt` | Maintenance reports with category, status, assignee, dates, notes and attachment |
| `notifications.txt` | Messages waiting for each user, and whether they have been read |
| `activity-log.txt` | Who did what and when (only ever added to) |
| `attachments/` | Photos and PDFs sent with maintenance reports |

`NULL` marks an empty value. A `|` or `\` typed by a user is stored as `\|` or `\\`, so free text can't break the columns.

## Project structure

```text
src/utar/
├── Main.java          console app: login loop and role routing
├── GuiMain.java       desktop app
├── model/             User (abstract), CampusUser, Admin, MaintenancePersonnel, Facility,
│                      Booking, MaintenanceReport, Reportable, Notification, LogEntry
├── service/           DataStore, UserService, BookingService, MaintenanceService,
│                      FacilityService, AnalyticsService, ReportPdfService,
│                      NotificationService, ActivityLogService
├── ui/                console menus: AuthMenu, UserMenu, AdminMenu, MaintenanceMenu
├── gui/               Swing windows: LoginFrame, MainFrame, one panel per tab,
│                      BarChartPanel, TimetableGrid
└── util/              ConsoleUI, InputValidator, PasswordHasher, PdfDocument
sample-data/           the records a new data/ folder starts with
docs/                  screenshots and a sample PDF report
```

## Bugs fixed after submission

- **Records lost when text contained `|`.** The save code escaped `|` as `\|`, but the load code still split on it. A booking note like "Room A|B" was saved, then dropped without warning on the next start.
- **Hard-coded year.** Dates were only accepted for 2026, so searching and booking would have stopped working on 1 January 2027. Impossible dates such as 31/02 were also accepted.
- **Crash on end of input.** Pressing Ctrl+D (or Ctrl+Z on Windows) at a prompt crashed the program with a stack trace. It now exits cleanly.
- **Maintenance reports lost on restart.** Reports that hadn't been assigned yet were skipped when the data was loaded, and In Progress or Resolved reports went back to Assigned. The next save made the loss permanent, and a new report could reuse a lost report's ID.
- **Reminders used up for other users.** The login reminder showed every user's upcoming bookings, not just your own, and marked them all as sent.
- **Bookings in the past.** Any date in the current year was accepted, including days that had already gone.
- **Crash when changing a booking.** Typing a time such as `10am` in the console's Modify Booking crashed the program, because the new times were never checked.
- **Reminders for bookings that had already happened.** The admin's Send Booking Reminders screen said it listed upcoming bookings, but it counted every approved booking, so users could be reminded about bookings from months ago.
