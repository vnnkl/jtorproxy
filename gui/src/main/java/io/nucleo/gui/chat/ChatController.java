package io.nucleo.gui.chat;

import io.nucleo.gui.chat.contacts.ContactsController;
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
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    public HBox root;
    private Network network;
    private String peerAddress;

    @FXML private Label statusLabel;
    @FXML private TextArea textArea;
    @FXML private TextField inputTextField, addressTextField;
    @FXML private Button sendButton;

    private final BooleanProperty isPeerAddressAvailable = new SimpleBooleanProperty();
    private final BooleanProperty netWorkReady = new SimpleBooleanProperty();
    private final StringProperty status = new SimpleStringProperty();
    private boolean shuttingDown;
    private ServerHandler serverHandler;
    private ContactsController contactsController;

    public ChatController() {
    }

    @Override
    public void initialize(URL fxmlFileLocation, ResourceBundle resources) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/io/nucleo/gui/chat/contacts/ContactsView" + "" +
                                                                              ".fxml"));
            Pane contactsView = loader.load();
            contactsController = loader.getController();
            root.getChildren().add(0, contactsView);

            inputTextField.disableProperty().bind(netWorkReady.not());
            sendButton.disableProperty().bind(netWorkReady.and(isPeerAddressAvailable).not());
            statusLabel.textProperty().bind(status);
            addressTextField.setText("Not known yet...");
            sendButton.sceneProperty().addListener((observable, oldValue, newValue) -> {
                if (oldValue == null && newValue != null) {
                    // exit handlers
                    newValue.setOnKeyReleased(keyEvent -> {
                        if (new KeyCodeCombination(KeyCode.W,
                                                   KeyCombination.SHORTCUT_DOWN).match(keyEvent) || new 
                                KeyCodeCombination(
                                KeyCode.Q,
                                KeyCombination.SHORTCUT_DOWN).match(keyEvent)) shutDown();
                    });
                }
            });

            Thread shutDownHookThread = new Thread(this::shutDown, "ChatController.ShutDownHook");
            Runtime.getRuntime().addShutdownHook(shutDownHookThread);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void send() {
        String text = inputTextField.getText();
        if (text.length() > 0 && peerAddress.length() > 0)
            network.send(text, peerAddress, serializable -> Platform.runLater(() -> {
                if (serializable instanceof String) textArea.appendText(serializable + "\n");
            }));
    }

    public void init(Network network, Stage stage, String id, int serverPort, Repo repo) throws IOException {
        this.network = network;
        
        contactsController.init(stage, repo, this::connectToPeer);

        stage.setOnCloseRequest(e -> shutDown());
        
        network.statusProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> status.set(newValue));
        });
        network.addressProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> addressTextField.setText(newValue));
        });
        network.netWorkReadyProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> netWorkReady.set(true));
        });

        serverHandler = new ServerHandler(serializable -> {
            Platform.runLater(() -> {
                if (serializable instanceof String) textArea.appendText(serializable + "\n");
            });
            return null;
        });
        
        network.start(id, serverPort, repo, serverHandler);
    }

    public void connectToPeer(String peerAddress) {
        if (peerAddress != null) {
            this.peerAddress = peerAddress;
            if (network.addressProperty().get() != null)
                isPeerAddressAvailable.set(!peerAddress.equals(network.addressProperty().get()));
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
