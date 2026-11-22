package utar.gui;

import utar.model.User;
import utar.service.DataStore;
import utar.service.UserService;
import utar.util.InputValidator;

import javax.swing.*;
import java.awt.*;

/**
 * RegisterDialog.java - Sign-up form for new Student and Staff accounts.
 *
 * GUI version of AuthMenu.doRegister().  It uses the same InputValidator
 * checks and the same faculty / programme lists from DataStore, then calls
 * UserService.register(), which rejects an email that's already taken.
 *
 * Students pick a programme from their faculty's list; staff type a
 * department.  A CardLayout swaps between the two fields when the role
 * changes.
 */
class RegisterDialog extends JDialog {

    private static final String PROGRAMME_CARD  = "programme";
    private static final String DEPARTMENT_CARD = "department";

    private final UserService userService = new UserService();

    private final JComboBox<String> roleBox      = new JComboBox<>(new String[] {"Student", "Staff"});
    private final JTextField        nameField    = new JTextField(26);
    private final JTextField        emailField   = new JTextField(26);
    private final JPasswordField    passField    = new JPasswordField(26);
    private final JPasswordField    confirmField = new JPasswordField(26);
    private final JTextField        phoneField   = new JTextField(26);
    private final JComboBox<String> facultyBox   = new JComboBox<>(DataStore.FACULTIES);
    private final JComboBox<String> programmeBox = new JComboBox<>();
    private final JTextField        departmentField = new JTextField(26);

    private final JLabel     extraLabel = new JLabel("Programme");
    private final CardLayout extraCards = new CardLayout();
    private final JPanel     extraPanel = new JPanel(extraCards);
    private final JLabel     messageLabel = GuiUtil.errorLabel();

    private String registeredEmail;   // set only when registration succeeds

    RegisterDialog(Frame owner) {
        super(owner, "Register as New User", true);   // true = modal

        extraPanel.add(programmeBox, PROGRAMME_CARD);
        extraPanel.add(departmentField, DEPARTMENT_CARD);

        facultyBox.addActionListener(e -> fillProgrammes());
        roleBox.addActionListener(e -> showRoleField());
        fillProgrammes();

        JPanel form = GuiUtil.formPanel();
        GuiUtil.addFormRow(form, "Register as", roleBox);
        GuiUtil.addFormRow(form, "Full name", nameField);
        GuiUtil.addFormRow(form, "UTAR email", emailField);
        GuiUtil.addFormRow(form, "Password (min 6)", passField);
        GuiUtil.addFormRow(form, "Confirm password", confirmField);
        GuiUtil.addFormRow(form, "Contact number", phoneField);
        GuiUtil.addFormRow(form, "Faculty", facultyBox);
        GuiUtil.addFormRow(form, extraLabel, extraPanel);

        JButton registerButton = new JButton("Register");
        JButton cancelButton = new JButton("Cancel");
        registerButton.addActionListener(e -> doRegister());
        cancelButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(registerButton);

        JPanel body = new JPanel(new BorderLayout());
        body.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        body.add(form, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(GuiUtil.row(messageLabel), BorderLayout.NORTH);
        bottom.add(GuiUtil.row(registerButton, cancelButton), BorderLayout.SOUTH);
        body.add(bottom, BorderLayout.SOUTH);

        add(body);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** The email of the new account, or null if the user cancelled. */
    String getRegisteredEmail() {
        return registeredEmail;
    }

    /** Refill the programme list for the chosen faculty. */
    private void fillProgrammes() {
        programmeBox.removeAllItems();
        for (String p : DataStore.PROGRAMMES_BY_FACULTY[facultyBox.getSelectedIndex()]) {
            programmeBox.addItem(p);
        }
    }

    /** Students choose a programme, staff type a department. */
    private void showRoleField() {
        boolean staff = roleBox.getSelectedIndex() == 1;
        extraLabel.setText(staff ? "Department" : "Programme");
        extraCards.show(extraPanel, staff ? DEPARTMENT_CARD : PROGRAMME_CARD);
    }

    /** Validate every field (same rules as the console), then register. */
    private void doRegister() {
        boolean staff   = roleBox.getSelectedIndex() == 1;
        String name     = nameField.getText().trim();
        String email    = emailField.getText().trim();
        String password = new String(passField.getPassword()).trim();
        String confirm  = new String(confirmField.getPassword()).trim();
        String phone    = phoneField.getText().trim();
        String faculty  = (String) facultyBox.getSelectedItem();
        String extra    = staff ? departmentField.getText().trim()
                                : (String) programmeBox.getSelectedItem();

        String error = null;
        if (name.isEmpty())                               error = "Name cannot be empty.";
        else if (!InputValidator.isValidName(name))       error = "Name must contain letters and spaces only.";
        else if (!InputValidator.isValidEmail(email))     error = "Invalid email format.";
        else if (!InputValidator.isUtarEmail(email))      error = "Only UTAR emails are allowed (@utar.edu.my).";
        else if (!InputValidator.isValidPassword(password)) error = "Password must be at least 6 characters.";
        else if (!password.equals(confirm))               error = "Passwords do not match.";
        else if (!InputValidator.isValidPhone(phone))     error = "Invalid phone number (8-15 digits, e.g. 012-3456789).";
        else if (staff && extra.isEmpty())                error = "Department cannot be empty.";

        if (error != null) {
            messageLabel.setText(error);
            return;
        }

        try {
            User u = userService.register(name, email, password, phone, faculty,
                    staff ? User.UserRole.STAFF : User.UserRole.STUDENT, extra);
            GuiUtil.showInfo(this, "Registration successful!\nYour user ID is "
                    + u.getUserId() + ". You can now log in.");
            registeredEmail = u.getEmail();
            dispose();
        } catch (IllegalArgumentException ex) {
            // e.g. "Email already registered."
            messageLabel.setText(ex.getMessage());
        }
    }
}
