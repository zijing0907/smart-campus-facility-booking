package utar.gui;

import utar.model.User;
import utar.service.UserService;

import javax.swing.*;
import java.awt.*;

/**
 * LoginFrame.java - The first window: log in, or open the registration form.
 *
 * GUI version of AuthMenu.  On a successful login it closes itself and
 * opens a MainFrame for that user; MainFrame decides which tabs to show
 * based on the user's role.
 */
public class LoginFrame extends JFrame {

    private final UserService userService = new UserService();

    private final JTextField emailField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JLabel messageLabel = GuiUtil.errorLabel();

    private static boolean lookAndFeelSet = false;

    /** Set up the look and feel (first time only) and show the login window. */
    public static void open() {
        if (!lookAndFeelSet) {
            GuiUtil.useNimbusLookAndFeel();
            lookAndFeelSet = true;
        }
        new LoginFrame().setVisible(true);
    }

    private LoginFrame() {
        super("UTAR Smart Campus - Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // -- Form ----------------------------------------------------------
        JPanel form = GuiUtil.formPanel();
        GuiUtil.addFormRow(form, "Email", emailField);
        GuiUtil.addFormRow(form, "Password", passwordField);

        JButton loginButton = new JButton("Login");
        JButton registerButton = new JButton("Register as new user");
        loginButton.addActionListener(e -> doLogin());
        registerButton.addActionListener(e -> openRegistration());
        getRootPane().setDefaultButton(loginButton);   // ENTER presses Login

        JLabel demoLabel = new JLabel("<html>Demo accounts (password <b>pass123</b>):"
                + " student@utar.edu.my, staff@utar.edu.my, maintenance@utar.edu.my<br>"
                + "Admin: admin@utar.edu.my / <b>admin123</b></html>");
        demoLabel.setForeground(Color.GRAY);

        // -- Layout --------------------------------------------------------
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        for (JComponent c : new JComponent[] {
                form, GuiUtil.row(messageLabel), GuiUtil.row(loginButton, registerButton), demoLabel }) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            body.add(c);
        }

        add(GuiUtil.headerBand("UTAR Smart Campus Facility System",
                "Universiti Tunku Abdul Rahman \u2014 Sungai Long Campus"), BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);

        pack();
        setResizable(false);
        setLocationRelativeTo(null);   // centre on screen
    }

    /**
     * doLogin - same checks as AuthMenu.doLogin(): both fields filled in,
     * then UserService.login().  A null result means wrong details, and we
     * check which part was wrong so the message is more helpful.
     */
    private void doLogin() {
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (email.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Enter your email and password.");
            return;
        }

        User user;
        try {
            user = userService.login(email, password);
        } catch (IllegalStateException locked) {
            // Too many wrong passwords in a row - the message says until when
            // wrap the long message to the form's width; pack() grows the window to fit it
            messageLabel.setText("<html>" + GuiUtil.wrapHtml(locked.getMessage(),
                    messageLabel.getFont(), 520) + "</html>");
            passwordField.setText("");
            pack();
            return;
        }
        if (user == null) {
            if (!userService.emailExists(email)) {
                messageLabel.setText("No account found with this email.");
            } else {
                int left = userService.attemptsLeft(email);
                messageLabel.setText("Incorrect password. " + left + (left == 1 ? " attempt" : " attempts")
                        + " left before the account is locked.");
            }
            passwordField.setText("");
            return;
        }

        dispose();                          // close the login window
        new MainFrame(user).setVisible(true);
    }

    /** Open the registration form and, if it worked, fill in the new email. */
    private void openRegistration() {
        RegisterDialog dialog = new RegisterDialog(this);
        dialog.setVisible(true);   // modal: this line waits until the dialog closes

        if (dialog.getRegisteredEmail() != null) {
            emailField.setText(dialog.getRegisteredEmail());
            passwordField.setText("");
            passwordField.requestFocusInWindow();
            messageLabel.setText(" ");
        }
    }
}
