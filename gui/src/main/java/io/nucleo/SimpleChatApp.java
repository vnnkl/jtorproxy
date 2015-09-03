package io.nucleo;

import io.nucleo.net.TorProxy;
import io.nucleo.util.Tuple3;

import java.io.File;

import java.nio.file.Paths;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleChatApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(SimpleChatApp.class);

    private static final int torProxy1LocalPort = 7771;
    private static final int torProxy2LocalPort = 7772;
    private static final int torProxy1HiddenServicePort = 8881;
    private static final int torProxy2HiddenServicePort = 8882;

    private final BooleanProperty leftChatInited = new SimpleBooleanProperty();
    private final BooleanProperty rightChatInited = new SimpleBooleanProperty();
    private final StringProperty statusLeft = new SimpleStringProperty();
    private final StringProperty statusRight = new SimpleStringProperty();

    private TorProxy torProxy1;
    private TorProxy torProxy2;
    private boolean shuttingDown;
    private boolean isTorProxy1SetupHiddenServiceCompleted, isTorProxy2SetupHiddenServiceCompleted,
            torProxy1DataSent, torProxy2DataSent,
            torProxy1DataArrived, torProxy2DataArrived;
    private TextArea leftTextArea, rightTextArea;
    private Button startTorButton, stopTorButton;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // top container with controls
        startTorButton = new Button("Start Tor");
        startTorButton.setDisable(false);
        startTorButton.setOnAction(e -> startupTor());
        stopTorButton = new Button("Stop Tor");
        stopTorButton.setDisable(false);
        stopTorButton.setOnAction(e -> shutDown());

        HBox controlsHBox = new HBox();
        controlsHBox.setSpacing(10);
        controlsHBox.getChildren().addAll(startTorButton, stopTorButton);


        // outer chat hBox
        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.setFillHeight(true);

        // left chat elements
        Tuple3<TextArea, TextField, Button> tupleLeft = addChatElements(hBox);
        tupleLeft.third.disableProperty().bind(leftChatInited.not());
        tupleLeft.third.setOnAction(e -> onLeftSideSendData(tupleLeft.second.getText()));
        leftTextArea = tupleLeft.first;
        leftTextArea.setEditable(false);

        // right chat elements
        Tuple3<TextArea, TextField, Button> tupleRight = addChatElements(hBox);
        tupleRight.third.disableProperty().bind(rightChatInited.not());
        tupleRight.third.setOnAction(e -> onRightSideSendData(tupleRight.second.getText()));
        rightTextArea = tupleRight.first;
        rightTextArea.setEditable(false);


        // root container
        AnchorPane root = new AnchorPane();
        AnchorPane.setTopAnchor(controlsHBox, 20d);
        AnchorPane.setLeftAnchor(controlsHBox, 20d);
        AnchorPane.setRightAnchor(controlsHBox, 20d);
        AnchorPane.setTopAnchor(hBox, 60d);
        AnchorPane.setBottomAnchor(hBox, 20d);
        AnchorPane.setLeftAnchor(hBox, 20d);
        AnchorPane.setRightAnchor(hBox, 20d);
        root.getChildren().addAll(controlsHBox, hBox);


        // scene
        Scene scene = new Scene(root, 800, 800);
        primaryStage.setScene(scene);
        primaryStage.show();

        // exit handlers
        scene.setOnKeyReleased(keyEvent -> {
            if (new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN).match(keyEvent) ||
                    new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN).match(keyEvent))
                shutDown();
        });
        primaryStage.setOnCloseRequest(e -> shutDown());
        Thread shutDownHookThread = new Thread(SimpleChatApp.this::shutDown, "SimpleChatApp.ShutDownHook");
        Runtime.getRuntime().addShutdownHook(shutDownHookThread);


        // start up tor
        startupTor();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onLeftSideSendData(String text) {
        torProxy1.sendData(text, torProxy2.getOnionAddress());
    }

    private void onRightSideSendData(String text) {
        torProxy2.sendData(text, torProxy1.getOnionAddress());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Tor
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void startupTor() {
        statusLeft.set("Start up tor");
        statusRight.set("Start up tor");
        shuttingDown = false;
        startTorButton.setDisable(true);
        stopTorButton.setDisable(false);
        String userDataDir = Paths.get(System.getProperty("user.home"), "Library", "Application Support",
                "SimpleChatApp").toString();
        torProxy1 = new TorProxy(torProxy1LocalPort, new File(userDataDir, "torProxy1"));
        torProxy1.addStartupCompletedListener(() -> {
            Platform.runLater(SimpleChatApp.this::torProxy1StartupCompleted);
            return null;
        });
        torProxy1.startTor();

        torProxy2 = new TorProxy(torProxy2LocalPort, new File(userDataDir, "torProxy2"));
        torProxy2.addStartupCompletedListener(() -> {
            Platform.runLater(SimpleChatApp.this::torProxy2StartupCompleted);
            return null;
        });
        torProxy2.startTor();
    }

    private void shutDown() {
        if (!shuttingDown) {
            shuttingDown = true;
            Platform.runLater(() -> {
                statusLeft.set("Shut down");
                statusLeft.set("Shut down");
                torProxy1.shutDown();
                torProxy2.shutDown();
                startTorButton.setDisable(false);
                stopTorButton.setDisable(true);
            });
        }
    }

    private void torProxy1StartupCompleted() {
        log.debug("torProxy1StartupCompleted");
        statusLeft.set("Start up hidden service");
        try {
            torProxy1.setupHiddenService(torProxy1HiddenServicePort, () -> {
                Platform.runLater(this::torProxy1SetupHiddenServiceCompleted);
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void torProxy2StartupCompleted() {
        log.debug("torProxy2StartupCompleted");
        statusRight.set("Start up hidden service");
        try {
            torProxy2.setupHiddenService(torProxy2HiddenServicePort, () -> {
                Platform.runLater(this::torProxy2SetupHiddenServiceCompleted);
                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        torProxy1.sendData("test from torProxy1", torProxy2.getOnionAddress());
    }

    private void torProxy1SetupHiddenServiceCompleted() {
        log.debug("torProxy1SetupHiddenServiceCompleted");
        statusLeft.set("Hidden service completed");
        torProxy1.addReceivingDataListener(data -> Platform.runLater(() -> {
            leftTextArea.appendText(data + "\n");
            log.debug("torProxy1 received Data: " + data);
        }));
        isTorProxy1SetupHiddenServiceCompleted = true;
        checkAllHiddenServicesCompleted();
    }

    private void torProxy2SetupHiddenServiceCompleted() {
        log.debug("torProxy2SetupHiddenServiceCompleted");
        statusRight.set("Hidden service completed");
        torProxy2.addReceivingDataListener(data -> Platform.runLater(() -> {
            rightTextArea.appendText(data + "\n");
            log.debug("torProxy2 received Data: " + data);
        }));
        isTorProxy2SetupHiddenServiceCompleted = true;
        checkAllHiddenServicesCompleted();
    }

    private void checkAllHiddenServicesCompleted() {
        log.debug("checkAllHiddenServicesCompleted");
        if (isTorProxy1SetupHiddenServiceCompleted && isTorProxy2SetupHiddenServiceCompleted) {
            statusLeft.set("Setup client socket to peers hidden service");
            statusRight.set("Setup client socket to peers hidden service");
            try {
                torProxy1.setupClientSocket(torProxy2.getOnionAddress(), torProxy2HiddenServicePort, () -> {
                    Platform.runLater(SimpleChatApp.this::torProxy1SetupClientSocketCompleted);
                    return null;
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                torProxy2.setupClientSocket(torProxy1.getOnionAddress(), torProxy1HiddenServicePort, () -> {
                    Platform.runLater(SimpleChatApp.this::torProxy2SetupClientSocketCompleted);
                    return null;
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void torProxy1SetupClientSocketCompleted() {
        log.debug("torProxy1SetupClientSocketCompleted");
        statusLeft.set("Client socket to peers hidden service completed");
        leftChatInited.set(true);
        torProxy1.sendData("test data from torProxy1 to torProxy2", torProxy2.getOnionAddress());
    }

    private void torProxy2SetupClientSocketCompleted() {
        log.debug("torProxy2SetupClientSocketCompleted");
        statusRight.set("Client socket to peers hidden service completed");
        rightChatInited.set(true);
        torProxy2.sendData("test data from torProxy2 to torProxy1", torProxy1.getOnionAddress());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // GUI
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Tuple3<TextArea, TextField, Button> addChatElements(HBox hBox) {
        Label statusLabelLeft = new Label();
        statusLabelLeft.textProperty().bind(statusLeft);
        HBox hBox1 = new HBox();
        hBox1.setSpacing(5);
        hBox1.getChildren().addAll(new Label("Status: "), statusLabelLeft);

        TextArea textArea = new TextArea();
        TextField textField = new TextField("test entry");
        Button button = new Button("Send");
        button.setDisable(true);

        VBox vBox = new VBox();
        vBox.setSpacing(10);
        hBox.setFillHeight(true);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        vBox.setFillWidth(true);
        vBox.getChildren().addAll(hBox1, textArea, textField, button);

        hBox.getChildren().add(vBox);

        return new Tuple3(textArea, textField, button);
    }
}
