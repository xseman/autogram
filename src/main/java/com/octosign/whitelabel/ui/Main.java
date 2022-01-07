package com.octosign.whitelabel.ui;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.octosign.whitelabel.communication.SignedData;
import com.octosign.whitelabel.communication.SignatureUnit;
import com.octosign.whitelabel.error_handling.*;

import com.octosign.whitelabel.signing.SigningManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import com.octosign.whitelabel.cli.command.CommandFactory;
import com.octosign.whitelabel.cli.command.ListenCommand;
import com.octosign.whitelabel.communication.Info;
import com.octosign.whitelabel.communication.document.Document;
import com.octosign.whitelabel.communication.server.Server;
import javafx.stage.Window;
import org.slf4j.LoggerFactory;

import static com.octosign.whitelabel.ui.FXUtils.*;
import static com.octosign.whitelabel.ui.I18n.translate;

import static java.util.Objects.requireNonNullElse;

public class Main extends Application {

    public enum Status {
        LOADING,
        READY,
    }

    private final SigningManager signingManager = new SigningManager();

    private StatusIndication statusIndication;

    private Server server;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());

        Application.launch(Main.class, args);
    }

    @Override
    public void start(Stage primaryStage) {
        var cliCommand = CommandFactory.fromParameters(getParameters());

        if (cliCommand instanceof ListenCommand listenCommand) {
            startServer(listenCommand);
            statusIndication = new StatusIndication(this::exit);

            // Prevent exiting in server mode on last window close
            Platform.setImplicitExit(false);
        } else {
            // No CLI command means standalone GUI mode
            // TODO: Show something more useful, this should be either:
            // 1. Standalone mode once it gets implemented
            // 2. Info about the app and how it is launched from the web
            displayInfo("info.appLaunched.header", "info.appLaunched.description");
        }
    }

    private void startServer(ListenCommand command) {
        I18n.setLocale(command.getLanguage());

        server = new Server(command.getHost(), command.getPort(), command.getInitialNonce(), command.isRequiredSSL());
        server.setAllowedOrigin(command.getOrigin());

        var version = getVersion();
        server.setDevMode(version.equals("dev"));
        server.setInfo(new Info(version, Status.LOADING));

        if (command.getOrigin() != null)
            server.setAllowedOrigin(command.getOrigin());

        if (command.getSecretKey() != null)
            server.setSecretKey(command.getSecretKey());

        server.start();
        System.out.println(translate("app.runningOn", server.getAddress()));

      // TODO decide,gkit if we want to allow documentation outside of dev or not
      // if (server.isDevMode()) {
            var protocol = server.isHttps() ? "https" : "http";
            var docsAddress = protocol + ":/" + server.getAddress().toString() + "/documentation";
            System.out.println(translate("text.docsAvailableAt", docsAddress));
      //  }

        server.setOnSign((SignatureUnit unit) -> {
            var future = new CompletableFuture<SignedData>();

            Platform.runLater(() -> {
                openWindow(unit, (byte[] signedContent) -> {
                    Document d = unit.getDocument();
                    Document signedDocument = new Document(d.getId(), d.getTitle(), signedContent, d.getLegalEffect());
                    SignedData signedData = new SignedData(signedDocument, unit.getMimeType(), unit.isPlainOldXML());

                    future.complete(signedData);
                });
            });
            return future;
        });

        server.setInfo(new Info(version, Status.READY));
    }

    private void exit() {
        if (statusIndication != null) statusIndication.dispose();
        if (server != null) server.stop();
        Platform.exit();
    }

    private void openWindow(SignatureUnit signatureUnit, Consumer<byte[]> onSigned) {
        var windowStage = new Stage();

        var fxmlLoader = loadWindow("main");
        VBox root = fxmlLoader.getRoot();

        MainController controller = fxmlLoader.getController();
        controller.setSigningManager(signingManager);
        controller.setSignatureUnit(signatureUnit);
        controller.setOnSigned((byte[] signedContent) -> {
            onSigned.accept(signedContent);
            windowStage.close();
        });
        controller.loadDocument();

        var scene = new Scene(root, 720, 540);
        windowStage.setTitle(translate("app.name"));
        windowStage.setScene(scene);
        windowStage.show();
    }

    public static FXMLLoader loadWindow(String name) {
        var fxmlLoader = new FXMLLoader(Main.class.getResource(name + ".fxml"), I18n.getBundle());
        try {
            fxmlLoader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fxmlLoader;
    }

    /**
     * Application version as defined in pom if packaged or dev otherwise
     */
    public static String getVersion() {
        return requireNonNullElse(Main.class.getPackage().getImplementationVersion(), "dev");
    }

    private static class ExceptionHandler implements Thread.UncaughtExceptionHandler {

        private static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ExceptionHandler.class);

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            if (throwable instanceof IntegrationException ie) {
                LOGGER.error("IntegrationException - code: " + ie.getCode().toString(), ie);
                if (ie.shouldDisplay())
                    Platform.runLater(() -> displayError(ie));
                closeAllWindows();

            } else if (throwable instanceof UserException ue) {
                LOGGER.error("UserException: ", ue);
                Platform.runLater(() -> displayError(ue));

            } else {
                LOGGER.error("[NOT EXPECTED] Error: " + throwable.getClass().getName(), throwable);
                Platform.runLater(FXUtils::displaySimpleError);
                System.exit(1);
            }
        }

        private void closeAllWindows() {
            Stage.getWindows().stream().filter(Window::isShowing).forEach(window -> ((Stage)window).close());
        }
    }
}
