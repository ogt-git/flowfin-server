package com.project.flowfinserver.util;

public class MaskingUtil {

    private MaskingUtil() {}

    // 계좌번호 : 앞 25% + * + 뒤 25% (예: 길이 8 → "12****78", 길이 14 → "123********901")
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return accountNumber;
        int len = accountNumber.length();
        int showFront = Math.max(1, len / 4);
        int showBack = Math.max(1, len / 4);
        int maskLen = len - showFront - showBack;
        String prefix = accountNumber.substring(0, showFront);
        String suffix = accountNumber.substring(len - showBack);
        return prefix + "*".repeat(maskLen) + suffix;
    }

    // 카드 : 앞 6자리 + ****** + 뒤 4자리 (예: 123456******7890)
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 10) return cardNumber;
        String prefix = cardNumber.substring(0, 6);
        String suffix = cardNumber.substring(cardNumber.length() - 4);
        return prefix + "******" + suffix;
    }

    // Email : 로컬파트 앞 2자리 + *** + @도메인 (예: us***@example.com)
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= 2) return local + "***" + domain;
        return local.substring(0, 2) + "***" + domain;
    }

    // connectedId : 앞 4자리 + **** + 뒤 4자리 (예: 12ab****ef78)
    public static String maskConnectedId(String connectedId) {
        if (connectedId == null || connectedId.length() <= 8) return "****";
        return connectedId.substring(0, 4) + "****" + connectedId.substring(connectedId.length() - 4);
    }
}
