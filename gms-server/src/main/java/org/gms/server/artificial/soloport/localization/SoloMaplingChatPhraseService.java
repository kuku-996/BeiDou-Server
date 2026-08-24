package org.gms.server.artificial.soloport.localization;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.gms.exception.BizException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.gms.server.artificial.soloport.localization.SoloMaplingChineseLocalization.PhraseCategory;

/** Persistent, hot-reloadable categorized phrase books edited from the BeiDou web console. */
@Slf4j
@Service
public class SoloMaplingChatPhraseService {
    private static final int MIN_PHRASES = 10;
    private static final int MAX_PHRASES = 500;
    private static final int MAX_LINE_LENGTH = 80;

    private final Path configDirectory = Path.of(System.getProperty("user.dir"), "config", "solomapling-bot-phrases-zh");
    private final Path legacyPhraseFile = Path.of(System.getProperty("user.dir"), "config", "solomapling-bot-chat-zh.txt");

    @PostConstruct
    public void loadOnStartup() {
        try {
            Map<PhraseCategory, List<String>> phraseBook = new EnumMap<>(PhraseCategory.class);
            for (PhraseCategory category : PhraseCategory.values()) {
                Path phraseFile = getPhraseFile(category);
                if (Files.exists(phraseFile)) {
                    phraseBook.put(category, normalize(Files.readAllLines(phraseFile, StandardCharsets.UTF_8)));
                } else if (category == PhraseCategory.GENERAL && Files.exists(legacyPhraseFile)) {
                    phraseBook.put(category, normalize(Files.readAllLines(legacyPhraseFile, StandardCharsets.UTF_8)));
                    log.info("Migrated legacy SoloMapling chat phrases from {}.", legacyPhraseFile);
                }
            }
            SoloMaplingChineseLocalization.setPhraseBook(phraseBook);
            log.info("Loaded editable SoloMapling Chinese phrase books from {}.", configDirectory);
        } catch (Exception e) {
            log.warn("Unable to load editable SoloMapling phrase books; using built-in phrases.", e);
        }
    }

    /** Legacy API kept for a safe transition from the former single text area. */
    public List<String> getPhrases() {
        return SoloMaplingChineseLocalization.getBotLines();
    }

    /** Legacy API kept for a safe transition from the former single text area. */
    public synchronized List<String> savePhrases(List<String> phrases) {
        List<String> normalized = validateAndNormalize(phrases, PhraseCategory.GENERAL);
        try {
            saveCategory(PhraseCategory.GENERAL, normalized);
            SoloMaplingChineseLocalization.setPhraseLines(PhraseCategory.GENERAL, normalized);
            return normalized;
        } catch (IOException e) {
            throw new BizException("保存机器人聊天内容失败：" + e.getMessage());
        }
    }

    public Map<String, List<String>> getPhraseBook() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (PhraseCategory category : PhraseCategory.values()) {
            result.put(toApiKey(category), SoloMaplingChineseLocalization.getPhraseLines(category));
        }
        return result;
    }

    public synchronized Map<String, List<String>> savePhraseBook(Map<String, List<String>> phraseBook) {
        Map<PhraseCategory, List<String>> normalized = new EnumMap<>(PhraseCategory.class);
        for (PhraseCategory category : PhraseCategory.values()) {
            List<String> source = phraseBook == null ? null : phraseBook.get(toApiKey(category));
            normalized.put(category, validateAndNormalize(source, category));
        }
        try {
            for (Map.Entry<PhraseCategory, List<String>> entry : normalized.entrySet()) {
                saveCategory(entry.getKey(), entry.getValue());
            }
            SoloMaplingChineseLocalization.setPhraseBook(normalized);
            log.info("Updated {} categorized SoloMapling Chinese phrase books.", normalized.size());
            return getPhraseBook();
        } catch (IOException e) {
            throw new BizException("保存机器人分类词库失败：" + e.getMessage());
        }
    }

    private List<String> validateAndNormalize(List<String> phrases, PhraseCategory category) {
        List<String> normalized = normalize(phrases);
        if (normalized.size() < MIN_PHRASES) {
            throw BizException.illegalArgument("“" + getCategoryName(category) + "”至少保留 "
                    + MIN_PHRASES + " 条不同内容。");
        }
        return normalized;
    }

    private void saveCategory(PhraseCategory category, List<String> phrases) throws IOException {
        Files.createDirectories(configDirectory);
        Files.writeString(getPhraseFile(category), String.join(System.lineSeparator(), phrases) + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private Path getPhraseFile(PhraseCategory category) {
        return configDirectory.resolve(toApiKey(category) + ".txt");
    }

    private String toApiKey(PhraseCategory category) {
        return category.name().toLowerCase();
    }

    private String getCategoryName(PhraseCategory category) {
        return switch (category) {
            case GENERAL -> "普通聊天";
            case CHALKBOARD -> "黑板";
            case MEGAPHONE -> "喇叭";
            case TRADE -> "交易";
        };
    }

    private List<String> normalize(List<String> source) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (source != null) {
            for (String phrase : source) {
                if (phrase == null) continue;
                String value = phrase.trim();
                if (!value.isEmpty()) unique.add(value.length() > MAX_LINE_LENGTH ? value.substring(0, MAX_LINE_LENGTH) : value);
                if (unique.size() == MAX_PHRASES) break;
            }
        }
        return new ArrayList<>(unique);
    }
}
