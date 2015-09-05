package io.nucleo.net.app;

import io.nucleo.net.Repo;
import io.nucleo.net.chat.ChatController;
import io.nucleo.net.contacts.ContactsController;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.layout.*;
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
        if (args.length > 0)
            idLeft = args[0];
        if (args.length > 1)
            portLeft = Integer.parseInt(args[1]);
        if (args.length > 2)
            idRight = args[2];
        if (args.length > 3)
            portRight = Integer.parseInt(args[3]);

        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException, InterruptedException {
        Repo repo = new Repo();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/io/nucleo/net/chat/ChatView.fxml"));
        Pane leftChatView = loader.load();
        ChatController leftChatController = loader.getController();
        leftChatController.init(primaryStage, idLeft, portLeft, repo);

        loader = new FXMLLoader(getClass().getResource("/io/nucleo/net/chat/ChatView.fxml"));
        Pane rightChatView = loader.load();
        ChatController rightChatController = loader.getController();
        rightChatController.init(primaryStage, idRight, portRight, repo);

        loader = new FXMLLoader(getClass().getResource("/io/nucleo/net/contacts/ContactsView.fxml"));
        Pane leftContactsView = loader.load();
        ContactsController leftContactsController = loader.getController();
        leftContactsController.init(primaryStage, repo, leftChatController::connectToPeer);

        loader = new FXMLLoader(getClass().getResource("/io/nucleo/net/contacts/ContactsView.fxml"));
        Pane rightContactsView = loader.load();
        ContactsController rightContactsController = loader.getController();
        rightContactsController.init(primaryStage, repo, rightChatController::connectToPeer);

        HBox chatPane = new HBox();
        chatPane.setSpacing(10);
        chatPane.setPadding(new Insets(10, 10, 10, 10));
        chatPane.getChildren().addAll(leftContactsView, leftChatView, rightContactsView, rightChatView);

        Scene scene = new Scene(chatPane, 1600, 800);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
