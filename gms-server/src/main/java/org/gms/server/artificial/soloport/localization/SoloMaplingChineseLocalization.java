package org.gms.server.artificial.soloport.localization;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Presentation-only localisation for SoloMapling.  The imported dialogue data
 * remains intact, while every visible bot sentence and SoloMapling GM prompt is
 * made suitable for the Chinese client at its shared output boundary.
 */
public final class SoloMaplingChineseLocalization {
    @FunctionalInterface
    public interface AiSpeechGenerator {
        String generate(PhraseCategory category, String sourceMessage);
    }

    public enum PhraseCategory {
        GENERAL,
        CHALKBOARD,
        MEGAPHONE,
        TRADE
    }

    private static final String[] GENERAL_LINES = {
            "今天的冒险也要加油！", "这张地图的风景真不错。", "一起去打怪吧！",
            "我先在这里休息一下。", "听说自由市场有好东西。", "枫叶村今天真热闹。",
            "我还要继续变强！", "路过的冒险家，祝你好运！", "背包里的药水够用吗？",
            "这附近的怪物不少呢。", "有空一起组队吧！", "慢慢来，享受冒险。"
    };
    private static final String[] CHALKBOARD_LINES = {
            "路过的冒险家，祝你今天好运！", "组队刷怪中，欢迎一起冒险。", "自由市场看看有没有需要的东西。",
            "正在整理背包，稍后继续出发。", "想找人一起做组队任务。", "练级路上，慢慢变强！",
            "出售多余材料，欢迎私聊。", "今天也要把每日目标完成。", "地图风景不错，停下来休息一会。",
            "新手可以来问路，一起玩更开心。"
    };
    private static final String[] MEGAPHONE_LINES = {
            "寻找一起练级的伙伴，在线联系！", "自由市场有好东西，欢迎来看看。", "有没有人一起做组队任务？",
            "出售常用卷轴和材料，价格好商量。", "公会招收活跃玩家，一起冒险！", "祝大家今天爆好装备！",
            "寻找固定队友，晚上一起刷图。", "收一些需要的材料，有的请联系。", "新手求带，愿意一起做任务！",
            "祝各位冒险家游戏愉快！"
    };
    private static final String[] TRADE_LINES = {
            "这个交易看起来没问题。", "谢谢，合作愉快！", "这个价格可以接受。",
            "请确认一下交易内容。", "我再看看背包里的物品。", "暂时没有合适的物品，抱歉。",
            "材料和金币都确认好了。", "交易完成，祝你好运！", "这个物品正是我需要的。",
            "这次先不交易了，下次再见。"
    };
    private static final Map<PhraseCategory, List<String>> DEFAULT_LINES = Map.of(
            PhraseCategory.GENERAL, List.copyOf(Arrays.asList(GENERAL_LINES)),
            PhraseCategory.CHALKBOARD, List.copyOf(Arrays.asList(CHALKBOARD_LINES)),
            PhraseCategory.MEGAPHONE, List.copyOf(Arrays.asList(MEGAPHONE_LINES)),
            PhraseCategory.TRADE, List.copyOf(Arrays.asList(TRADE_LINES))
    );
    private static volatile Map<PhraseCategory, List<String>> editableLines = Map.of();
    private static volatile AiSpeechGenerator aiSpeechGenerator;

    private static final Map<String, String> COMMAND_GROUPS = Map.ofEntries(
            Map.entry("!move", "机器人移动、寻路与移动录制"),
            Map.entry("!env", "机器人环境与性能管理"),
            Map.entry("!bot", "机器人创建、删除与状态管理"),
            Map.entry("!fmbot", "自由市场机器人与商店管理"),
            Map.entry("!betafmshop", "自由市场商店测试管理"),
            Map.entry("!tradebot", "机器人交易测试管理"),
            Map.entry("!opq", "组队任务机器人管理"),
            Map.entry("!gcmove", "游戏中心机器人移动管理"),
            Map.entry("!reactor", "地图机关测试管理"),
            Map.entry("!test", "SoloMapling 测试管理")
    );

    private SoloMaplingChineseLocalization() {
    }

    /** Replaces the generic Chinese bot phrase pool without requiring a restart. */
    public static void setBotLines(List<String> lines) {
        setPhraseLines(PhraseCategory.GENERAL, lines);
    }

    public static List<String> getBotLines() {
        return getPhraseLines(PhraseCategory.GENERAL);
    }

    public static void setPhraseBook(Map<PhraseCategory, List<String>> phraseBook) {
        Map<PhraseCategory, List<String>> copy = new EnumMap<>(PhraseCategory.class);
        for (PhraseCategory category : PhraseCategory.values()) {
            List<String> lines = phraseBook == null ? null : phraseBook.get(category);
            if (lines != null && !lines.isEmpty()) {
                copy.put(category, List.copyOf(lines));
            }
        }
        editableLines = Map.copyOf(copy);
    }

    public static void setPhraseLines(PhraseCategory category, List<String> lines) {
        Map<PhraseCategory, List<String>> copy = new EnumMap<>(PhraseCategory.class);
        copy.putAll(editableLines);
        if (lines == null || lines.isEmpty()) {
            copy.remove(category);
        } else {
            copy.put(category, List.copyOf(lines));
        }
        editableLines = Map.copyOf(copy);
    }

    public static List<String> getPhraseLines(PhraseCategory category) {
        return editableLines.getOrDefault(category, DEFAULT_LINES.get(category));
    }

    public static void setAiSpeechGenerator(AiSpeechGenerator generator) {
        aiSpeechGenerator = generator;
    }

    public static String botSpeech(String message) {
        return botSpeech(message, PhraseCategory.GENERAL);
    }

    public static String botSpeech(String message, PhraseCategory category) {
        if (message == null || message.isBlank()) {
            return message;
        }
        String text = message.trim();
        AiSpeechGenerator generator = aiSpeechGenerator;
        if (generator != null) {
            try {
                String generated = generator.generate(category, text);
                if (generated != null && !generated.isBlank()) {
                    return generated;
                }
            } catch (Exception ignored) {
                // AI is an optional presentation layer; local phrases are always the safe fallback.
            }
        }
        if (containsChinese(text)) {
            return text;
        }
        if (text.matches("(?i)cloud pieces:\\s*\\d+")) {
            return "云朵碎片：" + text.replaceAll("\\D+", "");
        }
        if (text.matches("(?i)dropping\\s+\\d+\\s+clouds?!")) {
            return "我来放下 " + text.replaceAll("\\D+", "") + " 个云朵碎片！";
        }
        if (text.equalsIgnoreCase("Got a record!")) return "拿到唱片了！";
        if (text.equalsIgnoreCase("Dropping my record!")) return "我来放下唱片！";
        if (text.equalsIgnoreCase("Let's go again.")) return "下一局继续！";
        if (text.toLowerCase(Locale.ROOT).startsWith("i'll get the ") && text.endsWith(" box!")) {
            return "这个宝箱交给我！";
        }
        List<String> lines = getPhraseLines(category);
        return lines.get(Math.floorMod(text.hashCode(), lines.size()));
    }

    /**
     * Generates a player reply from an AI context.  Unlike {@link #botSpeech},
     * the source text here is an instruction for the model, never text that is
     * safe to show in-game.  If the API is offline, choose a local reply rather
     * than leaking that instruction into the chat window.
     */
    public static String botReply(String promptContext, PhraseCategory category, String fallbackSeed) {
        AiSpeechGenerator generator = aiSpeechGenerator;
        if (generator != null) {
            try {
                String generated = generator.generate(category, promptContext);
                if (generated != null && !generated.isBlank()) {
                    // A provider can ignore the Chinese prompt and answer in English.
                    // Never expose that raw provider output through a bot avatar: keep
                    // the in-game presentation Chinese and use the local phrase book as
                    // the deterministic fallback.
                    return visibleChinese(generated, category, fallbackSeed);
                }
            } catch (Exception ignored) {
                // The local phrase library is the deliberate offline fallback.
            }
        }
        return visibleChinese(null, category, fallbackSeed == null ? promptContext : fallbackSeed);
    }

    /**
     * Final presentation guard for text that was already prepared by another
     * service.  Unlike {@link #botSpeech(String, PhraseCategory)}, this never
     * calls the optional AI provider again, so it is safe at packet-send time.
     */
    public static String visibleChinese(String message, PhraseCategory category, String fallbackSeed) {
        if (message != null && !message.isBlank() && containsChinese(message)) {
            return message.trim();
        }
        List<String> lines = getPhraseLines(category == null ? PhraseCategory.GENERAL : category);
        String seed = fallbackSeed;
        if (seed == null || seed.isBlank()) {
            seed = message == null ? "SoloMapling" : message;
        }
        return lines.get(Math.floorMod(seed.hashCode(), lines.size()));
    }

    /** Converts SoloMapling GM feedback while deliberately leaving command tokens usable. */
    public static String gmMessage(String message) {
        if (message == null || message.isBlank() || containsChinese(message)) {
            return message;
        }
        String text = message.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        if (text.startsWith("!")) {
            int separator = text.indexOf(" - ");
            String command = separator >= 0 ? text.substring(0, separator) : text;
            String description = separator >= 0 ? text.substring(separator + 3) : "";
            return command + " - " + describeCommand(command, description);
        }
        if (lower.contains("invalid command")) return "无效指令。请输入 !<指令> help 查看帮助。";
        if (lower.contains("second input not an integer")) return "第二个参数必须为整数。";
        if (lower.contains("please input an integer for cid")) return "请输入机器人 CID（整数）。可使用 !move help 查看帮助。";
        if (lower.contains("bot null") || lower.contains("bot not found")) return "未找到对应的机器人。";
        if (lower.startsWith("start movement data recording")) return "已开始录制机器人移动数据。";
        if (lower.startsWith("stop movement data recording")) return "已停止录制机器人移动数据。";
        if (lower.startsWith("bot movement commands")) return "机器人移动指令";
        if (lower.startsWith("environment commands")) return "环境管理指令";
        if (lower.startsWith("free market") || lower.startsWith("fm bot")) return "自由市场机器人指令";
        if (lower.startsWith("trade bot")) return "机器人交易指令";
        if (lower.startsWith("opq")) return "组队任务机器人指令";
        if (lower.startsWith("threads:") || lower.startsWith("bots:") || lower.startsWith("macro ticks:")) {
            return "SoloMapling 运行状态已刷新，请查看服务端控制台中的性能统计。";
        }
        return "GM 指令已执行。详细状态请查看服务端控制台。";
    }

    private static String describeCommand(String command, String description) {
        String normalized = command.toLowerCase(Locale.ROOT);
        String lowerDescription = description.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(" help") || lowerDescription.contains("commands")) return "显示本组管理命令帮助";
        if (lowerDescription.contains("populate fm spot")) return "在当前位置生成自由市场摊位";
        if (lowerDescription.contains("populate entire fm")) return "生成整个自由市场区域的摊位";
        if (lowerDescription.contains("destroy all shops")) return "删除全部机器人商店";
        if (lowerDescription.contains("store permit")) return "授予机器人开设个人商店许可证";
        if (lowerDescription.contains("create bot shop")) return "在当前位置创建机器人商店";
        if (lowerDescription.contains("get your position") || lowerDescription.contains("get bot position")) return "显示坐标、脚踏点与地图信息";
        if (lowerDescription.contains("closest tp portal") || lowerDescription.contains("closest portal")) return "查找最近的传送门";
        if (lowerDescription.contains("start recording")) return "开始录制机器人移动数据";
        if (lowerDescription.contains("stop movement recording")) return "停止录制机器人移动数据";
        if (lowerDescription.contains("play movement recording") || lowerDescription.contains("play csv recording")) return "播放已保存的移动记录";
        if (lowerDescription.contains("pathfind")) return "让机器人寻路移动到指定位置";
        if (lowerDescription.contains("wander") || lowerDescription.contains("stroll") || lowerDescription.contains("loiter")) return "让机器人在指定范围内自然移动";
        if (lowerDescription.contains("portal and enter")) return "移动至传送门并进入";
        if (lowerDescription.contains("move bot to portal")) return "移动机器人至指定传送门";
        if (lowerDescription.contains("interrupt") || lowerDescription.contains("stop bot movement")) return "中断并停止机器人移动";
        if (lowerDescription.contains("sits on chair") || lowerDescription.contains("cancels sit")) return "控制机器人坐下或起身";
        if (lowerDescription.contains("spawn") && lowerDescription.contains("attack")) return "生成机器人并在当前位置自动攻击";
        if (lowerDescription.contains("spawn")) return "在当前位置生成对应机器人或测试对象";
        if (lowerDescription.contains("remove all") || lowerDescription.contains("remove bot") || lowerDescription.contains("delete")) return "移除指定机器人或测试对象";
        if (lowerDescription.contains("set as") || lowerDescription.contains("convert")) return "将指定机器人转换为对应功能类型";
        if (lowerDescription.contains("force") && lowerDescription.contains("attack")) return "强制机器人执行攻击测试";
        if (lowerDescription.contains("attack")) return "执行机器人攻击或战斗测试";
        if (lowerDescription.contains("party")) return "执行机器人组队邀请、接受或队伍状态测试";
        if (lowerDescription.contains("trade")) return "执行机器人交易流程或交易数据测试";
        if (lowerDescription.contains("chat") || lowerDescription.contains("speaks")) return "让机器人发送聊天消息";
        if (lowerDescription.contains("reaction") || lowerDescription.contains("reactor")) return "读取、生成或测试地图反应堆";
        if (lowerDescription.contains("blackjack")) return "配置或测试二十一点机器人";
        if (lowerDescription.contains("platform")) return "读取或调整机器人所在平台";
        if (lowerDescription.contains("map")) return "读取或调整当前地图相关状态";
        if (lowerDescription.contains("dump") || lowerDescription.contains("read") || lowerDescription.contains("list") || lowerDescription.contains("get ")) return "读取并显示当前运行状态";
        if (lowerDescription.contains("start")) return "启动对应的机器人功能";
        if (lowerDescription.contains("stop")) return "停止对应的机器人功能";
        for (Map.Entry<String, String> entry : COMMAND_GROUPS.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "SoloMapling 管理功能";
    }

    private static boolean containsChinese(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '\u3400' && c <= '\u9fff') return true;
        }
        return false;
    }
}
