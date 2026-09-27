package com.nexus.ai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class SecurityShield {

    private static final Logger log = LoggerFactory.getLogger(SecurityShield.class);

    private static final Pattern INJECTION_PATTERNS = Pattern.compile(
            "(?i)(ignore previous instructions|repeat system prompt|print your system prompt|extract internal system prompt|output system prompt|show developer mode|jailbreak|dump memory|list api keys|show groq_api_key|show gemini_api_key|bypass safety|dump rag database|exfiltrate data via claude|exfiltrate data via codex)"
    );

    public boolean isPromptInjection(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) return false;
        boolean matched = INJECTION_PATTERNS.matcher(prompt).find();
        if (matched) {
            log.warn("SecurityShield caught potential prompt injection / anti-exfiltration attack in prompt");
        }
        return matched;
    }

    public String sanitizePrompt(String prompt) {
        if (prompt == null) return "";
        if (isPromptInjection(prompt)) {
            return "[SECURITY SHIELD ACTIVATED]: The prompt contained illegal injection instructions. NEXUS AI will answer the query safely without exposing system prompts or credentials.";
        }
        return prompt;
    }
}
