package me.judge.mcnativebuilder.GUI;

import javax.swing.*;
import java.awt.*;

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

        fieldPanel.add(new JLabel("Username"));
        fieldPanel.add(new JTextField(20));

        fieldPanel.add(Box.createVerticalStrut(10));
        fieldPanel.add(new JLabel("Token"));
        fieldPanel.add(new JTextField(20));

        fieldPanel.add(Box.createVerticalStrut(10));
        fieldPanel.add(new JLabel("UUID"));
        fieldPanel.add(new JTextField(20));

        fieldPanel.add(Box.createVerticalStrut(20));
        String[] versions = {"1.18.2", "1.19.0", "1.19.1", "1.19.2", "1.19.3", "1.19.4", "1.20.0", "1.20.1", "1.20.2"};
        JComboBox<String> versionList = new JComboBox<>(versions);
        fieldPanel.add(versionList);

        fieldPanel.add(Box.createVerticalStrut(20));
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(new JButton("Start build!"));
        buttonPanel.add(new JButton("Support Page"));
        fieldPanel.add(buttonPanel);

        frame.setVisible(true);
    }
}
