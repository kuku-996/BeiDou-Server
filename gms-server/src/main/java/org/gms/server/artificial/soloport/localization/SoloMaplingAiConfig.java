package org.gms.server.artificial.soloport.localization;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server-side settings for the optional OpenAI-compatible bot speech provider. */
public class SoloMaplingAiConfig {
    private boolean enabled;
    private String endpoint = "https://api.openai.com/v1";
    private String apiKey = "";
    private boolean apiKeyConfigured;
    private String model = "gpt-4o-mini";
    private int timeoutMs = 3000;
    private double temperature = 0.9;
    private Map<String, String> systemPrompts = defaultSystemPrompts();

    public static Map<String, String> defaultSystemPrompts() {
        Map<String, String> prompts = new LinkedHashMap<>();
        prompts.put("general", "你是GMS083冒险岛中的普通玩家。根据原始内容生成一句自然、简短、口语化的简体中文聊天，像真实玩家交流；不要解释，不要加引号，最多40个汉字。");
        prompts.put("chalkboard", "你是GMS083冒险岛玩家。根据原始内容生成一句适合角色黑板展示的简体中文内容，可用于组队、休息、交易或心情表达；不要解释，不要加引号，最多35个汉字。");
        prompts.put("megaphone", "你是GMS083冒险岛玩家。根据原始内容生成一句适合游戏喇叭广播的简体中文消息，可以招募、收购、出售或祝福；禁止现实货币交易、网址和外挂广告，不要解释，不要加引号，最多45个汉字。");
        prompts.put("trade", "你正在GMS083冒险岛交易窗口中与玩家交易。根据原始内容生成一句自然简短的简体中文交易回复；必须保留原文中的物品名、数量和金币数值，不要编造条件，不要解释，不要加引号，最多50个汉字。");
        return prompts;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isApiKeyConfigured() {
        return apiKeyConfigured;
    }

    public void setApiKeyConfigured(boolean apiKeyConfigured) {
        this.apiKeyConfigured = apiKeyConfigured;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public Map<String, String> getSystemPrompts() {
        return systemPrompts;
    }

    public void setSystemPrompts(Map<String, String> systemPrompts) {
        this.systemPrompts = systemPrompts;
    }
}
