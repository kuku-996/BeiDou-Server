package org.gms.server.artificial.soloport.localization;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.gms.exception.BizException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.gms.server.artificial.soloport.localization.SoloMaplingChineseLocalization.PhraseCategory;

/** Optional OpenAI-compatible text generation with timeout, circuit breaker and local fallback. */
@Slf4j
@Service
public class SoloMaplingAiSpeechService {
    private static final int MIN_TIMEOUT_MS = 500;
    private static final int MAX_TIMEOUT_MS = 10_000;
    private static final int FAILURE_LIMIT = 3;
    private static final long CIRCUIT_BREAK_MS = 60_000L;

    private final Path configFile = Path.of(System.getProperty("user.dir"), "config", "solomapling-ai-chat.json");
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long circuitOpenUntil;
    private volatile SoloMaplingAiConfig config = normalize(new SoloMaplingAiConfig(), null);

    @PostConstruct
    public void initialize() {
        if (Files.exists(configFile)) {
            try {
                SoloMaplingAiConfig loaded = JSON.parseObject(
                        Files.readString(configFile, StandardCharsets.UTF_8), SoloMaplingAiConfig.class);
                config = normalize(loaded, null);
                log.info("Loaded SoloMapling AI chat configuration (enabled={}, model={}).",
                        config.isEnabled(), config.getModel());
            } catch (Exception e) {
                log.warn("Unable to load SoloMapling AI chat configuration; AI chat remains disabled.", e);
            }
        }
        SoloMaplingChineseLocalization.setAiSpeechGenerator(this::generate);
    }

    public SoloMaplingAiConfig getClientConfig() {
        SoloMaplingAiConfig result = copy(config);
        result.setApiKeyConfigured(config.getApiKey() != null && !config.getApiKey().isBlank());
        result.setApiKey("");
        return result;
    }

    public synchronized SoloMaplingAiConfig saveClientConfig(SoloMaplingAiConfig submitted) {
        SoloMaplingAiConfig normalized = normalize(submitted, config);
        try {
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, JSON.toJSONString(normalized, JSONWriter.Feature.PrettyFormat),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            config = normalized;
            consecutiveFailures.set(0);
            circuitOpenUntil = 0;
            log.info("Updated SoloMapling AI chat configuration (enabled={}, model={}).",
                    normalized.isEnabled(), normalized.getModel());
            return getClientConfig();
        } catch (IOException e) {
            throw new BizException("保存机器人 AI 配置失败：" + e.getMessage());
        }
    }

    public String testConnection() {
        SoloMaplingAiConfig current = config;
        if (!current.isEnabled()) {
            throw BizException.illegalArgument("请先开启并保存 API 接口开关。");
        }
        String result = requestCompletion(current, PhraseCategory.GENERAL, "今天在射手村遇到了很多冒险家");
        if (result == null || result.isBlank()) {
            throw new BizException("API 未返回有效内容，请检查地址、密钥、模型和服务端日志。");
        }
        consecutiveFailures.set(0);
        circuitOpenUntil = 0;
        return result;
    }

    public String generate(PhraseCategory category, String sourceMessage) {
        SoloMaplingAiConfig current = config;
        if (!current.isEnabled() || System.currentTimeMillis() < circuitOpenUntil) {
            return null;
        }
        try {
            String generated = requestCompletion(current, category, sourceMessage);
            if (generated == null || generated.isBlank()) {
                recordFailure("empty response");
                return null;
            }
            consecutiveFailures.set(0);
            return sanitize(generated, category);
        } catch (Exception e) {
            recordFailure(e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    private String requestCompletion(SoloMaplingAiConfig current, PhraseCategory category, String sourceMessage) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", current.getModel());
            body.put("temperature", current.getTemperature());
            body.put("max_tokens", 120);

            JSONArray messages = new JSONArray();
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", current.getSystemPrompts().get(toApiKey(category)));
            messages.add(system);
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", "原始内容：" + sourceMessage);
            messages.add(user);
            body.put("messages", messages);

            HttpRequest.Builder builder = HttpRequest.newBuilder(resolveChatCompletionsUri(current.getEndpoint()))
                    .timeout(Duration.ofMillis(current.getTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8));
            if (current.getApiKey() != null && !current.getApiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + current.getApiKey());
            }
            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode());
            }
            JSONObject root = JSON.parseObject(response.body());
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) return null;
            JSONObject message = choices.getJSONObject(0).getJSONObject("message");
            return message == null ? null : message.getString("content");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("API request interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("API request failed", e);
        }
    }

    private void recordFailure(String reason) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= FAILURE_LIMIT) {
            circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_BREAK_MS;
            consecutiveFailures.set(0);
            log.warn("SoloMapling AI chat paused for 60 seconds after repeated failures: {}", reason);
        } else {
            log.debug("SoloMapling AI chat request failed ({}/{}): {}", failures, FAILURE_LIMIT, reason);
        }
    }

    private URI resolveChatCompletionsUri(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw BizException.illegalArgument("API 地址必须以 http:// 或 https:// 开头。");
        }
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (!value.endsWith("/chat/completions")) value += "/chat/completions";
        return URI.create(value);
    }

    private SoloMaplingAiConfig normalize(SoloMaplingAiConfig submitted, SoloMaplingAiConfig existing) {
        SoloMaplingAiConfig source = submitted == null ? new SoloMaplingAiConfig() : submitted;
        SoloMaplingAiConfig result = new SoloMaplingAiConfig();
        result.setEnabled(source.isEnabled());
        result.setEndpoint(blankToDefault(source.getEndpoint(), "https://api.openai.com/v1"));
        result.setModel(blankToDefault(source.getModel(), "gpt-4o-mini"));
        result.setTimeoutMs(Math.max(MIN_TIMEOUT_MS, Math.min(MAX_TIMEOUT_MS, source.getTimeoutMs())));
        result.setTemperature(Math.max(0.0, Math.min(2.0, source.getTemperature())));
        String submittedKey = source.getApiKey() == null ? "" : source.getApiKey().trim();
        result.setApiKey(submittedKey.isEmpty() && existing != null ? existing.getApiKey() : submittedKey);
        result.setApiKeyConfigured(result.getApiKey() != null && !result.getApiKey().isBlank());

        Map<String, String> prompts = SoloMaplingAiConfig.defaultSystemPrompts();
        if (source.getSystemPrompts() != null) {
            for (String key : prompts.keySet()) {
                String prompt = source.getSystemPrompts().get(key);
                if (prompt != null && !prompt.isBlank()) prompts.put(key, prompt.trim());
            }
        }
        result.setSystemPrompts(prompts);
        resolveChatCompletionsUri(result.getEndpoint());
        return result;
    }

    private SoloMaplingAiConfig copy(SoloMaplingAiConfig source) {
        SoloMaplingAiConfig result = new SoloMaplingAiConfig();
        result.setEnabled(source.isEnabled());
        result.setEndpoint(source.getEndpoint());
        result.setApiKey(source.getApiKey());
        result.setApiKeyConfigured(source.isApiKeyConfigured());
        result.setModel(source.getModel());
        result.setTimeoutMs(source.getTimeoutMs());
        result.setTemperature(source.getTemperature());
        result.setSystemPrompts(new LinkedHashMap<>(source.getSystemPrompts()));
        return result;
    }

    private String sanitize(String generated, PhraseCategory category) {
        String value = generated.replace('\r', ' ').replace('\n', ' ').trim();
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("“") && value.endsWith("”"))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        int maxLength = switch (category) {
            case GENERAL -> 80;
            case CHALKBOARD -> 50;
            case MEGAPHONE -> 52;
            case TRADE -> 60;
        };
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private String toApiKey(PhraseCategory category) {
        return category.name().toLowerCase();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
