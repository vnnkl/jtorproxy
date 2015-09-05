package io.nucleo.gui.app;

import io.nucleo.gui.chat.ChatController;
import io.nucleo.net.LocalHostNetwork;
import io.nucleo.net.Network;
import io.nucleo.net.Repo;
import io.nucleo.net.TorNetwork;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatWith2Peers extends Application {
    private static final Logger log = LoggerFactory.getLogger(ChatWith2Peers.class);

    private static String idLeft = "chatLeft";
    private static int portLeft = 1111;
    private static String idRight = "chatRight";
    private static int portRight = 2222;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void main(String[] args) {
        if (args.length > 0) idLeft = args[0];
        if (args.length > 1) portLeft = Integer.parseInt(args[1]);
        if (args.length > 2) idRight = args[2];
        if (args.length > 3) portRight = Integer.parseInt(args[3]);

        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException, InterruptedException {
        boolean useTor = false;
        Repo repo = new Repo();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/io/nucleo/gui/chat/ChatView.fxml"));
        Pane leftChatView = loader.load();
        ChatController leftChatController = loader.getController();
        Network network = useTor ? new TorNetwork() : new LocalHostNetwork();
        leftChatController.init(network, primaryStage, idLeft, portLeft, repo);

        loader = new FXMLLoader(getClass().getResource("/io/nucleo/gui/chat/ChatView.fxml"));
        Pane rightChatView = loader.load();
        ChatController rightChatController = loader.getController();
        network = useTor ? new TorNetwork() : new LocalHostNetwork();
        rightChatController.init(network, primaryStage, idRight, portRight, repo);

        HBox chatPane = new HBox();
        chatPane.setSpacing(10);
        chatPane.setPadding(new Insets(10, 10, 10, 10));
        chatPane.getChildren().addAll(leftChatView, rightChatView);

        Scene scene = new Scene(chatPane, 1600, 800);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
