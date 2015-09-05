package io.nucleo.net.contacts;

import io.nucleo.net.Repo;

import java.net.URL;

import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ContactsController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ContactsController.class);

    @FXML private ListView listView;

    private final ObservableList<String> addresses = FXCollections.observableArrayList();

    private boolean shuttingDown;
    private Repo repo;

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
                    addresses.clear();
                    repo.getAddresses().stream().forEach(e -> addresses.add(e));
                });
            }
        }, 0, interval);
    }

    public void init(Stage stage, Repo repo, Consumer<String> selectionHandler) {
        this.repo = repo;
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
}
