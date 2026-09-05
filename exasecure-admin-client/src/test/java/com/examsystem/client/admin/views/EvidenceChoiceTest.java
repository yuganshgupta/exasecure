package com.examsystem.client.admin.views;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class EvidenceChoiceTest {
    @Test void everyArtifactRetainsItsExactRetrievalKeyAndUnknownStage() {
        var choices = EvidenceChoice.forTypes(List.of("L1", "L2", "LZZ", "INITIAL", "LONG_ABSENCE"));
        assertThat(choices).extracting(EvidenceChoice::storageType).containsExactly("L1", "L2", "LZZ", "INITIAL", "LONG_ABSENCE");
        assertThat(choices.subList(0, 3)).extracting(Object::toString).containsExactly(
                "Legacy evidence 1 — capture stage unknown", "Legacy evidence 2 — capture stage unknown", "Legacy evidence 3 — capture stage unknown");
    }
    @Test void missingTypesDoNotFabricateInitialEvidence() {
        assertThat(EvidenceChoice.forTypes(null)).isEmpty();
        assertThat(EvidenceChoice.forTypes(List.of())).isEmpty();
    }
}
