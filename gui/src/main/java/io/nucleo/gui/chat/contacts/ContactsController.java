package io.nucleo.gui.chat.contacts;

import io.nucleo.storage.StorageClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;
import java.util.function.Consumer;


public class ContactsController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ContactsController.class);

    @FXML
    private ListView listView;

    private final ObservableList<String> addresses = FXCollections.observableArrayList();

    private boolean shuttingDown;
    private StorageClient storageClient;
    private String ownAddress;

    public ContactsController() {
    }

    @Override
    public void initialize(URL fxmlFileLocation, ResourceBundle resources) {
        Thread shutDownHookThread = new Thread(this::shutDown, "ChatController.ShutDownHook");
        Runtime.getRuntime().addShutdownHook(shutDownHookThread);
    }

    private void shutDown() {
        if (!shuttingDown) {
            Platform.runLater(() -> shuttingDown = true);
        }
    }

    public void startPolling() {
        Timer timer = new Timer();
        long interval = 1000;
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    List<String> remoteList = new ArrayList<>();
                    storageClient.get("addresses", serializable -> {
                        if (serializable instanceof Set) {
                            Set<String> set = (Set<String>) serializable;
                            set.stream().filter(e -> !e.equals(ownAddress)).forEach(
                                    e -> remoteList.add(e));
                            remoteList.sort(String::compareTo);
                            Platform.runLater(() -> {
                                if (!addresses.equals(remoteList)) addresses.setAll(remoteList);
                            });
                        }
                    });
                });
            }
        }, 0, interval);
    }

    public void start(Stage stage, StorageClient storageClient, Consumer<String> selectionHandler) {
        this.storageClient = storageClient;
        storageClient.start();
        stage.setOnCloseRequest(e -> shutDown());

        listView.setItems(addresses);

        //noinspection Convert2Lambda
        listView.getSelectionModel().getSelectedItems().addListener(new ListChangeListener() {
            @Override
            public void onChanged(Change c) {
                selectionHandler.accept((String) listView.getSelectionModel().getSelectedItem());
            }
        });
    }

    public void setOwnAddress(String ownAddress) {
        this.ownAddress = ownAddress;
    }

}
