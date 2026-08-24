package org.gms.server.artificial;

import org.gms.server.artificial.soloport.localization.SoloMaplingChineseLocalization;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoloMaplingChineseLocalizationTest {
    @Test
    void convertsImportedEnglishBotSpeechBeforeItReachesTheClient() {
        assertTrue(hasChinese(SoloMaplingChineseLocalization.botSpeech("lets farm at henesys today")));
        assertEquals("云朵碎片：12", SoloMaplingChineseLocalization.botSpeech("Cloud Pieces: 12"));
    }

    @Test
    void preservesExecutableGmCommandAndTranslatesItsDescription() {
        assertEquals("!move help - 显示本组管理命令帮助",
                SoloMaplingChineseLocalization.gmMessage("!move help - Bot movement commands"));
        assertEquals("未找到对应的机器人。", SoloMaplingChineseLocalization.gmMessage("Bot null"));
    }

    @Test
    void acceptsAnEditablePhrasePoolAtRuntime() {
        List<String> original = SoloMaplingChineseLocalization.getBotLines();
        try {
            SoloMaplingChineseLocalization.setBotLines(List.of("自定义句子一", "自定义句子二"));
            String line = SoloMaplingChineseLocalization.botSpeech("untranslated source line");
            assertTrue(line.equals("自定义句子一") || line.equals("自定义句子二"));
        } finally {
            SoloMaplingChineseLocalization.setBotLines(original);
        }
    }

    @Test
    void routesEachOutputTypeToItsOwnEditablePhrasePool() {
        List<String> originalGeneral = SoloMaplingChineseLocalization.getPhraseLines(
                SoloMaplingChineseLocalization.PhraseCategory.GENERAL);
        List<String> originalBoard = SoloMaplingChineseLocalization.getPhraseLines(
                SoloMaplingChineseLocalization.PhraseCategory.CHALKBOARD);
        List<String> originalMega = SoloMaplingChineseLocalization.getPhraseLines(
                SoloMaplingChineseLocalization.PhraseCategory.MEGAPHONE);
        List<String> originalTrade = SoloMaplingChineseLocalization.getPhraseLines(
                SoloMaplingChineseLocalization.PhraseCategory.TRADE);
        try {
            SoloMaplingChineseLocalization.setPhraseBook(Map.of(
                    SoloMaplingChineseLocalization.PhraseCategory.GENERAL, List.of("普通词库"),
                    SoloMaplingChineseLocalization.PhraseCategory.CHALKBOARD, List.of("黑板词库"),
                    SoloMaplingChineseLocalization.PhraseCategory.MEGAPHONE, List.of("喇叭词库"),
                    SoloMaplingChineseLocalization.PhraseCategory.TRADE, List.of("交易词库")
            ));
            assertEquals("普通词库", SoloMaplingChineseLocalization.botSpeech("plain english"));
            assertEquals("黑板词库", SoloMaplingChineseLocalization.botSpeech("board english",
                    SoloMaplingChineseLocalization.PhraseCategory.CHALKBOARD));
            assertEquals("喇叭词库", SoloMaplingChineseLocalization.botSpeech("mega english",
                    SoloMaplingChineseLocalization.PhraseCategory.MEGAPHONE));
            assertEquals("交易词库", SoloMaplingChineseLocalization.botSpeech("trade english",
                    SoloMaplingChineseLocalization.PhraseCategory.TRADE));
        } finally {
            SoloMaplingChineseLocalization.setPhraseBook(Map.of(
                    SoloMaplingChineseLocalization.PhraseCategory.GENERAL, originalGeneral,
                    SoloMaplingChineseLocalization.PhraseCategory.CHALKBOARD, originalBoard,
                    SoloMaplingChineseLocalization.PhraseCategory.MEGAPHONE, originalMega,
                    SoloMaplingChineseLocalization.PhraseCategory.TRADE, originalTrade
            ));
        }
    }

    @Test
    void usesOptionalAiGeneratorAndFallsBackWhenItReturnsNothing() {
        List<String> original = SoloMaplingChineseLocalization.getBotLines();
        try {
            SoloMaplingChineseLocalization.setBotLines(List.of("本地回退"));
            SoloMaplingChineseLocalization.setAiSpeechGenerator((category, source) ->
                    category == SoloMaplingChineseLocalization.PhraseCategory.MEGAPHONE ? "智能喇叭" : null);
            assertEquals("智能喇叭", SoloMaplingChineseLocalization.botSpeech("english mega",
                    SoloMaplingChineseLocalization.PhraseCategory.MEGAPHONE));
            assertEquals("本地回退", SoloMaplingChineseLocalization.botSpeech("english chat",
                    SoloMaplingChineseLocalization.PhraseCategory.GENERAL));
        } finally {
            SoloMaplingChineseLocalization.setAiSpeechGenerator(null);
            SoloMaplingChineseLocalization.setBotLines(original);
        }
    }

    private static boolean hasChinese(String value) {
        return value != null && value.codePoints().anyMatch(c -> c >= 0x3400 && c <= 0x9fff);
    }
}
