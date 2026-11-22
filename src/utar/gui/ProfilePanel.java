package utar.gui;

import utar.model.*;
import utar.service.DataStore;
import utar.service.UserService;
import utar.util.InputValidator;

import javax.swing.*;
import java.awt.*;

/**
 * ProfilePanel.java - Student / Staff tab: my details and my password.
 *
 * GUI version of UserMenu options 8 and 9.
 *   - My details: change name, contact number or faculty through
 *     UserService.updateProfile().  ID, email and role can't be changed.
 *   - Change password: UserService.changePassword() only changes it if
 *     the current password is correct.
 *
 * After a successful profile update the panel runs onProfileChanged, so
 * MainFrame can show the new name in its header.
 */
class ProfilePanel extends JPanel implements Refreshable {

    private final User user;
    private final Runnable onProfileChanged;
    private final UserService userService = new UserService();

    // -- My details --------------------------------------------------------
    private final JLabel idLabel    = new JLabel();
    private final JLabel emailLabel = new JLabel();
    private final JLabel roleLabel  = new JLabel();
    private final JLabel extraTitle = new JLabel();   // "Programme" or "Department"
    private final JLabel extraLabel = new JLabel();
    private final JTextField nameField  = new JTextField(26);
    private final JTextField phoneField = new JTextField(26);
    private final JComboBox<String> facultyBox = new JComboBox<>();
    private final JLabel profileMessage = GuiUtil.errorLabel();

    // -- Change password ---------------------------------------------------
    private final JPasswordField currentField = new JPasswordField(20);
    private final JPasswordField newField     = new JPasswordField(20);
    private final JPasswordField confirmField = new JPasswordField(20);
    private final JLabel passwordMessage = GuiUtil.errorLabel();

    ProfilePanel(User user, Runnable onProfileChanged) {
        super(new BorderLayout());
        this.user = user;
        this.onProfileChanged = onProfileChanged;
        GuiUtil.padded(this, 10);

        for (String faculty : DataStore.FACULTIES) {
            facultyBox.addItem(faculty);
        }

        JButton saveButton = new JButton("Save changes");
        saveButton.addActionListener(e -> saveProfile());
        JButton changeButton = new JButton("Change password");
        changeButton.addActionListener(e -> changePassword());

        JPanel details = GuiUtil.formPanel();
        GuiUtil.addFormRow(details, "User ID", idLabel);
        GuiUtil.addFormRow(details, "Email", emailLabel);
        GuiUtil.addFormRow(details, "Role", roleLabel);
        GuiUtil.addFormRow(details, extraTitle, extraLabel);
        GuiUtil.addFormRow(details, "Full name", nameField);
        GuiUtil.addFormRow(details, "Contact number", phoneField);
        GuiUtil.addFormRow(details, "Faculty", facultyBox);
        GuiUtil.addFormRow(details, "", GuiUtil.row(saveButton));

        JPanel password = GuiUtil.formPanel();
        GuiUtil.addFormRow(password, "Current password", currentField);
        GuiUtil.addFormRow(password, "New password (min 6)", newField);
        GuiUtil.addFormRow(password, "Confirm new password", confirmField);
        GuiUtil.addFormRow(password, "", GuiUtil.row(changeButton));

        JPanel sections = new JPanel(new GridLayout(1, 2, 30, 0));
        sections.add(section("My details", details, profileMessage));
        sections.add(section("Change password", password, passwordMessage));

        // NORTH keeps the forms at their natural height instead of stretching them
        add(sections, BorderLayout.NORTH);
    }

    /**
     * A heading, a form and a message line stacked on top of each other,
     * all pinned to the top so both sections line up side by side.
     */
    private static JPanel section(String title, JPanel form, JLabel message) {
        message.setVerticalAlignment(SwingConstants.TOP);
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.add(form, BorderLayout.NORTH);
        body.add(message, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(GuiUtil.heading(title), BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    /** Fill the form with the saved details (throws away unsaved edits). */
    @Override
    public void refresh() {
        idLabel.setText(user.getUserId());
        emailLabel.setText(user.getEmail());
        roleLabel.setText(user.getRoleLabel());

        // Programme for students, department for staff (polymorphism through CampusUser)
        if (user instanceof CampusUser) {
            extraTitle.setText(user.getRole() == User.UserRole.STAFF ? "Department" : "Programme");
            extraLabel.setText(GuiUtil.orDash(((CampusUser) user).getExtra()));
        }

        nameField.setText(user.getName());
        phoneField.setText(user.getContactNumber());
        selectFaculty(user.getFaculty());
        profileMessage.setText(" ");
    }

    /** Select the user's faculty, adding it first if it isn't in the standard list. */
    private void selectFaculty(String faculty) {
        boolean found = false;
        for (int i = 0; i < facultyBox.getItemCount(); i++) {
            if (facultyBox.getItemAt(i).equals(faculty)) {
                found = true;
            }
        }
        if (!found && faculty != null) {
            facultyBox.addItem(faculty);
        }
        facultyBox.setSelectedItem(faculty);
    }

    /**
     * saveProfile - check the fields, then save them.  Name and phone are
     * only checked when they were changed, so an existing name such as
     * "Dr. Wong" doesn't stop someone from updating just their phone number.
     */
    private void saveProfile() {
        String name    = nameField.getText().trim();
        String phone   = phoneField.getText().trim();
        String faculty = (String) facultyBox.getSelectedItem();

        boolean nameChanged    = !name.equals(user.getName());
        boolean phoneChanged   = !phone.equals(user.getContactNumber());
        boolean facultyChanged = faculty != null && !faculty.equals(user.getFaculty());

        String error = null;
        if (name.isEmpty())                                      error = "Name cannot be empty.";
        else if (nameChanged && !InputValidator.isValidName(name)) error = "Name must contain letters and spaces only.";
        else if (phoneChanged && !InputValidator.isValidPhone(phone)) error = "Invalid phone number (8-15 digits, e.g. 012-3456789).";

        if (error != null) {
            profileMessage.setText(error);
            return;
        }
        if (!nameChanged && !phoneChanged && !facultyChanged) {
            profileMessage.setText(" ");
            GuiUtil.showInfo(this, "No changes made.");
            return;
        }

        userService.updateProfile(user.getUserId(), phone, faculty, name);
        profileMessage.setText(" ");
        onProfileChanged.run();   // e.g. show the new name in the header
        GuiUtil.showInfo(this, "Your details have been updated.");
    }

    /** changePassword - same rules as the console menu. */
    private void changePassword() {
        String current = new String(currentField.getPassword()).trim();
        String next    = new String(newField.getPassword()).trim();
        String confirm = new String(confirmField.getPassword()).trim();

        String error = null;
        if (current.isEmpty())                          error = "Enter your current password.";
        else if (!InputValidator.isValidPassword(next)) error = "New password must be at least 6 characters.";
        else if (next.equals(current))                  error = "New password must be different from the current one.";
        else if (!next.equals(confirm))                 error = "New passwords do not match.";
        // UserService checks the current password and only then saves the new one
        else if (!userService.changePassword(user.getUserId(), current, next))
                                                        error = "Current password is incorrect.";

        if (error != null) {
            passwordMessage.setText(error);
            return;
        }

        currentField.setText("");
        newField.setText("");
        confirmField.setText("");
        passwordMessage.setText(" ");
        GuiUtil.showInfo(this, "Password changed. Use your new password next time you log in.");
    }
}
