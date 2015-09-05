package io.nucleo.net.chat;

import io.nucleo.net.Network;
import io.nucleo.net.Repo;
import io.nucleo.net.ServerHandler;

import java.io.IOException;

import java.net.URL;

import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private Network network;
    private String peerAddress;

    @FXML private Label statusLabel;
    @FXML private TextArea textArea;
    @FXML private TextField inputTextField, addressTextField;
    @FXML private Button sendButton;

    private final BooleanProperty peerAddressSet = new SimpleBooleanProperty();
    private final BooleanProperty netWorkReady = new SimpleBooleanProperty();
    private final StringProperty status = new SimpleStringProperty();
    private boolean shuttingDown;
    private ServerHandler serverHandler;

    public ChatController() {
    }

    @Override
    public void initialize(URL fxmlFileLocation, ResourceBundle resources) {
        inputTextField.disableProperty().bind(netWorkReady.not());
        sendButton.disableProperty().bind(netWorkReady.and(peerAddressSet).not());
        statusLabel.textProperty().bind(status);
        addressTextField.setText("Not known yet...");
        sendButton.sceneProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue == null && newValue != null) {
                // exit handlers
                newValue.setOnKeyReleased(keyEvent -> {
                    if (new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN).match(keyEvent) ||
                            new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN).match(keyEvent))
                        shutDown();
                });
            }
        });

        Thread shutDownHookThread = new Thread(this::shutDown, "ChatController.ShutDownHook");
        Runtime.getRuntime().addShutdownHook(shutDownHookThread);
    }

    @FXML
    public void send() {
        String text = inputTextField.getText();
        if (text.length() > 0 && peerAddress.length() > 0)
            network.send(text, peerAddress, serializable -> Platform.runLater(() -> {
                if (serializable instanceof String)
                    textArea.appendText(serializable + "\n");
            }));
    }

    public void init(Stage stage, String id, int hiddenServicePort, Repo repo) throws IOException {
        stage.setOnCloseRequest(e -> shutDown());
        serverHandler = new ServerHandler(serializable -> {
            Platform.runLater(() -> {
                if (serializable instanceof String)
                    textArea.appendText(serializable + "\n");
            });
            return null;
        });

        network = new Network(id, hiddenServicePort, repo, serverHandler);
        network.statusProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> status.set(newValue));
        });
        network.hiddenServiceDescriptorProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> addressTextField.setText(newValue.getOnionUrl() + ":" +
                    newValue.getServicePort()));
        });
        network.netWorkReadyProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> netWorkReady.set(true));
        });
    }

    public void connectToPeer(String peerAddress) {
        if (peerAddress != null) {
            this.peerAddress = peerAddress;
            peerAddressSet.set(!peerAddress.equals(network.hiddenServiceDescriptorProperty().get().getFullAddress()));
            network.connectToPeer(peerAddress);
        }
    }


    private void shutDown() {
        if (!shuttingDown) {
            Platform.runLater(() -> {
                shuttingDown = true;
                status.set("Status: Shutting down");
            });
            new Thread(() -> {
                network.shutDown();
            }).start();
        }

    }
}
