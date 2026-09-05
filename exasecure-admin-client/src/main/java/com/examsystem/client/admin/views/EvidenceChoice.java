package com.examsystem.client.admin.views;

import java.util.ArrayList;
import java.util.List;

/** Display labels never replace the exact server storage key used for retrieval. */
record EvidenceChoice(String storageType, String label) {
    static List<EvidenceChoice> forTypes(List<String> types) {
        if (types == null) return List.of();
        List<EvidenceChoice> choices = new ArrayList<>();
        int legacy = 0;
        for (String type : types) {
            String label = switch (type) {
                case "INITIAL" -> "Initial evidence (~0.8s)";
                case "LONG_ABSENCE" -> "Long absence evidence (~5.0s)";
                default -> "Legacy evidence " + (++legacy) + " — capture stage unknown";
            };
            choices.add(new EvidenceChoice(type, label));
        }
        return List.copyOf(choices);
    }

    @Override public String toString() { return label; }
}
