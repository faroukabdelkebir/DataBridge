package wevioo.se.monitoringms.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayTranslationServiceTest {

    private final DisplayTranslationService service = new DisplayTranslationService();

    @ParameterizedTest
    @CsvSource({
            "ABANDONED,  Abandonné",
            "COMPLETED,  Terminé",
            "EXECUTING,  En cours d'exécution",
            "FAILED,     Échec",
            "NOOP,       Aucune action",
            "STARTED,    En cours",
            "STARTING,   Démarrage",
            "STOPPED,    Arrêté",
            "STOPPING,   Arrêt en cours",
            "UNKNOWN,    Inconnu"
    })
    void translateStatusCoversEveryBatchStatus(String technical, String expected) {
        assertThat(service.translateStatus(technical)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"completed", "Completed", "cOmPlEtEd"})
    void translateStatusIsCaseInsensitive(String value) {
        assertThat(service.translateStatus(value)).isEqualTo("Terminé");
    }

    @Test
    void translateStatusReturnsUnknownValueUnchanged() {
        assertThat(service.translateStatus("PENDING_REVIEW")).isEqualTo("PENDING_REVIEW");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void translateStatusPassesThroughNullAndBlankValues(String value) {
        assertThat(service.translateStatus(value)).isEqualTo(value);
    }
}