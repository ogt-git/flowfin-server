package com.project.flowfinserver.dto.codef;

import com.project.flowfinserver.domain.CodefConnectedAccount;
import com.project.flowfinserver.util.MaskingUtil;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CodefConnectionResponse {

    private final Long id;
    private final String organizationCode;
    private final String accountType;
    private final String accountNumber;
    private final LocalDateTime createdAt;

    public CodefConnectionResponse(CodefConnectedAccount account) {
        this.id = account.getId();
        this.organizationCode = account.getOrganizationCode();
        this.accountType = account.getAccountType().name();
        this.accountNumber = MaskingUtil.maskAccountNumber(account.getAccountNumber());
        this.createdAt = account.getCreatedAt();
    }
}
