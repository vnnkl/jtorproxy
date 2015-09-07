package io.nucleo.gui.app;

import io.nucleo.gui.chat.ChatController;
import io.nucleo.net.LocalHostNetwork;
import io.nucleo.net.Network;
import io.nucleo.net.TorNetwork;
import io.nucleo.storage.InProcessStorageClient;
import io.nucleo.storage.StorageClient;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Chat extends Application {
    private static final Logger log = LoggerFactory.getLogger(Chat.class);

    private static String id = "chat";
    private static int port = 3333;
    private static boolean useTor = true;
    //private static String serverStorageUrl = "localhost:8888";
    private static String serverStorageUrl;


    // optional args: id port useTor useServerStorage
    public static void main(String[] args) {
        if (args.length > 0) id = args[0];
        if (args.length > 1) port = Integer.parseInt(args[1]);
        if (args.length > 2) useTor = Boolean.parseBoolean(args[2]);
        if (args.length > 3) serverStorageUrl = args[3];

        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException, InterruptedException {
        StorageClient storageClient;
        if (serverStorageUrl != null) {
            Network storageClientNetwork = serverStorageUrl.contains("onion:") ? new TorNetwork() : new LocalHostNetwork();
            storageClient = new StorageClient(serverStorageUrl, id, storageClientNetwork);
        } else {
            storageClient = new InProcessStorageClient();
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/io/nucleo/gui/chat/ChatView.fxml"));
        Pane chatView = loader.load();
        ChatController chatController = loader.getController();
        Network network = useTor ? new TorNetwork() : new LocalHostNetwork();
        chatController.init(network, primaryStage, id, port, storageClient);

        Scene scene = new Scene(chatView, 900, 800);
        primaryStage.setScene(scene);
        primaryStage.setTitle(id);
        primaryStage.show();
    }
}
