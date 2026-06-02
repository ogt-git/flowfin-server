package com.project.flowfinserver.codef;

import com.project.flowfinserver.exception.CodefErrorType;

import java.util.Set;

public final class CodefErrorClassifier {

    private static final Set<String> AUTH_ERROR_CODES = Set.of(
            "CF-04008", "CF-04010", "CF-04019", "CF-04038",
            "CF-12801", "CF-12899"
    );

    private static final Set<String> AUTH_UNRECOVERABLE_CODES = Set.of(
            "CF-12802", "CF-12806"
    );

    private static final Set<String> TRANSIENT_ERROR_CODES = Set.of(
            "CF-01002", "CF-01004", "CF-01007", "CF-00016",
            "CF-01006", "CF-12003", "CF-12104", "CF-12703",
            "CF-12201", "CF-12701"
    );

    private static final Set<String> RATE_LIMIT_CODES = Set.of(
            "CF-00012", "CF-00022", "CF-00023"
    );

    private static final Set<String> PERMANENT_ERROR_CODES = Set.of(
            "CF-04000", "CF-04015"
    );

    private CodefErrorClassifier() {}

    public static CodefErrorType classify(String errorCode) {
        if (errorCode == null) return CodefErrorType.UNKNOWN;
        if (AUTH_ERROR_CODES.contains(errorCode))        return CodefErrorType.AUTH_ERROR;
        if (AUTH_UNRECOVERABLE_CODES.contains(errorCode)) return CodefErrorType.AUTH_UNRECOVERABLE;
        if (TRANSIENT_ERROR_CODES.contains(errorCode))   return CodefErrorType.TRANSIENT_ERROR;
        if (RATE_LIMIT_CODES.contains(errorCode))        return CodefErrorType.RATE_LIMIT_ERROR;
        if (PERMANENT_ERROR_CODES.contains(errorCode))   return CodefErrorType.PERMANENT_ERROR;
        return CodefErrorType.UNKNOWN;
    }
}
