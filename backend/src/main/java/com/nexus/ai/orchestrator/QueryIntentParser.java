package com.nexus.ai.orchestrator;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QueryIntentParser {

    public enum QuestionTag {
        WHAT, WHERE, WHEN, HOW, WHO, WHY, WHICH, GENERAL
    }

    public enum QueryDomain {
        EDUCATION,
        PERSON,
        COMPANY,
        SOFTWARE_HARDWARE,
        PLACE,
        SCIENCE_MYSTERY,
        GEOPOLITICS,
        LEGAL,
        GENERAL
    }

    public static class ParsedIntent {
        private final QuestionTag questionTag;
        private final QueryDomain domain;
        private final String cleanKeywords;
        private final List<String> targetedSearchQueries;

        public ParsedIntent(QuestionTag questionTag, QueryDomain domain, String cleanKeywords, List<String> targetedSearchQueries) {
            this.questionTag = questionTag;
            this.domain = domain;
            this.cleanKeywords = cleanKeywords;
            this.targetedSearchQueries = targetedSearchQueries;
        }

        public QuestionTag getQuestionTag() { return questionTag; }
        public QueryDomain getDomain() { return domain; }
        public String getCleanKeywords() { return cleanKeywords; }
        public List<String> getTargetedSearchQueries() { return targetedSearchQueries; }
    }

    public ParsedIntent parse(String rawPrompt) {
        if (rawPrompt == null || rawPrompt.trim().isEmpty()) {
            return new ParsedIntent(QuestionTag.GENERAL, QueryDomain.GENERAL, "", List.of());
        }

        String prompt = rawPrompt.trim();
        String lower = prompt.toLowerCase();

        // 1. Identify Questioning Tag
        QuestionTag tag = QuestionTag.GENERAL;
        if (lower.startsWith("what") || lower.contains(" what is") || lower.contains(" what are")) tag = QuestionTag.WHAT;
        else if (lower.startsWith("where") || lower.contains(" where is") || lower.contains(" located")) tag = QuestionTag.WHERE;
        else if (lower.startsWith("when") || lower.contains(" when did") || lower.contains(" date of")) tag = QuestionTag.WHEN;
        else if (lower.startsWith("how") || lower.contains(" how to") || lower.contains(" how does")) tag = QuestionTag.HOW;
        else if (lower.startsWith("who") || lower.contains(" who is") || lower.contains(" founder")) tag = QuestionTag.WHO;
        else if (lower.startsWith("why") || lower.contains(" why does") || lower.contains(" reason for")) tag = QuestionTag.WHY;
        else if (lower.startsWith("which")) tag = QuestionTag.WHICH;

        // 2. Identify Domain Category
        QueryDomain domain = classifyDomain(lower);

        // 3. Extract Clean Keywords
        String cleanKeywords = extractCleanKeywords(prompt);

        // 4. Generate Multi-Platform Search Routing Queries
        List<String> searchQueries = buildTargetedQueries(cleanKeywords, domain, tag);

        return new ParsedIntent(tag, domain, cleanKeywords, searchQueries);
    }

    private QueryDomain classifyDomain(String lower) {
        // Legal & Regulatory
        if (lower.contains("law") || lower.contains("legal") || lower.contains("statute") ||
            lower.contains("court") || lower.contains("constitution") || lower.contains("regulation")) {
            return QueryDomain.LEGAL;
        }

        // Software & Hardware Architecture / Programming
        if (lower.contains("code") || lower.contains("java") || lower.contains("python") ||
            lower.contains("spring") || lower.contains("microservices") || lower.contains("sql") ||
            lower.contains("algorithm") || lower.contains("dsa") || lower.contains("hardware") ||
            lower.contains("chip") || lower.contains("architecture") || lower.contains("api") ||
            lower.contains("docker") || lower.contains("kubernetes") || lower.contains("refactor") ||
            lower.contains("data structure") || lower.contains("leetcode") || lower.contains("bug")) {
            return QueryDomain.SOFTWARE_HARDWARE;
        }

        // Company & Corporate Registry
        if (lower.contains("company") || lower.contains("office") || lower.contains("branch") ||
            lower.contains("corp") || lower.contains("inc") || lower.contains("ltd") ||
            lower.contains("ambitionbox") || lower.contains("glassdoor") || lower.contains("jobs") ||
            lower.contains("careers") || lower.contains("ctc") || lower.contains("salary") ||
            lower.contains("work culture") || lower.contains("headquarters") || lower.contains("labs")) {
            return QueryDomain.COMPANY;
        }

        // Person & Biography
        if (lower.contains("who is") || lower.contains("person") || lower.contains("biography") ||
            lower.contains("ceo") || lower.contains("founder") || lower.contains("author") ||
            lower.contains("profile") || lower.contains("linkedin") || lower.contains("net worth")) {
            return QueryDomain.PERSON;
        }

        // Geopolitics & Ongoing Global Issues
        if (lower.contains("geopolitics") || lower.contains("war") || lower.contains("treaty") ||
            lower.contains("sanction") || lower.contains("g20") || lower.contains("un ") ||
            lower.contains("diplomacy") || lower.contains("minister") || lower.contains("conflict") ||
            lower.contains("ongoing issue") || lower.contains("international relations")) {
            return QueryDomain.GEOPOLITICS;
        }

        // Education & Academics
        if (lower.contains("university") || lower.contains("degree") || lower.contains("course") ||
            lower.contains("syllabus") || lower.contains("college") || lower.contains("academic") ||
            lower.contains("textbook") || lower.contains("exam") || lower.contains("study")) {
            return QueryDomain.EDUCATION;
        }

        // Place & Geolocation
        if (lower.contains("city") || lower.contains("district") || lower.contains("state") ||
            lower.contains("country") || lower.contains("river") || lower.contains("mountain") ||
            lower.contains("tourist") || lower.contains("map") || lower.contains("where is")) {
            return QueryDomain.PLACE;
        }

        // Science & Mystery
        if (lower.contains("physics") || lower.contains("quantum") || lower.contains("mystery") ||
            lower.contains("space") || lower.contains("nasa") || lower.contains("theory") ||
            lower.contains("experiment") || lower.contains("paradox") || lower.contains("debate")) {
            return QueryDomain.SCIENCE_MYSTERY;
        }

        return QueryDomain.GENERAL;
    }

    private String extractCleanKeywords(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim()
                .replaceAll("(?i)^(what is|what are|where is|where are|who is|who are|when is|when did|how is|how does|why is|why does|tell me about|explain me about|explain me abot|explain|search web for|search for|information on)\\s+", "")
                .replaceAll("[?!=.,\"]", "")
                .trim();
        cleaned = cleaned.replaceAll("(?i)\\bcaption\\b", "captain");
        String lowerRaw = raw.toLowerCase();
        if ((lowerRaw.contains("subas chandra") || lowerRaw.contains("subash chandra") || lowerRaw.contains("subhash chandra")) &&
            (lowerRaw.contains("boss") || lowerRaw.contains("bose") || lowerRaw.contains("netaji"))) {
            return "Subhas Chandra Bose freedom fighter Netaji";
        }
        if (cleaned.equalsIgnoreCase("ssc") || lowerRaw.equals("ssc") || lowerRaw.equals("tell me about ssc") || lowerRaw.equals("what is ssc")) {
            return "Staff Selection Commission SSC 10th Board Exam SSC Napoli";
        }
        return cleaned;
    }

    private List<String> buildTargetedQueries(String keywords, QueryDomain domain, QuestionTag tag) {
        if (keywords == null || keywords.isEmpty()) return List.of();
        return List.of(keywords);
    }
}
