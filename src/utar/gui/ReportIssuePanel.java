package utar.gui;

import utar.model.*;
import utar.service.DataStore;
import utar.service.MaintenanceService;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

/**
 * ReportIssuePanel.java - Student / Staff tab: report a broken facility.
 *
 * GUI version of UserMenu option 6.  MaintenanceService.reportIssue()
 * saves the report with status REPORTED and adds one to the facility's
 * issue count.  The report date is filled in automatically with today's
 * date, so the user doesn't have to type it as in the console.
 *
 * The user can also attach one photo or PDF.  The file is checked as soon
 * as it's chosen (MaintenanceService.checkAttachment), a photo shows a
 * small preview, and on submit the service copies it into data/attachments.
 */
class ReportIssuePanel extends JPanel {

    private static final int PREVIEW_SIZE = 140;   // largest side of the photo preview, in pixels

    private final User user;
    private final MaintenanceService maintService = new MaintenanceService();

    // Facility drop-down: the text shown, and the facility at the same index
    private final ArrayList<Facility> facilities = DataStore.getInstance().getAllFacilities();
    private final JComboBox<String> facilityBox = new JComboBox<>();
    private final JComboBox<String> categoryBox =
            new JComboBox<>(GuiUtil.prettyNames(MaintenanceReport.IssueCategory.values()));
    private final JTextArea descriptionArea = new JTextArea(5, 40);

    // Attachment (optional)
    private File attachment;                       // null = nothing attached
    private final JLabel attachmentLabel = new JLabel();
    private final JButton removeButton = new JButton("Remove");
    private final JLabel previewLabel = new JLabel();

    ReportIssuePanel(User user) {
        super(new BorderLayout());
        this.user = user;
        GuiUtil.padded(this, 10);

        for (Facility f : facilities) {
            facilityBox.addItem(f.getFacilityId() + "  -  " + f.getName() + "  (" + f.getLocation() + ")");
        }
        facilityBox.setMaximumRowCount(15);

        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        JButton chooseButton = new JButton("Choose file...");
        chooseButton.addActionListener(e -> chooseAttachment());
        removeButton.addActionListener(e -> setAttachment(null));
        JLabel limits = new JLabel("Photo (JPG, PNG, GIF, BMP) or PDF, up to 5 MB");
        limits.setForeground(Color.GRAY);

        JButton submitButton = new JButton("Submit report");
        submitButton.addActionListener(e -> submit());

        JPanel form = GuiUtil.formPanel();
        GuiUtil.addFormRow(form, "Facility", facilityBox);
        GuiUtil.addFormRow(form, "Category", categoryBox);
        GuiUtil.addFormRow(form, "What's wrong?", new JScrollPane(descriptionArea));
        // three rows, so the label lines up with the Choose file button
        GuiUtil.addFormRow(form, "Photo or file (optional)",
                GuiUtil.row(chooseButton, attachmentLabel, removeButton));
        GuiUtil.addFormRow(form, "", GuiUtil.row(limits));
        GuiUtil.addFormRow(form, "", GuiUtil.row(previewLabel));
        GuiUtil.addFormRow(form, "", GuiUtil.row(submitButton));

        JLabel note = new JLabel("The admin office will assign your report to maintenance staff.");
        note.setForeground(Color.GRAY);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(GuiUtil.heading("Report a facility issue"), BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        content.add(note, BorderLayout.SOUTH);

        // NORTH keeps the form at its natural height instead of stretching it
        JPanel holder = new JPanel(new BorderLayout());
        holder.add(content, BorderLayout.WEST);
        add(holder, BorderLayout.NORTH);

        setAttachment(null);
    }

    /** Let the user pick a photo or PDF, and check it straight away. */
    private void chooseAttachment() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Attach a photo or PDF");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Photos and PDF files", "jpg", "jpeg", "png", "gif", "bmp", "pdf"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            MaintenanceService.checkAttachment(file);   // type, size and contents
            setAttachment(file);
        } catch (IllegalArgumentException ex) {
            GuiUtil.showError(this, ex.getMessage());
        }
    }

    /** Show the chosen file (or none), with a small preview for photos. */
    private void setAttachment(File file) {
        attachment = file;
        removeButton.setVisible(file != null);
        previewLabel.setIcon(null);

        if (file == null) {
            attachmentLabel.setText("No file chosen");
            attachmentLabel.setForeground(Color.GRAY);
        } else {
            attachmentLabel.setText(file.getName() + "  (" + Math.max(1, file.length() / 1024) + " KB)");
            attachmentLabel.setForeground(getForeground());
            if (MaintenanceService.isImage(file.getName())) {
                previewLabel.setIcon(GuiUtil.scaledImage(file, PREVIEW_SIZE, PREVIEW_SIZE));
            }
        }
        revalidate();
        repaint();
    }

    private void submit() {
        String description = descriptionArea.getText().trim();
        if (description.isEmpty()) {
            GuiUtil.showError(this, "Please describe the issue.");
            descriptionArea.requestFocusInWindow();
            return;
        }
        Facility facility = facilities.get(facilityBox.getSelectedIndex());
        MaintenanceReport.IssueCategory category =
                MaintenanceReport.IssueCategory.values()[categoryBox.getSelectedIndex()];

        try {
            MaintenanceReport r = maintService.reportIssue(facility.getFacilityId(),
                    user.getUserId(), description, category, GuiUtil.today(), attachment);
            GuiUtil.showInfo(this, "Report " + r.getReportId() + " submitted for "
                    + facility.getName() + (r.hasAttachment() ? ", with your attachment" : "")
                    + ". Thank you!");
            descriptionArea.setText("");
            setAttachment(null);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // e.g. the file was deleted after choosing it, or the disk is full
            GuiUtil.showError(this, ex.getMessage());
        }
    }
}
