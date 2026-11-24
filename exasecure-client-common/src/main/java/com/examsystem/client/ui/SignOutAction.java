package com.examsystem.client.ui;

import com.examsystem.client.api.ApiClient;
import com.examsystem.client.async.AsyncRunner;
import javafx.scene.Node;

import java.util.function.Consumer;

public final class SignOutAction {
    private SignOutAction() {}

    public static void run(ApiClient api, Node currentView, Runnable showLogin) {
        var session = api.getSessionState().get();
        currentView.setDisable(true);
        Consumer<Boolean> complete = revoked -> {
            if (currentView.getScene() == null) return;
            if (api.getSessionState().get() != null) {
                currentView.setDisable(false);
                return;
            }
            showLogin.run();
            if (!revoked) {
                UiFeedback.showInfo("Signed Out Locally", "The Server could not confirm that the previous session was revoked.");
            }
        };
        AsyncRunner.runAsync(() -> api.logoutSession(session), complete, error -> complete.accept(false));
    }
}
