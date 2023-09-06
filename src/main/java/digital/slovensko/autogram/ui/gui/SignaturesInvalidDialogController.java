package digital.slovensko.autogram.ui.gui;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class SignaturesInvalidDialogController implements SuppressedFocusController {
    private final SigningDialogController signigDialogController;

    @FXML
    Button cancelButton;
    @FXML
    Button continueButton;
    @FXML
    Node mainBox;

    public SignaturesInvalidDialogController(SigningDialogController signigDialogController) {
        this.signigDialogController = signigDialogController;
    }

    public void onCancelAction() {
        var window = mainBox.getScene().getRoot().getScene().getWindow();
        if (window instanceof Stage)
            ((Stage) window).close();

        signigDialogController.enableSigning();
    }

    public void onContinueAction() {
        var window = mainBox.getScene().getRoot().getScene().getWindow();
        if (window instanceof Stage)
            ((Stage) window).close();
        signigDialogController.sign();
    }

    @Override
    public Node getNodeForLoosingFocus() {
        return mainBox;
    }

}
