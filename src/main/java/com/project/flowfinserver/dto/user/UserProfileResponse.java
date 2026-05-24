package com.project.flowfinserver.dto.user;

import com.project.flowfinserver.domain.CodefConnectedAccount;
import com.project.flowfinserver.domain.User;
import com.project.flowfinserver.util.MaskingUtil;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class UserProfileResponse {

    private final Long id;
    private final String name;
    private final String email;
    private final String riskType;
    private final LocalDateTime createdAt;
    private final List<ConnectedAccountInfo> connectedAccounts;

    public UserProfileResponse(User user, List<CodefConnectedAccount> accounts) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = MaskingUtil.maskEmail(user.getEmail());
        this.riskType = user.getRiskType();
        this.createdAt = user.getCreatedAt();
        this.connectedAccounts = accounts.stream()
                .map(ConnectedAccountInfo::new)
                .toList();
    }

    @Getter
    public static class ConnectedAccountInfo {
        private final Long id;
        private final String organizationCode;
        private final String accountType;
        private final String accountNumber;

        public ConnectedAccountInfo(CodefConnectedAccount account) {
            this.id = account.getId();
            this.organizationCode = account.getOrganizationCode();
            this.accountType = account.getAccountType().name();
            this.accountNumber = MaskingUtil.maskAccountNumber(account.getAccountNumber());
        }
    }
}
