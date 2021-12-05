package app.view;

import app.controller.GameController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;

public class SettingsMenu extends JFrame {
    private JTextField w, h, food, foodPp, delay;
    GameController gameController;

    public SettingsMenu(GameController gameController) {
        this.gameController = gameController;
        this.init();
    }

    private void init() {
        GridBagLayout grid = new GridBagLayout();
        setLayout(grid);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel w_prompt = new JLabel("Ширина");
        add(w_prompt, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 1;
        w = new JTextField("40");
        add(w, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        JLabel h_prompt = new JLabel("Высота");
        add(h_prompt, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 1;
        h = new JTextField("30");
        add(h, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        JLabel food_prompt = new JLabel("Еда");
        add(food_prompt, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 1;
        food = new JTextField("15");
        add(food, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JLabel foodpp_prompt = new JLabel("Еда на игрока");
        add(foodpp_prompt, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 1;
        foodPp = new JTextField("0.2");
        add(foodPp, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        JLabel delay_prompt = new JLabel("State Delay (ms)");
        add(delay_prompt, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 1;
        delay = new JTextField("1000");
        add(delay, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 3;
        JButton start = new JButton("Старт");
        start.addActionListener(e -> {
            int h_, w_, food_, delay_;
            float foodapp_;
            try {
                h_ = Integer.parseInt(h.getText());
            } catch (NumberFormatException ex) {
                h_ = 30;
                h.setText("30");
            }
            try {
                w_ = Integer.parseInt(w.getText());
            } catch (NumberFormatException ex) {
                w_ = 40;
                w.setText("40");
            }
            try {
                food_ = Integer.parseInt(food.getText());
            } catch (NumberFormatException ex) {
                food_ = 1;
                food.setText("1");
            }
            try {
                foodapp_ = Float.parseFloat(foodPp.getText());
            } catch (NumberFormatException ex) {
                foodapp_ = 1.0F;
                foodPp.setText("1.0");
            }
            try {
                delay_ = Integer.parseInt(delay.getText());
            } catch (NumberFormatException ex) {
                delay_ = 500;
                delay.setText("500");
            }
            if (w_ < 1) {
                w_ = 40;
            }
            if (h_ < 1) {
                h_ = 30;
            }
            gameController.newGame(w_, h_, food_, foodapp_, delay_);
            dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        });
        add(start, gbc);

        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setResizable(true);
        setAutoRequestFocus(true);
        pack();
        setVisible(true);
    }
}
