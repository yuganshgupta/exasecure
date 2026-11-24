package com.examsystem.client.admin.views;

import com.examsystem.client.session.SessionState.UserSession;

record EvidenceRequest(long generation, UserSession owner) {
    boolean isCurrent(long currentGeneration, UserSession currentSession) {
        return generation == currentGeneration && owner != null && owner == currentSession;
    }
}
