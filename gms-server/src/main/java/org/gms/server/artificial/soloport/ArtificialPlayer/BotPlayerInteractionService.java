package org.gms.server.artificial.soloport.ArtificialPlayer;

import org.gms.client.Character;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotCommandsPack.SocialCommands;
import org.gms.server.artificial.soloport.localization.SoloMaplingChineseLocalization;
import org.gms.server.maps.MapleMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static org.gms.server.artificial.soloport.ArtificialPlayer.BotHelpers.isBot;
import static org.gms.server.artificial.soloport.localization.SoloMaplingChineseLocalization.PhraseCategory.GENERAL;
import static org.gms.server.artificial.soloport.server.MethodScheduler.runAfterDelay;

/**
 * A deliberately small player-to-bot conversation bridge.  It is event driven,
 * responds with at most one nearby bot, and never runs an AI request on a packet
 * handler thread.  The same bridge handles normal chat and megaphone context.
 */
public final class BotPlayerInteractionService {
    private static final int HEARING_RADIUS_PX = 650;
    private static final long PLAYER_COOLDOWN_MS = 3_500L;
    private static final long BOT_COOLDOWN_MS = 7_000L;
    private static final long MAP_COOLDOWN_MS = 900L;
    private static final Map<Integer, Long> playerReplyAt = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> botReplyAt = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> mapReplyAt = new ConcurrentHashMap<>();

    private BotPlayerInteractionService() {
    }

    public static void onPlayerGeneralChat(Character player, String message) {
        respond(player, message, false);
    }

    public static void onPlayerMegaphone(Character player, String message) {
        respond(player, message, true);
    }

    private static void respond(Character player, String message, boolean megaphone) {
        if (player == null || message == null || message.isBlank() || isBot(player)) return;
        String trimmed = message.trim();
        if (trimmed.length() < 2 || trimmed.startsWith("!")) return;

        MapleMap map = player.getMap();
        if (map == null || player.getPosition() == null) return;
        long now = System.currentTimeMillis();
        if (!acquireCooldown(playerReplyAt, player.getId(), now, PLAYER_COOLDOWN_MS)
                || !acquireCooldown(mapReplyAt, map.getId(), now, MAP_COOLDOWN_MS)) return;

        Character bot = selectNearbyAvailableBot(player, map);
        if (bot == null || !acquireCooldown(botReplyAt, bot.getId(), now, BOT_COOLDOWN_MS)) return;

        String source = buildPromptSource(player, trimmed, megaphone);
        long delay = 750L + ThreadLocalRandom.current().nextLong(900L);
        runAfterDelay(() -> replyIfStillNearby(player, bot, map, source), delay);
    }

    private static Character selectNearbyAvailableBot(Character player, MapleMap map) {
        long radiusSq = (long) HEARING_RADIUS_PX * HEARING_RADIUS_PX;
        List<Character> candidates = new ArrayList<>();
        for (Character candidate : map.getCharacters()) {
            if (candidate == null || !isBot(candidate) || candidate.getTrade() != null
                    || candidate.getPosition() == null) continue;
            if (candidate.getPosition().distanceSq(player.getPosition()) <= radiusSq) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingDouble(bot -> bot.getPosition().distanceSq(player.getPosition())));
        // Pick among the closest three so a crowd still feels natural without every bot chiming in.
        return candidates.get(ThreadLocalRandom.current().nextInt(Math.min(3, candidates.size())));
    }

    private static void replyIfStillNearby(Character player, Character bot, MapleMap expectedMap, String source) {
        if (player.getMap() != expectedMap || bot.getMap() != expectedMap || bot.getTrade() != null) return;
        // `source` is AI-only context, not a displayable Chinese sentence.
        // botReply deliberately selects the local phrase library when the API
        // times out instead of echoing this context into public chat.
        String reply = SoloMaplingChineseLocalization.botReply(source, GENERAL, player.getName());
        if (reply != null && !reply.isBlank()) {
            SocialCommands.BotFullChatPrepared(bot, reply);
        }
    }

    private static boolean acquireCooldown(Map<Integer, Long> values, int key, long now, long cooldownMs) {
        Long previous = values.put(key, now);
        return previous == null || now - previous >= cooldownMs;
    }

    private static String buildPromptSource(Character player, String message, boolean megaphone) {
        if (looksLikeNameCall(player, message)) {
            return "对方正在喊你的名字。只输出一句自然的即时回应，例如‘在呢，怎么啦？’；不要复述名字，不要描述场景，不要解释。";
        }
        String lower = message.toLowerCase();
        if (megaphone) return "玩家用喇叭说：\"" + message + "\"。只输出一句像真实玩家的简短回应；不要复述原话，不要出现‘玩家’、‘冒险家’、‘回应’或任何说明文字。";
        if (containsAny(lower, "组队", "队伍", "一起刷", "一起打", "带我", "求带", "pq")) {
            return "有人说：\"" + message + "\"。只输出一句自然的游戏内组队回复，可同意、婉拒或说明正在练级；不要复述原话，不要解释。";
        }
        if (containsAny(lower, "打怪", "练级", "刷怪", "boss", "怪物", "经验", "掉宝", "爆", "伤害")) {
            return "有人在练级时说：\"" + message + "\"。只输出一句自然、简短的战斗聊天回复；不要复述原话，不要解释。";
        }
        if (containsAny(lower, "收", "卖", "买", "交易", "价格", "金币", "卷轴")) {
            return "有人讨论游戏内交易：\"" + message + "\"。只输出一句礼貌、简短的交易回复；不得涉及现实货币、网址或外挂，不要解释。";
        }
        return "有人说：\"" + message + "\"。只输出一句自然、口语化的游戏内闲聊回复；不要复述原话，不要描述场景，不要解释。";
    }

    private static boolean looksLikeNameCall(Character player, String message) {
        if (player == null || message == null) return false;
        String value = message.trim();
        return value.equals(player.getName());
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) return true;
        }
        return false;
    }
}
