package com.project.flowfinserver.codef;

import com.project.flowfinserver.exception.CodefErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CodefErrorClassifierTest {

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("AUTH_ERROR 코드 분류")
    @CsvSource({
            "CF-04008, AUTH_ERROR",
            "CF-04010, AUTH_ERROR",
            "CF-04019, AUTH_ERROR",
            "CF-04038, AUTH_ERROR",
            "CF-12899, AUTH_ERROR"
    })
    void authError(String code, CodefErrorType expected) {
        assertThat(CodefErrorClassifier.classify(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("AUTH_UNRECOVERABLE 코드 분류")
    @CsvSource({
            "CF-12802, AUTH_UNRECOVERABLE",
            "CF-12806, AUTH_UNRECOVERABLE",
            "CF-12801, AUTH_UNRECOVERABLE",
            "CF-12800, AUTH_UNRECOVERABLE",
            "CF-12803, AUTH_UNRECOVERABLE",
            "CF-12834, AUTH_UNRECOVERABLE",
            "CF-12833, AUTH_UNRECOVERABLE",
            "CF-13031, AUTH_UNRECOVERABLE",
            "CF-13032, AUTH_UNRECOVERABLE"
    })
    void authUnrecoverable(String code, CodefErrorType expected) {
        assertThat(CodefErrorClassifier.classify(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("COOLDOWN 코드 분류")
    @CsvSource({
            "CF-12201, COOLDOWN",
            "CF-01006, COOLDOWN",
            "CF-12207, COOLDOWN",
            "CF-12106, COOLDOWN"
    })
    void cooldown(String code, CodefErrorType expected) {
        assertThat(CodefErrorClassifier.classify(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("TRANSIENT_ERROR 코드 분류")
    @CsvSource({
            "CF-01002, TRANSIENT_ERROR",
            "CF-01004, TRANSIENT_ERROR",
            "CF-01007, TRANSIENT_ERROR",
            "CF-00016, TRANSIENT_ERROR",
            "CF-12003, TRANSIENT_ERROR",
            "CF-12104, TRANSIENT_ERROR",
            "CF-12703, TRANSIENT_ERROR",
            "CF-12107, TRANSIENT_ERROR"
    })
    void transientError(String code, CodefErrorType expected) {
        assertThat(CodefErrorClassifier.classify(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("RATE_LIMIT_ERROR 코드 분류")
    @CsvSource({
            "CF-00012, RATE_LIMIT_ERROR",
            "CF-00022, RATE_LIMIT_ERROR",
            "CF-00023, RATE_LIMIT_ERROR"
    })
    void rateLimitError(String code, CodefErrorType expected) {
        assertThat(CodefErrorClassifier.classify(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("PERMANENT_ERROR 코드 분류")
    @CsvSource({
            "CF-04000, PERMANENT_ERROR",
            "CF-04015, PERMANENT_ERROR",
            "CF-13010, PERMANENT_ERROR",
            "CF-13013, PERMANENT_ERROR"
    })
    void permanentError(String code, CodefErrorType expected) {
        assertThat(CodefErrorClassifier.classify(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("CARD_UNAVAILABLE 코드 분류")
    @CsvSource({
            "CF-13101, CARD_UNAVAILABLE",
            "CF-13110, CARD_UNAVAILABLE"
    })
    void cardUnavailable(String code, CodefErrorType expected) {
        assertThat(CodefErrorClassifier.classify(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("EMPTY_RESULT 코드 분류")
    @CsvSource({
            "CF-03999, EMPTY_RESULT",
            "CF-12109, EMPTY_RESULT",
            "CF-12111, EMPTY_RESULT",
            "CF-13025, EMPTY_RESULT",
            "CF-13105, EMPTY_RESULT"
    })
    void emptyResult(String code, CodefErrorType expected) {
        assertThat(CodefErrorClassifier.classify(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("INSTITUTION_UNAVAILABLE 코드 분류")
    @CsvSource({
            "CF-12701, INSTITUTION_UNAVAILABLE",
            "CF-12710, INSTITUTION_UNAVAILABLE",
            "CF-12041, INSTITUTION_UNAVAILABLE"
    })
    void institutionUnavailable(String code, CodefErrorType expected) {
        assertThat(CodefErrorClassifier.classify(code)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → UNKNOWN")
    @DisplayName("UNKNOWN 코드 분류 — 미등록 코드 및 null")
    @CsvSource({
            "CF-99999",
            "CF-00001",
            "INVALID"
    })
    void unknownError(String code) {
        assertThat(CodefErrorClassifier.classify(code)).isEqualTo(CodefErrorType.UNKNOWN);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("null 입력 시 UNKNOWN 반환")
    void nullCode() {
        assertThat(CodefErrorClassifier.classify(null)).isEqualTo(CodefErrorType.UNKNOWN);
    }
}
