package com.project.flowfinserver.dto.codef;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class CodefSyncResultDto {
    private final int savedCount;
    private final int skippedCount;
    private final List<String> failedAccounts;
    private final LocalDateTime syncedAt;
    private final String accountType;
    private final LocalDateTime nextAvailableAt;
}
