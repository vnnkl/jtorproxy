package io.nucleo.net.app;

import io.nucleo.net.Repo;
import io.nucleo.net.chat.ChatController;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Chat extends Application {
    private static final Logger log = LoggerFactory.getLogger(Chat.class);

    private static String id = "chat";
    private static int port = 1111;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    // optional args: id port

    public static void main(String[] args) {
        if (args.length > 0)
            id = args[0];
        if (args.length > 1)
            port = Integer.parseInt(args[1]);

        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException, InterruptedException {
        Repo repo = new Repo();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/io/nucleo/net/chat/ChatView.fxml"));
        Pane chatView = loader.load();
        ChatController chatController = loader.getController();
        chatController.init(primaryStage, id, port, repo);

        Scene scene = new Scene(chatView, 900, 800);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
