package app.view;

import app.controller.GameController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Locale;

public class NewGameSettingsMenu extends JFrame {
    private static final int SETTINGS_MENU_WIDTH = 300;
    private static final int SETTINGS_MENU_HEIGHT = 300;
    private static final String SETTINGS_MENU_TITLE = "NEW GAME SETTINGS";

    private static final int LAYOUT_WEST_PAD = 25;
    private static final int LAYOUT_NORTH_PAD = 10;
    private static final int TEXT_FIELDS_WEST_PAD = 220;
    private static final int DEFAULT_COMPONENTS_WIDTH = 50;
    private static final int DEFAULT_COMPONENTS_HEIGHT = 20;

    private static final int START_BUTTON_WIDTH = 200;
    private static final int START_BUTTON_HEIGHT = 40;
    private JButton startButton;

    private static final Font DEFAULT_LABELS_FONT = new Font("Times New Roman", Font.BOLD, 15);

    private final HashMap<String, Number> gameProtoDefaultValues = new HashMap<>() {{
        put("defaultWidth", 40);
        put("defaultHeight", 30);
        put("defaultFoodStatic", 3);
        put("defaultFoodPerPlayer", 1);
        put("defaultStateDelay", 1000);
        put("defaultFoodProb", 0.1f);
        put("defaultPingDelay", 100);
        put("defaultNodeTimeout", 800);

        put("minWidth", 10);
        put("minHeight", 10);
        put("minFoodStatic", 0);
        put("minFoodPerPlayer", 0);
        put("minStateDelay", 1);
        put("minFoodProb", 0f);
        put("minPingDelay", 1);
        put("minNodeTimeout", 1);

        put("maxWidth", 100);
        put("maxHeight", 100);
        put("maxFoodStatic", 100);
        put("maxFoodPerPlayer", 100);
        put("maxStateDelay", 10000);
        put("maxFoodProb", 1f);
        put("maxPingDelay", 10000);
        put("maxNodeTimeout", 10000);
    }};

    private final HashMap<String, JTextField> proposedGameDefaultValues = new HashMap<>() {{
        put("width", new JTextField(Integer.toString((Integer) gameProtoDefaultValues.get("defaultWidth"))));
        put("height", new JTextField(Integer.toString((Integer) gameProtoDefaultValues.get("defaultHeight"))));
        put("foodStatic", new JTextField(Integer.toString((Integer) gameProtoDefaultValues.get("defaultFoodStatic"))));
        put("foodPerPlayer", new JTextField(Integer.toString((Integer) gameProtoDefaultValues.get("defaultFoodPerPlayer"))));
        put("stateDelay", new JTextField(Integer.toString((Integer) gameProtoDefaultValues.get("defaultStateDelay"))));
        put("foodProb", new JTextField(Float.toString((Float) gameProtoDefaultValues.get("defaultFoodProb"))));
        put("pingDelay", new JTextField(Integer.toString((Integer) gameProtoDefaultValues.get("defaultPingDelay"))));
        put("nodeTimeout", new JTextField(Integer.toString((Integer) gameProtoDefaultValues.get("defaultNodeTimeout"))));
    }};

    private final HashMap<String, JLabel> gameFieldsLabels = new HashMap<>() {{
        put("width", new JLabel("Width (in cells)"));
        put("height", new JLabel("Height (in cells)"));
        put("foodStatic", new JLabel("Food static"));
        put("foodPerPlayer", new JLabel("Food per player"));
        put("stateDelay", new JLabel("State delay (in ms)"));
        put("foodProb", new JLabel("Food probability"));
        put("pingDelay", new JLabel("Ping delay (in ms)"));
        put("nodeTimeout", new JLabel("Node timeout (in ms)"));
    }};

    private SpringLayout springLayout;
    private Container contentPane;

    private final GameController gameController;

    public NewGameSettingsMenu(GameController gameController) {
        this.gameController = gameController;
        this.setTitle(SETTINGS_MENU_TITLE);
        this.setPreferredSize(new Dimension(SETTINGS_MENU_WIDTH, SETTINGS_MENU_HEIGHT));
        this.initSettingsMenu();
        this.showSettingMenu();
    }

    private void initSettingsMenu() {
        contentPane = this.getContentPane();
        springLayout = new SpringLayout();
        contentPane.setLayout(springLayout);

        addAllComponentsToMenu();
        setTextStylesForAllParts();

        configureWidthPart();
        configureHeightPart();
        configureFoodStaticPart();
        configureFoodProbPart();
        configureStateDelayPart();
        configurePingDelayPart();
        configureFoodPerPlayerPart();
        configureNodeTimeoutPart();
        initStartButton();
    }

    private void addAllComponentsToMenu() {
        for (var label : gameFieldsLabels.entrySet()) {
            contentPane.add(label.getValue());
        }

        for (var textField : proposedGameDefaultValues.entrySet()) {
            contentPane.add(textField.getValue());
        }
    }

    private void setTextStylesForAllParts() {
        for (var label : gameFieldsLabels.entrySet()) {
            JLabel correspondingLabel = label.getValue();
            correspondingLabel.setPreferredSize(
                    new Dimension(DEFAULT_COMPONENTS_WIDTH * 4, DEFAULT_COMPONENTS_HEIGHT));
            correspondingLabel.setFont(DEFAULT_LABELS_FONT);
        }

        for (var textField : proposedGameDefaultValues.entrySet()) {
            JTextField correspondingTextField = textField.getValue();
            correspondingTextField.setPreferredSize(
                    new Dimension(DEFAULT_COMPONENTS_WIDTH, DEFAULT_COMPONENTS_HEIGHT));
            correspondingTextField.setFont(DEFAULT_LABELS_FONT);
        }
    }

    private void configureWidthPart() {
        springLayout.putConstraint(SpringLayout.WEST, gameFieldsLabels.get("width"),
                LAYOUT_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, gameFieldsLabels.get("width"),
                LAYOUT_NORTH_PAD, SpringLayout.NORTH, contentPane);

        springLayout.putConstraint(SpringLayout.WEST, proposedGameDefaultValues.get("width"),
                TEXT_FIELDS_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, proposedGameDefaultValues.get("width"),
                LAYOUT_NORTH_PAD, SpringLayout.NORTH, contentPane);
    }

    private void configureHeightPart() {
        springLayout.putConstraint(SpringLayout.WEST, gameFieldsLabels.get("height"),
                LAYOUT_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, gameFieldsLabels.get("height"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, gameFieldsLabels.get("width"));

        springLayout.putConstraint(SpringLayout.WEST, proposedGameDefaultValues.get("height"),
                TEXT_FIELDS_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, proposedGameDefaultValues.get("height"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, proposedGameDefaultValues.get("width"));
    }

    private void configureFoodStaticPart() {
        springLayout.putConstraint(SpringLayout.WEST, gameFieldsLabels.get("foodStatic"),
                LAYOUT_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, gameFieldsLabels.get("foodStatic"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, gameFieldsLabels.get("height"));

        springLayout.putConstraint(SpringLayout.WEST, proposedGameDefaultValues.get("foodStatic"),
                TEXT_FIELDS_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, proposedGameDefaultValues.get("foodStatic"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, proposedGameDefaultValues.get("height"));
    }

    private void configureFoodProbPart() {
        springLayout.putConstraint(SpringLayout.WEST, gameFieldsLabels.get("foodProb"),
                LAYOUT_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, gameFieldsLabels.get("foodProb"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, gameFieldsLabels.get("foodStatic"));

        springLayout.putConstraint(SpringLayout.WEST, proposedGameDefaultValues.get("foodProb"),
                TEXT_FIELDS_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, proposedGameDefaultValues.get("foodProb"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, proposedGameDefaultValues.get("foodStatic"));
    }

    private void configureStateDelayPart() {
        springLayout.putConstraint(SpringLayout.WEST, gameFieldsLabels.get("stateDelay"),
                LAYOUT_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, gameFieldsLabels.get("stateDelay"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, gameFieldsLabels.get("foodProb"));

        springLayout.putConstraint(SpringLayout.WEST, proposedGameDefaultValues.get("stateDelay"),
                TEXT_FIELDS_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, proposedGameDefaultValues.get("stateDelay"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, proposedGameDefaultValues.get("foodProb"));
    }

    private void configurePingDelayPart() {
        springLayout.putConstraint(SpringLayout.WEST, gameFieldsLabels.get("pingDelay"),
                LAYOUT_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, gameFieldsLabels.get("pingDelay"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, gameFieldsLabels.get("stateDelay"));

        springLayout.putConstraint(SpringLayout.WEST, proposedGameDefaultValues.get("pingDelay"),
                TEXT_FIELDS_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, proposedGameDefaultValues.get("pingDelay"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, proposedGameDefaultValues.get("stateDelay"));
    }

    private void configureFoodPerPlayerPart() {
        springLayout.putConstraint(SpringLayout.WEST, gameFieldsLabels.get("foodPerPlayer"),
                LAYOUT_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, gameFieldsLabels.get("foodPerPlayer"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, gameFieldsLabels.get("pingDelay"));

        springLayout.putConstraint(SpringLayout.WEST, proposedGameDefaultValues.get("foodPerPlayer"),
                TEXT_FIELDS_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, proposedGameDefaultValues.get("foodPerPlayer"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, proposedGameDefaultValues.get("pingDelay"));
    }

    private void configureNodeTimeoutPart() {
        springLayout.putConstraint(SpringLayout.WEST, gameFieldsLabels.get("nodeTimeout"),
                LAYOUT_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, gameFieldsLabels.get("nodeTimeout"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, gameFieldsLabels.get("foodPerPlayer"));

        springLayout.putConstraint(SpringLayout.WEST, proposedGameDefaultValues.get("nodeTimeout"),
                TEXT_FIELDS_WEST_PAD, SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, proposedGameDefaultValues.get("nodeTimeout"),
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, proposedGameDefaultValues.get("foodPerPlayer"));
    }

    private void initStartButton() {
        startButton = new JButton("Start game!");
        startButton.setPreferredSize(new Dimension(START_BUTTON_WIDTH, START_BUTTON_HEIGHT));

        contentPane.add(startButton);
        springLayout.putConstraint(SpringLayout.WEST, startButton,
                (int) (LAYOUT_WEST_PAD * 1.7), SpringLayout.WEST, contentPane);
        springLayout.putConstraint(SpringLayout.NORTH, startButton,
                LAYOUT_NORTH_PAD, SpringLayout.SOUTH, gameFieldsLabels.get("nodeTimeout"));

        addUserInputListenerToButton();
    }

    private void addUserInputListenerToButton() {
        startButton.addActionListener(event -> {
            int inputWidth;
            int inputHeight;
            int inputFoodStatic;
            int inputFoodPerPlayer;
            int inputStateDelay;
            float inputFoodProb;
            int inputPingDelay;
            int inputNodeTimeout;
            String currentVariableName = "OK";
            try {
                currentVariableName = "Width";
                inputWidth = Integer.parseInt(proposedGameDefaultValues.get("width").getText());
                currentVariableName = "Height";
                inputHeight = Integer.parseInt(proposedGameDefaultValues.get("height").getText());
                currentVariableName = "FoodStatic";
                inputFoodStatic = Integer.parseInt(proposedGameDefaultValues.get("foodStatic").getText());
                currentVariableName = "FoodPerPlayer";
                inputFoodPerPlayer = Integer.parseInt(proposedGameDefaultValues.get("foodPerPlayer").getText());
                currentVariableName = "StateDelay";
                inputStateDelay = Integer.parseInt(proposedGameDefaultValues.get("stateDelay").getText());
                currentVariableName = "FoodProb";
                inputFoodProb = Float.parseFloat(proposedGameDefaultValues.get("foodProb").getText());
                currentVariableName = "PingDelay";
                inputPingDelay = Integer.parseInt(proposedGameDefaultValues.get("pingDelay").getText());
                currentVariableName = "NodeTimeout";
                inputNodeTimeout = Integer.parseInt(proposedGameDefaultValues.get("nodeTimeout").getText());
            } catch (NumberFormatException exception) {
                JOptionPane.showConfirmDialog(this,
                        currentVariableName + " should be between " +
                                gameProtoDefaultValues.get("min" + currentVariableName) + " and " +
                                gameProtoDefaultValues.get("max" + currentVariableName),
                        "WARNING", JOptionPane.DEFAULT_OPTION);
                Number defaultValue = gameProtoDefaultValues.get("default" + currentVariableName);
                if (defaultValue instanceof Integer) {
                    proposedGameDefaultValues.get(getStringWithFirstLowerCase(currentVariableName))
                            .setText(Integer.toString((Integer) defaultValue));
                } else {
                    proposedGameDefaultValues.get(getStringWithFirstLowerCase(currentVariableName))
                            .setText(Float.toString((Float) defaultValue));
                }
                return;
            }
            gameController.launchNewGame(inputWidth, inputHeight,
                    inputFoodStatic, inputFoodPerPlayer,
                    inputStateDelay, inputFoodProb,
                    inputPingDelay, inputNodeTimeout);
            this.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        });
    }

    private void showSettingMenu() {
        this.setLocationRelativeTo(null);
        this.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        this.setResizable(false);
        this.setAutoRequestFocus(true);
        this.pack();
        this.setVisible(true);
    }

    private static String getStringWithFirstLowerCase(String word) {
        return word.substring(0, 1).toLowerCase(Locale.ROOT) + word.substring(1);
    }
}
