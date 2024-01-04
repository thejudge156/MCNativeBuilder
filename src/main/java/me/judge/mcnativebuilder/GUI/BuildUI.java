package me.judge.mcnativebuilder.GUI;

import me.judge.mcnativebuilder.Main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;

public class BuildUI {
    public static void main(String[] args) {
        JFrame frame = new JFrame("MCNative Builder");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(350, 300);
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        frame.add(mainPanel);

        JPanel fieldPanel = new JPanel();
        fieldPanel.setLayout(new BoxLayout(fieldPanel, BoxLayout.Y_AXIS));
        gbc.gridx = 0;
        gbc.gridy = 0;
        mainPanel.add(fieldPanel, gbc);

        JTextField token = new JTextField(20);
        fieldPanel.add(new JLabel("Token"));
        fieldPanel.add(token);

        JTextField uuid = new JTextField(20);
        fieldPanel.add(new JLabel("UUID"));
        fieldPanel.add(uuid);

        JTextField graalVM = new JTextField(20);
        fieldPanel.add(new JLabel("GraalVM SDK"));
        fieldPanel.add(graalVM);

        String[] versions = {"1.18.2", "1.19.0", "1.19.1", "1.19.2", "1.19.3", "1.19.4", "1.20.0", "1.20.1", "1.20.2"};
        JComboBox<String> versionList = new JComboBox<>(versions);
        fieldPanel.add(new JLabel("Version"));
        fieldPanel.add(versionList);

        fieldPanel.add(Box.createVerticalStrut(20));
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton buildButton = new JButton("Start build!");
        buildButton.setAction(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    Main.main(new String[] {
                            "--accessToken",
                            token.getText(),
                            "--version",
                            (String) versionList.getSelectedItem(),
                            "--uuid",
                            uuid.getText(),
                            "--graalvm",
                            graalVM.getText()
                    });
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        buttonPanel.add(new JButton("Support Page"));
        fieldPanel.add(buttonPanel);
        fieldPanel.add(buildButton);

        frame.setVisible(true);
    }
}
