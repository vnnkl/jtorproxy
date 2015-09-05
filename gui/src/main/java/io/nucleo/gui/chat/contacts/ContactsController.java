package io.nucleo.gui.chat.contacts;

import io.nucleo.storage.Storage;
import java.io.Serializable;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
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


public class ContactsController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ContactsController.class);

    @FXML private ListView listView;

    private final ObservableList<String> addresses = FXCollections.observableArrayList();
    private final List<String> remoteList = new ArrayList<>();

    private boolean shuttingDown;
    private Storage storage;
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
            Platform.runLater(() -> {
                shuttingDown = true;
            });
        }
    }

    private void poll() {
        Timer timer = new Timer();
        long interval = 1000;
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    remoteList.clear();
                    Serializable result = storage.get("addresses");
                    if (result instanceof Set) {
                        Set<String> set = (Set<String>) result;
                        set.stream().filter(e -> !e.equals(ownAddress)).forEach(e -> remoteList.add(e));
                    }

                    remoteList.sort((o1, o2) -> o1.compareTo(o2));

                    if (!addresses.equals(remoteList)) addresses.setAll(remoteList);
                });
            }
        }, 0, interval);
    }

    public void init(Stage stage, Storage storage, Consumer<String> selectionHandler) {
        this.storage = storage;
        stage.setOnCloseRequest(e -> shutDown());

        listView.setItems(addresses);
        listView.getSelectionModel().getSelectedItems().addListener(new ListChangeListener() {
            @Override
            public void onChanged(Change c) {
                selectionHandler.accept((String) listView.getSelectionModel().getSelectedItem());
            }
        });

        poll();
    }

    public void setOwnAddress(String ownAddress) {
        this.ownAddress = ownAddress;
    }
}
