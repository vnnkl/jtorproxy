package io.nucleo.gui.app;

import io.nucleo.gui.chat.ChatController;
import io.nucleo.net.LocalHostNetwork;
import io.nucleo.net.Network;
import io.nucleo.net.TorNetwork;
import io.nucleo.storage.InProcessStorageClient;
import io.nucleo.storage.StorageClient;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ChatWith2Peers extends Application {
    private static final Logger log = LoggerFactory.getLogger(ChatWith2Peers.class);

    private static String idLeft = "ChatWith2PeersLeft";
    private static int portLeft = 1111;
    private static String idRight = "ChatWith2PeersRight";
    private static int portRight = 2222;
    private static boolean useTor = true;
    // private static String serverStorageUrl= "localhost:8888";
    private static String serverStorageUrl;

    // optional args: idLeft portLeft idRight portRight useTor useServerStorage
    public static void main(String[] args) {
        if (args.length > 0) idLeft = args[0];
        if (args.length > 1) portLeft = Integer.parseInt(args[1]);
        if (args.length > 2) idRight = args[2];
        if (args.length > 3) portRight = Integer.parseInt(args[3]);
        if (args.length > 4) useTor = Boolean.parseBoolean(args[4]);
        if (args.length > 5) serverStorageUrl = args[5];

        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException, InterruptedException {
        StorageClient storageClientLeft;
        StorageClient storageClientRight;
        if (serverStorageUrl != null) {
            Network storageClientNetworkLeft = serverStorageUrl.contains("onion:") ? new TorNetwork() : new LocalHostNetwork();
            storageClientLeft = new StorageClient(serverStorageUrl, "ChatWith2Peers_storageClient_left", storageClientNetworkLeft);
            Network storageClientNetworkRight = serverStorageUrl.contains("onion:") ? new TorNetwork() : new LocalHostNetwork();
            storageClientRight = new StorageClient(serverStorageUrl, "ChatWith2Peers_storageClient_right", storageClientNetworkRight);
        } else {
            storageClientLeft = storageClientRight = new InProcessStorageClient();
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/io/nucleo/gui/chat/ChatView.fxml"));
        Pane leftChatView = loader.load();
        ChatController leftChatController = loader.getController();
        Network network = useTor ? new TorNetwork() : new LocalHostNetwork();
        leftChatController.init(network, primaryStage, idLeft, portLeft, storageClientLeft);

        loader = new FXMLLoader(getClass().getResource("/io/nucleo/gui/chat/ChatView.fxml"));
        Pane rightChatView = loader.load();
        ChatController rightChatController = loader.getController();
        network = useTor ? new TorNetwork() : new LocalHostNetwork();
        rightChatController.init(network, primaryStage, idRight, portRight, storageClientRight);

        HBox chatPane = new HBox();
        chatPane.setSpacing(10);
        chatPane.setPadding(new Insets(10, 10, 10, 10));
        chatPane.getChildren().addAll(leftChatView, rightChatView);

        Scene scene = new Scene(chatPane, 1600, 800);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
