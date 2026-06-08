package com.project.flowfinserver.dto;

import java.util.List;

public record ExpenseSaveResult(int savedCount, List<Long> pendingAiIds) {}
