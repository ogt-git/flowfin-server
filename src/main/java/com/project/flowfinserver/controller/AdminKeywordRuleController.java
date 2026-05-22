package com.project.flowfinserver.controller;

import com.project.flowfinserver.service.ExpenseKeywordClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminKeywordRuleController {

    private final ExpenseKeywordClassifier keywordClassifier;

    @PostMapping("/keyword-rules/reload")
    public ResponseEntity<String> reload() {
        keywordClassifier.reloadRules();
        return ResponseEntity.ok("keyword rules reloaded");
    }
}
