package org.gms.server.artificial.soloport.ArtificialPlayer.BotTradeSystem;

import org.gms.client.Character;
import org.gms.server.Trade;
import org.gms.server.artificial.soloport.localization.SoloMaplingChineseLocalization;
import org.gms.server.artificial.soloport.server.MethodScheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static org.gms.server.artificial.soloport.ArtificialPlayer.BotHelpers.isBot;
import static org.gms.server.artificial.soloport.localization.SoloMaplingChineseLocalization.PhraseCategory.TRADE;

/**
 * Makes direct player-to-bot trade chat conversational.  The original SoloMapling
 * trade FSM only reads offered items and mesos; it never consumed chat text.
 */
public final class BotTradeConversation {
    private static final long REPLY_COOLDOWN_MS = 900L;
    private static final Map<String, Long> lastReplyAt = new ConcurrentHashMap<>();

    private BotTradeConversation() {
    }

    public static void onPlayerTradeChat(Character player, String message) {
        if (player == null || message == null || message.isBlank()) return;

        Trade playerTrade = player.getTrade();
        if (playerTrade == null || playerTrade.getPartner() == null) return;
        Character bot = playerTrade.getPartner().getChr();
        if (bot == null || !isBot(bot)) return;

        String key = player.getId() + ":" + bot.getId();
        long now = System.currentTimeMillis();
        Long previous = lastReplyAt.put(key, now);
        if (previous != null && now - previous < REPLY_COOLDOWN_MS) return;

        String source = "交易对象说：\"" + message.trim() + "\"。请以交易商人的身份简短回应。";
        long delay = 700L + ThreadLocalRandom.current().nextLong(700L);
        MethodScheduler.runAfterDelay(() -> replyIfTradeIsStillOpen(player, bot, source), delay);
    }

    private static void replyIfTradeIsStillOpen(Character player, Character bot, String source) {
        Trade current = player.getTrade();
        if (current == null || current.getPartner() == null || current.getPartner().getChr() != bot) return;
        if (bot.getTrade() == null || bot.getTrade().getPartner() != current) return;

        // botSpeech first tries the configured API; any timeout/error falls back to the editable
        // local "交易" phrasebook, so an API outage never leaves the trade window silent.
        String reply = SoloMaplingChineseLocalization.botSpeech(source, TRADE);
        if (reply != null && !reply.isBlank()) {
            bot.getTrade().chat(reply);
        }
    }
}
