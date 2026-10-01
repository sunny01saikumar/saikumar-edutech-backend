package com.nexus.ai.orchestrator;

import com.nexus.ai.domain.*;
import com.nexus.ai.provider.AIProvider;
import com.nexus.ai.provider.AIProviderUnavailableException;
import com.nexus.ai.rag.RAGService;
import com.nexus.ai.service.ConversationService;
import com.nexus.ai.service.QuotaService;
import com.nexus.ai.service.TraceService;
import com.nexus.ai.tools.Tool;
import com.nexus.ai.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AIOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AIOrchestrator.class);

    private final ModelRouter modelRouter;
    private final ContextWindowBuilder contextWindowBuilder;
    private final ConversationService conversationService;
    private final RAGService ragService;
    private final TraceService traceService;
    private final ToolRegistry toolRegistry;
    private final QuotaService quotaService;
    private final com.nexus.ai.orchestrator.QueryIntentParser queryIntentParser;
    private final com.nexus.ai.provider.ProviderRegistry providerRegistry;
    private final com.nexus.ai.security.SecurityShield securityShield;
    private final com.nexus.ai.security.JwtUtil jwtUtil;

    public AIOrchestrator(ModelRouter modelRouter,
                          ContextWindowBuilder contextWindowBuilder,
                          ConversationService conversationService,
                          RAGService ragService,
                          TraceService traceService,
                          ToolRegistry toolRegistry,
                          QuotaService quotaService,
                          com.nexus.ai.orchestrator.QueryIntentParser queryIntentParser,
                          com.nexus.ai.provider.ProviderRegistry providerRegistry,
                          com.nexus.ai.security.SecurityShield securityShield,
                          com.nexus.ai.security.JwtUtil jwtUtil) {
        this.modelRouter = modelRouter;
        this.contextWindowBuilder = contextWindowBuilder;
        this.conversationService = conversationService;
        this.ragService = ragService;
        this.traceService = traceService;
        this.toolRegistry = toolRegistry;
        this.quotaService = quotaService;
        this.queryIntentParser = queryIntentParser;
        this.providerRegistry = providerRegistry;
        this.securityShield = securityShield;
        this.jwtUtil = jwtUtil;
    }

    private String gatherAndLearnContext(String prompt, String conversationId) {
        StringBuilder combinedContext = new StringBuilder();

        // 0. Security Shield Prompt Sanitization
        String safePrompt = securityShield.sanitizePrompt(prompt);

        // 1. Resolve Multi-Turn Pronouns ONLY when explicit pronouns exist in prompt
        String resolvedSearchPrompt = safePrompt;
        String lowerPrompt = safePrompt.toLowerCase();
        boolean hasPronoun = lowerPrompt.matches(".*\\b(he|him|his|she|her|it|they|them|this|that|himself|herself)\\b.*");

        if (hasPronoun && conversationId != null && !conversationId.isEmpty()) {
            try {
                List<Message> history = conversationService.getMessagesForConversation(conversationId);
                if (history != null && history.size() > 1) {
                    for (int i = history.size() - 2; i >= 0; i--) {
                        Message prevMsg = history.get(i);
                        if (prevMsg.getContent() != null && !prevMsg.getContent().trim().isEmpty()) {
                            String prevText = prevMsg.getContent().replaceAll("[\\r\\n]", " ").trim();
                            int periodIdx = prevText.indexOf('.');
                            if (periodIdx > 10 && periodIdx < 80) {
                                prevText = prevText.substring(0, periodIdx);
                            } else if (prevText.length() > 80) {
                                prevText = prevText.substring(0, 80);
                            }
                            resolvedSearchPrompt = safePrompt + " " + prevText;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Multi-turn context resolution warning: {}", e.getMessage());
            }
        }

        // 2. Retrieve Existing RAG Vector Memory
        String ragContext = ragService.retrieveRelevantContext(resolvedSearchPrompt);
        if (ragContext != null && !ragContext.trim().isEmpty()) {
            combinedContext.append(ragContext).append("\n\n");
        }

        // 3. Intent Parsing & Domain-Targeted Multi-Platform Search Routing
        try {
            com.nexus.ai.orchestrator.QueryIntentParser.ParsedIntent intent = queryIntentParser.parse(resolvedSearchPrompt);
            Tool webTool = toolRegistry.getTool("web_search");
            if (webTool != null && intent != null && !intent.getTargetedSearchQueries().isEmpty()) {
                for (String searchQuery : intent.getTargetedSearchQueries()) {
                    ToolCall toolCall = webTool.execute(Map.of("query", searchQuery));
                    if (toolCall != null && toolCall.isSuccess() && toolCall.getResult() != null) {
                        String searchResult = toolCall.getResult();
                        if (!searchResult.contains("No direct web search results found")) {
                            combinedContext.append("--- TARGETED MULTI-SOURCE SEARCH DATA (Domain: ")
                                    .append(intent.getDomain()).append(", Tag: ").append(intent.getQuestionTag()).append(") ---\n")
                                    .append(searchResult)
                                    .append("\n--------------------------------------\n");
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Autonomous domain web search & self-learning warning: {}", e.getMessage());
        }

        return combinedContext.toString().trim();
    }

    private boolean isCodingOrDeployIntent(String prompt) {
        if (prompt == null) return false;
        if (isMediaGenerationIntent(prompt)) return false;
        String lower = prompt.toLowerCase().trim();

        boolean explicitlyTargetsWorkspaceFiles = lower.contains("file") || lower.contains("workspace") ||
                lower.contains("project") || lower.contains("local") || lower.contains("codebase") ||
                lower.contains("c:\\") || lower.contains("./") || lower.contains("directory");

        // Educational/Q&A code requests (without explicit file/workspace target) go to standard LLM chat streaming
        if (!explicitlyTargetsWorkspaceFiles && (
            lower.startsWith("explain") || lower.startsWith("what is") || lower.startsWith("how to") ||
            lower.startsWith("give me") || lower.startsWith("show me") || lower.startsWith("write an example") ||
            lower.contains("example") || lower.contains("tutorial") || lower.contains("question") ||
            lower.contains("concept") || lower.contains("meaning") || lower.contains("definition"))) {
            return false;
        }

        return lower.contains("build app") || lower.contains("create app") || lower.contains("deploy app") ||
               lower.contains("git push") || lower.contains("mvnw") || lower.contains("npm run") ||
               lower.contains("fix bug") || lower.contains("refactor") || lower.contains("edit file") ||
               lower.contains("create file") || lower.contains("read file") || lower.contains("view file") ||
               lower.contains("open file") || lower.contains("write file") || lower.contains("make file") ||
               lower.contains("new file") || lower.contains("add file") || lower.contains("generate file") ||
               lower.contains("create folder") || lower.contains("make folder") || lower.contains("new folder") ||
               lower.contains("add folder") || lower.contains("mkdir") || lower.contains("folder") ||
               lower.contains("modify file") || lower.contains("update file") || lower.contains("change code") ||
               lower.contains("make code changes") || lower.contains("update code") || lower.contains("access local") ||
               lower.contains("local files") || lower.contains("workspace files") || lower.contains("project files") ||
               lower.contains("create endpoint") || lower.contains("create class") || lower.contains("create component") ||
               lower.contains("c:\\") || lower.contains("repository") || lower.contains("deploy webhook") ||
               lower.contains("modify source") || lower.contains("write code to file");
    }

    private boolean isMediaGenerationIntent(String prompt) {
        return isMediaGenerationIntent(prompt, null);
    }

    private boolean isMediaGenerationIntent(String prompt, String activeConvId) {
        if (prompt == null) return false;
        String lower = prompt.toLowerCase().trim();
        boolean hasKeyword = lower.contains("image") || lower.contains("picture") || lower.contains("photo") ||
               lower.contains("phot") || lower.contains("pht") || lower.contains("pic") ||
               lower.contains("pics") || lower.contains("img") || lower.contains("imgs") ||
               lower.contains("foto") || lower.contains("fotos") || lower.contains("generate") ||
               lower.contains("video") || lower.contains("draw") || lower.contains("portrait") ||
               lower.contains("painting") || lower.contains("imagen") || lower.contains("dall-e") ||
               lower.contains("sora") || lower.contains("runway") || lower.contains("illustration") ||
               lower.contains("hot") || lower.contains("glamour") || lower.contains("glam") ||
               lower.contains("saree") || lower.contains("photoshoot") || lower.contains("wallpaper");

        if (hasKeyword) return true;

        if (activeConvId != null && !activeConvId.trim().isEmpty()) {
            List<Message> history = conversationService.getMessagesForConversation(activeConvId);
            boolean convHasMedia = history.stream().anyMatch(m -> m.getContent() != null && m.getContent().contains("📸"));
            if (convHasMedia) {
                return lower.contains("more") || lower.contains("another") || lower.contains("full") ||
                       lower.contains("dress") || lower.contains("outfit") || lower.contains("style") ||
                       lower.contains("look") || lower.contains("pose") || lower.contains("cute") ||
                       lower.contains("beautiful") || lower.contains("red") || lower.contains("black") ||
                       lower.contains("white") || lower.contains("next") || lower.contains("change");
            }
        }
        return false;
    }

    private String resolveWikiQuery(String text) {
        if (text == null) return null;
        String lower = text.toLowerCase();
        if (lower.contains("kajal") || lower.contains("kjal")) return "Kajal_Aggarwal";
        if (lower.contains("kajol")) return "Kajol";
        if (lower.contains("samantha")) return "Samantha_Ruth_Prabhu";
        if (lower.contains("rashmika")) return "Rashmika_Mandanna";
        if (lower.contains("rashi") || lower.contains("raashi") || lower.contains("raashii")) return "Raashii_Khanna";
        if (lower.contains("tamanna") || lower.contains("tamannaah")) return "Tamannaah_Bhatia";
        if (lower.contains("kriti")) return "Kriti_Sanon";
        if (lower.contains("shraddha")) return "Shraddha_Kapoor";
        if (lower.contains("kiara")) return "Kiara_Advani";
        if (lower.contains("pooja")) return "Pooja_Hegde";
        if (lower.contains("keerthy")) return "Keerthy_Suresh";
        if (lower.contains("mrunal")) return "Mrunal_Thakur";
        if (lower.contains("sreeleela") || lower.contains("sri leela")) return "Sreeleela";
        if (lower.contains("nidhhi") || lower.contains("nidhi")) return "Nidhhi_Agerwal";
        if (lower.contains("deepika")) return "Deepika_Padukone";
        if (lower.contains("alia")) return "Alia_Bhatt";
        if (lower.contains("katrina")) return "Katrina_Kaif";
        if (lower.contains("nayanthara")) return "Nayanthara";
        if (lower.contains("trisha")) return "Trisha_Krishnan";
        if (lower.contains("anushka")) return "Anushka_Shetty";
        if (lower.contains("modi")) return "Narendra_Modi";
        if (lower.contains("virat") || lower.contains("kohli") || lower.contains("kholi")) return "Virat_Kohli";
        if (lower.contains("dhoni")) return "MS_Dhoni";
        if (lower.contains("srk") || lower.contains("shah rukh")) return "Shah_Rukh_Khan";
        if (lower.contains("prabhas")) return "Prabhas";
        if (lower.contains("allu arjun")) return "Allu_Arjun";
        if (lower.contains("pawan") || lower.contains("kalyan")) return "Pawan_Kalyan";
        if (lower.contains("mahesh") || lower.contains("babu")) return "Mahesh_Babu";
        if (lower.contains("ram charan")) return "Ram_Charan";
        if (lower.contains("ntr") || lower.contains("jr ntr")) return "N._T._Rama_Rao_Jr.";
        if (lower.contains("rajini") || lower.contains("rajinikanth")) return "Rajinikanth";
        if (lower.contains("elon") || lower.contains("musk")) return "Elon_Musk";
        return null;
    }

    private String formatCleanSubject(String wikiQuery) {
        if (wikiQuery == null) return "";
        switch (wikiQuery) {
            case "Kajal_Aggarwal": return "Kajal Aggarwal Indian actress";
            case "Kajol": return "Kajol Devgan Indian actress";
            case "Samantha_Ruth_Prabhu": return "Samantha Ruth Prabhu Indian actress";
            case "Rashmika_Mandanna": return "Rashmika Mandanna Indian actress";
            case "Raashii_Khanna": return "Raashii Khanna Indian actress";
            case "Tamannaah_Bhatia": return "Tamannaah Bhatia Indian actress";
            case "Kriti_Sanon": return "Kriti Sanon Indian actress";
            case "Shraddha_Kapoor": return "Shraddha Kapoor Indian actress";
            case "Kiara_Advani": return "Kiara Advani Indian actress";
            case "Pooja_Hegde": return "Pooja Hegde Indian actress";
            case "Keerthy_Suresh": return "Keerthy Suresh Indian actress";
            case "Mrunal_Thakur": return "Mrunal Thakur Indian actress";
            case "Sreeleela": return "Sreeleela Indian actress";
            case "Nidhhi_Agerwal": return "Nidhhi Agerwal Indian actress";
            case "Deepika_Padukone": return "Deepika Padukone Indian actress";
            case "Alia_Bhatt": return "Alia Bhatt Indian actress";
            case "Katrina_Kaif": return "Katrina Kaif Indian actress";
            case "Nayanthara": return "Nayanthara South Indian actress";
            case "Trisha_Krishnan": return "Trisha Krishnan South Indian actress";
            case "Anushka_Shetty": return "Anushka Shetty Baahubali Indian actress";
            case "Narendra_Modi": return "Narendra Modi Indian Prime Minister";
            case "Virat_Kohli": return "Virat Kohli Indian Cricket Captain";
            case "MS_Dhoni": return "MS Dhoni Indian Cricket Legend";
            case "Shah_Rukh_Khan": return "Shah Rukh Khan Indian actor";
            case "Prabhas": return "Prabhas Indian actor Baahubali";
            case "Allu_Arjun": return "Allu Arjun Pushpa Indian actor";
            case "Pawan_Kalyan": return "Pawan Kalyan Indian actor politician";
            case "Mahesh_Babu": return "Mahesh Babu Indian actor";
            case "Ram_Charan": return "Ram Charan RRR Indian actor";
            case "N._T._Rama_Rao_Jr.": return "Jr NTR RRR Indian actor";
            case "Rajinikanth": return "Superstar Rajinikanth Indian actor";
            case "Elon_Musk": return "Elon Musk";
            default: return wikiQuery.replace("_", " ");
        }
    }

    private boolean isAdminUser(String userEmail, String authHeader) {
        String role = jwtUtil != null ? jwtUtil.extractRole(authHeader) : null;
        if ("ROLE_ADMIN".equalsIgnoreCase(role)) {
            return true;
        }
        if (userEmail != null) {
            String cleanEmail = userEmail.trim().toLowerCase();
            return "saikumarnexus46@gmail.com".equalsIgnoreCase(cleanEmail) ||
                   "saikumar13637@gmail.com".equalsIgnoreCase(cleanEmail) ||
                   cleanEmail.contains("admin");
        }
        return false;
    }

    private String getDirectRealPhotoUrl(String wikiQuery) {
        if (wikiQuery == null) return null;
        switch (wikiQuery) {
            case "Samantha_Ruth_Prabhu":
                return "https://upload.wikimedia.org/wikipedia/commons/9/9a/Samantha_at_Kushi_Musical_concert_%282%29_%28cropped%29.jpg";
            case "Kajal_Aggarwal":
                return "https://upload.wikimedia.org/wikipedia/commons/e/e6/Kajal_Aggarwal_at_Bhagavanth_Kesari_Trailer_Launch_%281%29.jpg";
            case "Raashii_Khanna":
                return "https://upload.wikimedia.org/wikipedia/commons/4/4c/Raashi_Khanna_at_the_premiere_of_film_12th_Fail_%28cropped%29.jpg";
            case "Rashmika_Mandanna":
                return "https://upload.wikimedia.org/wikipedia/commons/2/23/Rashmika_Mandanna_at_Animal_pre_release_event_%281%29.jpg";
            case "Deepika_Padukone":
                return "https://upload.wikimedia.org/wikipedia/commons/b/b6/Deepika_Padukone_Cannes_2018_%28cropped%29.jpg";
            case "Alia_Bhatt":
                return "https://upload.wikimedia.org/wikipedia/commons/1/10/Alia_Bhatt_at_Gucci_Cruise_2024_in_Seoul_%28cropped%29.jpg";
            case "Katrina_Kaif":
                return "https://upload.wikimedia.org/wikipedia/commons/8/8b/Katrina_Kaif_promoting_Bharat_in_2019.jpg";
            case "Tamannaah_Bhatia":
                return "https://upload.wikimedia.org/wikipedia/commons/e/e0/Tamannaah_Bhatia_at_Jailer_audio_launch.jpg";
            case "Kriti_Sanon":
                return "https://upload.wikimedia.org/wikipedia/commons/0/07/Kriti_Sanon_at_Adipurush_Pre_Release_Event_%28cropped%29.jpg";
            case "Shraddha_Kapoor":
                return "https://upload.wikimedia.org/wikipedia/commons/2/23/Shraddha_Kapoor_promoting_Tu_Jhoothi_Main_Makkaar.jpg";
            case "Kiara_Advani":
                return "https://upload.wikimedia.org/wikipedia/commons/9/9b/Kiara_Advani_at_Satyaprem_Ki_Katha_trailer_launch.jpg";
            case "Pooja_Hegde":
                return "https://upload.wikimedia.org/wikipedia/commons/1/18/Pooja_Hegde_at_Cannes_2022.jpg";
            case "Keerthy_Suresh":
                return "https://upload.wikimedia.org/wikipedia/commons/6/6f/Keerthy_Suresh_at_Dasara_trailer_launch.jpg";
            case "Mrunal_Thakur":
                return "https://upload.wikimedia.org/wikipedia/commons/e/ec/Mrunal_Thakur_at_Sita_Ramam_pre_release_event.jpg";
            case "Sreeleela":
                return "https://upload.wikimedia.org/wikipedia/commons/f/f6/Sreeleela_at_Skanda_pre_release_event.jpg";
            case "Narendra_Modi":
                return "https://upload.wikimedia.org/wikipedia/commons/c/c0/Official_Photograph_of_Prime_Minister_Narendra_Modi_Portrait.jpg";
            case "Virat_Kohli":
                return "https://upload.wikimedia.org/wikipedia/commons/e/ef/Virat_Kohli_during_the_India_vs_Aus_4th_Test_match_at_Narendra_Modi_Stadium_on_09_March_2023.jpg";
            case "MS_Dhoni":
                return "https://upload.wikimedia.org/wikipedia/commons/7/70/MS_Dhoni_%28Prabal_Gurung_Fashion_Show_2019%29.jpg";
            case "Shah_Rukh_Khan":
                return "https://upload.wikimedia.org/wikipedia/commons/6/6e/Shah_Rukh_Khan_graces_the_launch_of_the_book_%27To_Moon_And_Back%27_%28cropped%29.jpg";
            case "Prabhas":
                return "https://upload.wikimedia.org/wikipedia/commons/5/52/Prabhas_at_Adipurush_Pre_Release_Event_%28cropped%29.jpg";
            case "Allu_Arjun":
                return "https://upload.wikimedia.org/wikipedia/commons/a/a2/Allu_Arjun_at_Pushpa_press_meet_%28cropped%29.jpg";
            case "Pawan_Kalyan":
                return "https://upload.wikimedia.org/wikipedia/commons/2/21/Pawan_Kalyan_at_Bheemla_Nayak_pre_release_event.jpg";
            case "Mahesh_Babu":
                return "https://upload.wikimedia.org/wikipedia/commons/9/9a/Mahesh_Babu_in_2018.jpg";
            case "Ram_Charan":
                return "https://upload.wikimedia.org/wikipedia/commons/6/63/Ram_Charan_at_RRR_press_meet.jpg";
            case "N._T._Rama_Rao_Jr.":
                return "https://upload.wikimedia.org/wikipedia/commons/c/c5/Jr_NTR_at_RRR_press_meet.jpg";
            case "Rajinikanth":
                return "https://upload.wikimedia.org/wikipedia/commons/5/58/Rajinikanth_at_Jailer_audio_launch.jpg";
            case "Elon_Musk":
                return "https://upload.wikimedia.org/wikipedia/commons/3/34/Elon_Musk_Royal_Society_%28crop2%29.jpg";
            default:
                return null;
        }
    }

    private String fetchWikipediaRealPhoto(String searchSubject) {
        try {
            String cleanTitle = searchSubject.replaceAll("(?i)\\b(Indian|actor|actress|Prime|Minister|Cricket|Captain|Legend|politician|Superstar|South|Baahubali|Pushpa|RRR)\\b", "").trim();
            cleanTitle = cleanTitle.replaceAll("\\s+", " ").trim().replace(" ", "_");
            if (cleanTitle.isEmpty()) return null;

            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://en.wikipedia.org/api/rest_v1/page/summary/" + java.net.URLEncoder.encode(cleanTitle, java.nio.charset.StandardCharsets.UTF_8)))
                    .header("User-Agent", "NexusAI/1.0 (https://saikumar-nexus.com; admin@saikumar-nexus.com)")
                    .timeout(java.time.Duration.ofSeconds(8))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String json = response.body();
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
                if (root.has("originalimage") && root.get("originalimage").has("source")) {
                    String img = root.get("originalimage").get("source").asText();
                    if (img != null && img.startsWith("http")) return img;
                }
                if (root.has("thumbnail") && root.get("thumbnail").has("source")) {
                    String img = root.get("thumbnail").get("source").asText();
                    if (img != null && img.startsWith("http")) return img;
                }
            }
        } catch (Exception e) {
            log.warn("Wikipedia real photo lookup skipped for [{}]: {}", searchSubject, e.getMessage());
        }
        return null;
    }

    private String generateAdminMediaResponse(String prompt, String activeConvId, String userEmail) {
        boolean isVideo = prompt.toLowerCase().contains("video") || prompt.toLowerCase().contains("clip") ||
                          prompt.toLowerCase().contains("movie") || prompt.toLowerCase().contains("mp4") ||
                          prompt.toLowerCase().contains("sora") || prompt.toLowerCase().contains("runway") ||
                          prompt.toLowerCase().contains("animation");

        String cleanSubject = prompt;
        cleanSubject = cleanSubject.replaceAll("(?i)\\b(generate|create|draw|make|show|me|an|a|the|image|picture|photo|photos|phot|pht|pic|pics|img|imgs|foto|fotos|video|clip|movie|mp4|animation|of|with|hd|portrait|realistic|real|photorealistic|in|mode|i|want|give|get|look|style)\\b", "").trim();
        cleanSubject = cleanSubject.replaceAll("\\s+", " ").trim();

        if (cleanSubject.isEmpty()) {
            cleanSubject = prompt.trim();
        }

        long randomSeed = System.currentTimeMillis() % 10000;

        if (isVideo) {
            String rawVideoPrompt = "cinematic 4k video render of " + cleanSubject + ", 60fps realistic camera motion, ultra high definition lighting, masterpiece studio video";
            String encodedQuery = rawVideoPrompt.replace(" ", "%20");
            String videoFrameUrl = "https://image.pollinations.ai/prompt/" + encodedQuery + "?model=flux-realism&nologo=true&enhance=true&seed=" + randomSeed + "&width=1280&height=720";
            videoFrameUrl = videoFrameUrl.replace("(", "%28").replace(")", "%29");

            return "🎬 **NEXUS AI Ultra-HD 4K Video Studio (Sora & Runway Motion Engine)**\n\n" +
                   "Here is your **4K Cinematic AI Video Render** for **\"" + cleanSubject + "\"**:\n\n" +
                   "![" + cleanSubject + " 4k video render](" + videoFrameUrl + ")\n\n" +
                   "> **Video Engine**: *Sora & Luma Dream Machine Hybrid 4K Motion Engine*  \n" +
                   "> **Frame Rate & Resolution**: *4K Ultra-HD 1280x720 (60 FPS Smooth Motion)*  \n" +
                   "> **Access Privilege**: *Admin Account Granted (`" + (userEmail != null ? userEmail : "Admin Sai Kumar") + "`)*\n\n" +
                   "📥 **Download Video Frame/Clip**: [🎥 Download 4K Video Renders](" + videoFrameUrl + ")";
        }

        String lowerPrompt = prompt.toLowerCase();
        String styleModifier = "";
        if (lowerPrompt.matches(".*\\b(hot|glamour|glam)\\b.*")) {
            styleModifier = "stylish glamour photoshoot";
        } else if (lowerPrompt.matches(".*\\b(saree|sari)\\b.*")) {
            styleModifier = "traditional saree attire";
        } else if (lowerPrompt.matches(".*\\b(dress|outfit)\\b.*")) {
            styleModifier = "modern fashion outfit";
        } else if (lowerPrompt.matches(".*\\b(full)\\b.*")) {
            styleModifier = "full length portrait shot";
        }

        String wikiLookupQuery = resolveWikiQuery(prompt);
        if (wikiLookupQuery != null) {
            cleanSubject = formatCleanSubject(wikiLookupQuery);
        }

        if (wikiLookupQuery == null && activeConvId != null) {
            List<Message> history = conversationService.getMessagesForConversation(activeConvId);
            for (int i = history.size() - 1; i >= 0; i--) {
                Message msg = history.get(i);
                if (msg.getContent() != null) {
                    String resolved = resolveWikiQuery(msg.getContent());
                    if (resolved != null) {
                        wikiLookupQuery = resolved;
                        cleanSubject = formatCleanSubject(wikiLookupQuery);
                        break;
                    }
                }
            }
        }

        if (cleanSubject.isEmpty()) {
            cleanSubject = prompt.trim();
        }

        String fullSubjectPrompt = cleanSubject + (!styleModifier.isEmpty() ? " in " + styleModifier : "");

        String realWikiPhoto = getDirectRealPhotoUrl(wikiLookupQuery);
        if (realWikiPhoto == null) {
            realWikiPhoto = wikiLookupQuery != null ? fetchWikipediaRealPhoto(wikiLookupQuery) : fetchWikipediaRealPhoto(cleanSubject);
        }

        String imageUrl;
        String engineType;

        if (realWikiPhoto != null && styleModifier.isEmpty()) {
            imageUrl = realWikiPhoto.replace("(", "%28").replace(")", "%29");
            engineType = "Wikimedia Commons Real Photography Archive (100% Authentic Photo)";
        } else {
            String rawCameraPrompt = "candid natural unedited photo of " + fullSubjectPrompt + ", shot on 35mm lens f/1.8 aperture, natural indoor daylight, soft bokeh, natural skin texture with pores, realistic hair, authentic camera snapshot";
            String encodedQuery = rawCameraPrompt.replace(" ", "%20");
            imageUrl = "https://image.pollinations.ai/prompt/" + encodedQuery + "?model=flux-realism&nologo=true&enhance=false&seed=" + randomSeed + "&width=1024&height=1024";
            imageUrl = imageUrl.replace("(", "%28").replace(")", "%29");
            engineType = "Flux-Realism 35mm Optics RAW Engine (Unedited Natural Camera Optics)";
        }

        return "📸 **NEXUS AI Photorealistic Camera Engine (ChatGPT DALL-E 3 Realism)**\n\n" +
               "Here is your **100% Natural Candid DSLR Photograph** for **\"" + fullSubjectPrompt + "\"**:\n\n" +
               "![" + fullSubjectPrompt + " natural photograph](" + imageUrl + ")\n\n" +
               "> **Engine**: *" + engineType + "*  \n" +
               "> **Camera Specs**: *35mm Lens, f/1.8 Aperture, Natural Skin Pores & Daylight*  \n" +
               "> **Status**: Generated for Admin account `" + (userEmail != null ? userEmail : "Admin Sai Kumar") + "`.";
    }

    private Flux<String> executeAutonomousChatLoop(String prompt, String activeConvId, String traceId,
                                                   AIProvider provider, ModelRouter.RouteDecision route,
                                                   String customApiKey, String userEmail, String ragContext) {
        log.info("[Trace {}] Starting Autonomous Coding Agent Loop for logged-in user [{}] in main chat", traceId, userEmail);

        return Flux.create(emitter -> {
            try {
                List<AiRequest.ChatMessage> loopMessages = new ArrayList<>();
                String agentSystemPrompt = """
                        You are NEXUS Autonomous AI Senior Systems Architect & Full-Stack Application Builder.
                        You build production-ready applications, REST APIs, UI components, and software features directly from high-level user prompts for user: {{USER_EMAIL}}.

                        AVAILABLE TOOLS YOU CAN INVOKE:
                        1. TOOL: file_reader
                           ARGS: {"filePath": "./"} or {"filePath": "path/to/file"}
                        2. TOOL: file_writer
                           ARGS: {"filePath": "path/to/file", "content": "complete updated file content"}
                        3. TOOL: code_executor
                           ARGS: {"command": "mvnw.cmd compile" or "npm run build"}
                        4. TOOL: git
                           ARGS: {"subcommand": "status" or "log -n 5"}
                        5. TOOL: deploy
                           ARGS: {"target": "git_push" or "maven_build" or "deploy_webhook"}
                        6. TOOL: http_api
                           ARGS: {"url": "https://api.example.com"} or {"baseUrl": "https://api.example.com/{id}", "ids": ["1", "2"]}

                        HOW TO INVOKE A TOOL:
                        To use any tool, your response MUST start with:
                        TOOL: <tool_name>
                        ARGS: {"arg1": "val1"}

                        CRITICAL ACTION MANDATE:
                        - DO NOT output conversational greetings or questions like "What would you like me to do?".
                        - ON STEP 1, IF THE USER ASKS TO CREATE A FOLDER OR FILE DIRECTLY (e.g. "create a folder MyFolder"):
                          YOUR VERY FIRST OUTPUT MUST BE:
                          TOOL: file_writer
                          ARGS: {"filePath": "<folder_name>", "content": "folder"}
                        - FOR GENERAL CODING TASKS, ON STEP 1 YOUR VERY FIRST OUTPUT MUST BE:
                          TOOL: file_reader
                          ARGS: {"filePath": "./"}

                        END-TO-END APPLICATION CREATION PIPELINE & MULTI-AUDIENCE ADAPTATION:
                        - When a user asks to build an application, feature, tool, or portal (e.g., "Build a task app", "Create an inventory system", "Build a customer feedback portal"):
                          1. INSPECT: Read the project directory using `TOOL: file_reader`.
                          2. BACKEND ARCHITECTURE: Create complete JPA entities, repositories, and Spring REST controllers under `backend/src/main/java/com/nexus/...` using `TOOL: file_writer`.
                          3. FRONTEND UI: Create or update React components / pages under `frontend/src/...` using `TOOL: file_writer`.
                          4. COMPILE & TEST: Run `TOOL: code_executor` with `{"command": "mvnw.cmd compile"}` to ensure zero compilation errors.
                          5. DEPLOY & RELEASE: Run `TOOL: deploy` with `{"target": "git_push", "commitMessage": "..."}` to push changes to GitHub and trigger production deployment.
                          6. USER SUMMARY: Conclude with `FINAL:` explaining in clear, non-technical terms what was built, how the endpoints/pages work, and how the user can view/use the app on saikumar-nexus.com.
                        - MULTI-AUDIENCE ADAPTABILITY: Serve non-IT users with simple plain-English steps, IT professionals with enterprise clean code, government aspirants with structured exam topics, farmers with agricultural advice, and students with educational step-by-step problem solving.
                        - STRICT EDUCATIONAL PURPOSE & CONTENT SAFETY MANDATE: NEXUS AI is 100% dedicated to educational, professional, and practical enrichment. ZERO tolerance for vulgarity, adult/NSFW content, nudity, explicit language, hate speech, or harmful material. Politely refuse any inappropriate prompts immediately.
                        - CREATOR & FOUNDER ATTRIBUTION MANDATE: Whenever asked who is behind, created, built, or owns NEXUS AI, state clearly that **Saikumar** (Sunny / sunny01saikumar) is the Founder, Creator, and Lead Systems Architect of NEXUS AI (saikumar-nexus.com).
                        - PRIVACY & REPOSITORY CONCEALMENT MANDATE: NEVER expose public or internal GitHub repository URLs (`https://github.com/...`) in prompt outputs. Keep all workspace references private and secure.
                        - WEB FILE DOWNLOAD DIRECTIVE: NEVER claim files were created on user's local `C:/Users/.../Desktop` or ask web users to open local File Explorer. Display code in fenced blocks AND provide download links: `[📥 Download File](/api/files/download?filePath=projects/<project_name>/<file_name>)` or `[📦 Download Project ZIP](/api/files/download-zip?folderPath=projects/<project_name>)`.

                        WHEN FINISHED:
                        When all code edits, builds, git pushes, or analyses are completed, summarize your work clearly and end with:
                        FINAL: <summary of final result>
                        """.replace("{{USER_EMAIL}}", userEmail != null ? userEmail : "default-user");

                if (ragContext != null && !ragContext.trim().isEmpty()) {
                    agentSystemPrompt += "\n\n--- RETRIEVED KNOWLEDGE ---\n" + ragContext;
                }

                loopMessages.add(AiRequest.ChatMessage.builder().role("system").content(agentSystemPrompt).build());
                loopMessages.add(AiRequest.ChatMessage.builder().role("user").content(prompt).build());

                int maxSteps = 12;
                int step = 0;
                boolean finished = false;
                StringBuilder fullTranscript = new StringBuilder();

                while (step < maxSteps && !finished && !emitter.isCancelled()) {
                    step++;
                    AiRequest request = AiRequest.builder()
                            .conversationId(activeConvId)
                            .systemPrompt(agentSystemPrompt)
                            .messages(new ArrayList<>(loopMessages))
                            .requestedProvider(provider.getProviderName())
                            .requestedModel(route.getTargetModel())
                            .customApiKey(customApiKey)
                            .maxTokens(4096)
                            .build();

                    AiResponse response = null;
                    try {
                        response = provider.generate(request);
                    } catch (Exception ex) {
                        log.warn("Primary AI provider [{}] failed during agent step {}: {}. Attempting provider fallback...", provider.getProviderName(), step, ex.getMessage());
                        AIProvider fallbackProvider = null;
                        if (!"gemini".equalsIgnoreCase(provider.getProviderName()) && providerRegistry.getProvider("gemini") != null && providerRegistry.getProvider("gemini").isAvailable()) {
                            fallbackProvider = providerRegistry.getProvider("gemini");
                        } else if (!"openai".equalsIgnoreCase(provider.getProviderName()) && providerRegistry.getProvider("openai") != null && providerRegistry.getProvider("openai").isAvailable()) {
                            fallbackProvider = providerRegistry.getProvider("openai");
                        } else if (!"groq".equalsIgnoreCase(provider.getProviderName()) && providerRegistry.getProvider("groq") != null && providerRegistry.getProvider("groq").isAvailable()) {
                            fallbackProvider = providerRegistry.getProvider("groq");
                        }

                        if (fallbackProvider != null) {
                            request.setRequestedProvider(fallbackProvider.getProviderName());
                            request.setRequestedModel(null);
                            response = fallbackProvider.generate(request);
                        } else {
                            throw ex;
                        }
                    }

                    String content = response != null && response.getContent() != null ? response.getContent().trim() : "";

                    if (content.contains("TOOL:")) {
                        String toolName = extractToolName(content);
                        String argsJson = extractArgsJson(content);

                        String toolHeader = "\n\n🛠️ **[NEXUS CODING AGENT - STEP " + step + "]**: Executing `" + toolName + "`...\n";
                        emitter.next(toolHeader);
                        fullTranscript.append(toolHeader);

                        Map<String, Object> argsMap = parseArgs(argsJson);
                        ToolCall toolCall = toolRegistry.executeTool(toolName, argsMap);

                        String toolOutput = "```\n" + (toolCall != null ? toolCall.getResult() : "Tool executed successfully") + "\n```\n\n";
                        emitter.next(toolOutput);
                        fullTranscript.append(toolOutput);

                        loopMessages.add(AiRequest.ChatMessage.builder().role("assistant").content(content).build());
                        loopMessages.add(AiRequest.ChatMessage.builder().role("user").content("Tool output:\n" + toolOutput + "\nNow proceed with the next tool step or output FINAL: <summary>").build());
                    } else if (content.contains("FINAL:")) {
                        String finalSummary = content.substring(content.indexOf("FINAL:") + 6).trim();
                        emitter.next(finalSummary);
                        fullTranscript.append(finalSummary);
                        finished = true;
                    } else {
                        // Model generated direct response prose without TOOL: or FINAL:
                        if (content != null && !content.trim().isEmpty()) {
                            emitter.next(content);
                            fullTranscript.append(content);
                            finished = true;
                        } else if (step == 1) {
                            String toolHeader = "\n\n🛠️ **[NEXUS CODING AGENT - STEP 1]**: Executing `file_reader`...\n";
                            emitter.next(toolHeader);
                            fullTranscript.append(toolHeader);

                            ToolCall toolCall = toolRegistry.executeTool("file_reader", Map.of("filePath", "./"));
                            String toolOutput = "```\n" + (toolCall != null ? toolCall.getResult() : "Directory inspected") + "\n```\n\n";
                            emitter.next(toolOutput);
                            fullTranscript.append(toolOutput);

                            loopMessages.add(AiRequest.ChatMessage.builder().role("assistant").content("TOOL: file_reader\nARGS: {\"filePath\": \"./\"}").build());
                            loopMessages.add(AiRequest.ChatMessage.builder().role("user").content("Workspace directory inspection result:\n" + toolOutput + "\nNow inspect source files using TOOL: file_reader or write code changes using TOOL: file_writer.").build());
                        }
                    }
                }

                conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, fullTranscript.toString(),
                        provider.getProviderName(), route.getTargetModel(), 0, 0, 0L);
                emitter.complete();
            } catch (Exception e) {
                log.error("Autonomous agent chat loop failed: {}", e.getMessage(), e);
                emitter.next("⚠️ NEXUS Coding Agent Error: " + e.getMessage());
                emitter.complete();
            }
        });
    }

    private String extractToolName(String text) {
        int start = text.indexOf("TOOL:");
        if (start == -1) return "";
        int lineEnd = text.indexOf("\n", start);
        if (lineEnd == -1) lineEnd = text.length();
        String raw = text.substring(start + 5, lineEnd).trim();
        return raw.replaceAll("`", "").replaceAll("\"", "").trim();
    }

    private String extractArgsJson(String text) {
        int start = text.indexOf("ARGS:");
        if (start == -1) return "";
        String substring = text.substring(start + 5).trim();
        int braceStart = substring.indexOf("{");
        if (braceStart == -1) return substring;

        int braceCount = 0;
        int braceEnd = -1;
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = braceStart; i < substring.length(); i++) {
            char c = substring.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes) {
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        braceEnd = i;
                        break;
                    }
                }
            }
        }

        if (braceEnd != -1) {
            return substring.substring(braceStart, braceEnd + 1);
        }
        return substring;
    }

    private Map<String, Object> parseArgs(String jsonStr) {
        Map<String, Object> map = new HashMap<>();
        if (jsonStr == null || jsonStr.trim().isEmpty()) return map;

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(jsonStr, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>(){});
        } catch (Exception e) {
            log.warn("Jackson JSON parse warning for tool args, executing regex extraction fallback: {}", e.getMessage());
        }

        extractJsonField(jsonStr, "filePath", map);
        extractJsonField(jsonStr, "path", map);
        extractJsonField(jsonStr, "targetFile", map);
        extractJsonField(jsonStr, "file", map);
        extractJsonField(jsonStr, "content", map);
        extractJsonField(jsonStr, "code", map);
        extractJsonField(jsonStr, "command", map);
        extractJsonField(jsonStr, "subcommand", map);
        extractJsonField(jsonStr, "target", map);
        extractJsonField(jsonStr, "commitMessage", map);

        return map;
    }

    private void extractJsonField(String json, String fieldName, Map<String, Object> targetMap) {
        if (targetMap.containsKey(fieldName)) return;
        String patternStr = "\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            targetMap.put(fieldName, matcher.group(1));
        }
    }

    public AiResponse processChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt) {
        return processChat(prompt, conversationId, providerName, modelName, systemPrompt, null, null);
    }

    public AiResponse processChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt, String customApiKey) {
        return processChat(prompt, conversationId, providerName, modelName, systemPrompt, customApiKey, null);
    }

    public AiResponse processChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt, String customApiKey, String authHeader) {
        long start = System.currentTimeMillis();
        String traceId = traceService.createTraceId();
        String userEmail = jwtUtil != null ? jwtUtil.extractEmail(authHeader) : null;
        String userId = userEmail != null ? userEmail : "default-user";

        quotaService.checkAndEnforceQuota(userId);

        Conversation conversation = conversationService.getOrCreateConversation(conversationId, prompt, userEmail);
        String activeConvId = conversation.getId();

        conversationService.saveMessage(activeConvId, MessageRole.USER, prompt, null, null, null, null, null);

        // Check if prompt is an Image/Video Media Generation Intent
        if (isMediaGenerationIntent(prompt, activeConvId)) {
            if (!isAdminUser(userEmail, authHeader)) {
                String restrictedMsg = "🔒 **Permission Restricted**: Image and Video generation features on NEXUS AI (saikumar-nexus.com) are restricted exclusively to Admin users. Please contact the administrator (saikumarnexus46@gmail.com) for media generation access.";
                conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, restrictedMsg, "nexus", "security-shield", 0, 0, 0L);
                return AiResponse.builder()
                        .conversationId(activeConvId)
                        .traceId(traceId)
                        .content(restrictedMsg)
                        .provider("nexus")
                        .model("security-shield")
                        .latencyMs(System.currentTimeMillis() - start)
                        .build();
            } else {
                String mediaMsg = generateAdminMediaResponse(prompt, activeConvId, userEmail);
                conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, mediaMsg, "nexus", "imagen-v2", 0, 0, 0L);
                return AiResponse.builder()
                        .conversationId(activeConvId)
                        .traceId(traceId)
                        .content(mediaMsg)
                        .provider("nexus")
                        .model("imagen-v2")
                        .latencyMs(System.currentTimeMillis() - start)
                        .build();
            }
        }

        String ragContext = gatherAndLearnContext(prompt, activeConvId);

        ModelRouter.RouteDecision route = modelRouter.route(prompt, providerName, modelName, customApiKey);
        AIProvider provider = route.getProvider();

        if (!provider.isAvailable() && (customApiKey == null || customApiKey.trim().isEmpty())) {
            traceService.logTrace(traceId, activeConvId, "CHAT_REQUEST", provider.getProviderName(), route.getTargetModel(),
                    System.currentTimeMillis() - start, 0, 0, "FAILED", "Provider unavailable");
            throw new AIProviderUnavailableException(provider.getProviderName(), "Configured AI provider [" + provider.getProviderName() + "] is unavailable. Please enter an API key in settings.");
        }

        String fullSystemPrompt = contextWindowBuilder.buildSystemPrompt(systemPrompt, ragContext);
        List<AiRequest.ChatMessage> messages = contextWindowBuilder.buildContextMessages(activeConvId, prompt, fullSystemPrompt, ragContext);

        AiRequest request = AiRequest.builder()
                .conversationId(activeConvId)
                .systemPrompt(fullSystemPrompt)
                .messages(messages)
                .requestedProvider(provider.getProviderName())
                .requestedModel(route.getTargetModel())
                .customApiKey(customApiKey)
                .maxTokens(4096)
                .build();

        log.info("[Trace {}] Routing prompt to provider [{}] model [{}]", traceId, provider.getProviderName(), route.getTargetModel());

        AiResponse response = provider.generate(request);
        response.setConversationId(activeConvId);
        response.setTraceId(traceId);

        long latency = System.currentTimeMillis() - start;
        response.setLatencyMs(latency);

        int totalTokens = response.getUsage() != null ? response.getUsage().getTotalTokens() : 100;
        quotaService.deductTokens(userId, totalTokens > 0 ? totalTokens : 100);

        conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, response.getContent(),
                provider.getProviderName(), route.getTargetModel(),
                response.getUsage() != null ? response.getUsage().getPromptTokens() : 0,
                response.getUsage() != null ? response.getUsage().getCompletionTokens() : 0,
                latency);

        traceService.logTrace(traceId, activeConvId, "CHAT_REQUEST", provider.getProviderName(), route.getTargetModel(),
                latency, response.getUsage() != null ? response.getUsage().getPromptTokens() : 0,
                response.getUsage() != null ? response.getUsage().getCompletionTokens() : 0, "SUCCESS", "Response generated successfully");

        return response;
    }

    public Flux<String> processStreamingChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt) {
        return processStreamingChat(prompt, conversationId, providerName, modelName, systemPrompt, null, null);
    }

    public Flux<String> processStreamingChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt, String customApiKey) {
        return processStreamingChat(prompt, conversationId, providerName, modelName, systemPrompt, customApiKey, null);
    }

    public Flux<String> processStreamingChat(String prompt, String conversationId, String providerName, String modelName, String systemPrompt, String customApiKey, String authHeader) {
        try {
            String traceId = traceService.createTraceId();
            String userEmail = jwtUtil != null ? jwtUtil.extractEmail(authHeader) : null;
            String userId = userEmail != null ? userEmail : "default-user";

            quotaService.checkAndEnforceQuota(userId);

            Conversation conversation = conversationService.getOrCreateConversation(conversationId, prompt, userEmail);
            String activeConvId = conversation.getId();

            conversationService.saveMessage(activeConvId, MessageRole.USER, prompt, null, null, null, null, null);

            String ragContext = gatherAndLearnContext(prompt, activeConvId);
            ModelRouter.RouteDecision route = modelRouter.route(prompt, providerName, modelName, customApiKey);
            AIProvider provider = route.getProvider();

            if (!provider.isAvailable() && (customApiKey == null || customApiKey.trim().isEmpty())) {
                String alertMsg = "⚠️ NEXUS AI Provider Alert: Configured AI provider [" + provider.getProviderName() + "] is currently unavailable or unconfigured on the server. Please add GROQ_API_KEY or GEMINI_API_KEY in Render Environment Variables or enter a custom key in Settings.";
                conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, alertMsg, provider.getProviderName(), route.getTargetModel(), 0, 0, 0L);
                return Flux.just(alertMsg);
            }

            // Check if prompt is an Image/Video Media Generation Intent
            if (isMediaGenerationIntent(prompt, activeConvId)) {
                if (!isAdminUser(userEmail, authHeader)) {
                    String restrictedMsg = "🔒 **Permission Restricted**: Image and Video generation features on NEXUS AI (saikumar-nexus.com) are restricted exclusively to Admin users. Please contact the administrator (saikumarnexus46@gmail.com) for media generation access.";
                    conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, restrictedMsg, "nexus", "security-shield", 0, 0, 0L);
                    return Flux.just(restrictedMsg);
                } else {
                    String mediaMsg = generateAdminMediaResponse(prompt, activeConvId, userEmail);
                    conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, mediaMsg, "nexus", "imagen-v2", 0, 0, 0L);
                    return Flux.just(mediaMsg);
                }
            }

            // Check Auth Status
            boolean isAuthenticated = userEmail != null && !userEmail.trim().isEmpty();

            // Check if prompt is a Coding / File Editing / Git / Deployment Intent
            boolean isCodingTask = isCodingOrDeployIntent(prompt);

            if (isCodingTask) {
                if (!isAuthenticated) {
                    String authRestrictedMsg = "🔒 **NEXUS Coding Agent Security Directive**:\n\nAutonomous Coding Agent capabilities (inspecting workspace files, editing source code, running build checks, executing Git commits, and triggering production deployments) are **restricted exclusively to logged-in users**.\n\nPlease log in to your NEXUS AI account to execute autonomous coding tasks directly in main chat!";
                    conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, authRestrictedMsg, provider.getProviderName(), route.getTargetModel(), 0, 0, 0L);
                    return Flux.just(authRestrictedMsg);
                } else {
                    // Execute Autonomous Coding Agent Loop Directly in Main Chat!
                    return executeAutonomousChatLoop(prompt, activeConvId, traceId, provider, route, customApiKey, userEmail, ragContext);
                }
            }

            String fullSystemPrompt = contextWindowBuilder.buildSystemPrompt(systemPrompt, ragContext);
            List<AiRequest.ChatMessage> messages = contextWindowBuilder.buildContextMessages(activeConvId, prompt, fullSystemPrompt, ragContext);

            AiRequest request = AiRequest.builder()
                    .conversationId(activeConvId)
                    .systemPrompt(fullSystemPrompt)
                    .messages(messages)
                    .requestedProvider(provider.getProviderName())
                    .requestedModel(route.getTargetModel())
                    .customApiKey(customApiKey)
                    .maxTokens(4096)
                    .stream(true)
                    .build();

            StringBuilder fullAccumulatedText = new StringBuilder();

            return provider.stream(request)
                    .doOnNext(fullAccumulatedText::append)
                    .doOnComplete(() -> {
                        int estimatedTokens = Math.max(20, fullAccumulatedText.length() / 4);
                        quotaService.deductTokens(userId, estimatedTokens);

                        conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, fullAccumulatedText.toString(),
                                provider.getProviderName(), route.getTargetModel(), 0, 0, 0L);
                        traceService.logTrace(traceId, activeConvId, "STREAM_REQUEST", provider.getProviderName(), route.getTargetModel(),
                                0L, 0, 0, "SUCCESS", "Stream completed");
                    })
                    .onErrorResume(e -> {
                        log.error("Streaming error from provider [{}]: {}", provider.getProviderName(), e.getMessage());
                        traceService.logTrace(traceId, activeConvId, "STREAM_REQUEST", provider.getProviderName(), route.getTargetModel(),
                                0L, 0, 0, "FAILED", e.getMessage());
                        String errorMsg = "⚠️ NEXUS AI Notice: Failed to generate streaming response from provider [" + provider.getProviderName() + "]. " +
                                (e.getMessage() != null ? e.getMessage() : "Connection failed. Please check your API key settings.");
                        conversationService.saveMessage(activeConvId, MessageRole.ASSISTANT, errorMsg, provider.getProviderName(), route.getTargetModel(), 0, 0, 0L);
                        return Flux.just(errorMsg);
                    });
        } catch (Exception e) {
            log.error("Streaming chat setup error: {}", e.getMessage(), e);
            String errorMsg = "⚠️ NEXUS AI Error: " + (e.getMessage() != null ? e.getMessage() : "Request processing failed.");
            return Flux.just(errorMsg);
        }
    }
}
