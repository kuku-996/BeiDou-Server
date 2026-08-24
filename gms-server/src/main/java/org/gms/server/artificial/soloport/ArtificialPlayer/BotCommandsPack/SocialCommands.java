package org.gms.server.artificial.soloport.ArtificialPlayer.BotCommandsPack;

import org.gms.client.Character;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotHelpers;
import org.gms.server.artificial.soloport.localization.SoloMaplingChineseLocalization;
import org.gms.server.artificial.soloport.localization.SoloMaplingChineseLocalization.PhraseCategory;
import org.gms.util.PacketCreator;

import java.util.ArrayList;
import java.util.List;

import static org.gms.server.artificial.soloport.server.SoloMaplingUtilities.generateRandomNumber;

/*
Commands related to bots socializing with the player
 */

public class SocialCommands {
    static boolean botChatTypingStyle = false;
    static boolean debugSkipDialog = false;

    public static void BotFullChat(Character fakechar, String message) {
        BotFullChat(fakechar, message, PhraseCategory.GENERAL);
    }

    private static void BotFullChat(Character fakechar, String message, PhraseCategory category) {
        String localized = SoloMaplingChineseLocalization.botSpeech(message, category);
        broadcastFullChat(fakechar, localized);
    }

    private static void broadcastFullChat(Character fakechar, String localized) {
        fakechar.getMap().broadcastMessage(PacketCreator.getChatText(fakechar.getId(), localized, fakechar.isGM(), (byte) 0));
    }

    /** Sends text that has already been generated/localized by an interaction service. */
    public static void BotFullChatPrepared(Character fakechar, String message) {
        if (fakechar != null && message != null && !message.isBlank()) {
            // AI and imported interaction handlers both arrive here.  Treat this
            // as the final visible-text boundary so a provider's English fallback
            // cannot leak into the Chinese game client.
            broadcastFullChat(fakechar, SoloMaplingChineseLocalization.visibleChinese(
                    message, PhraseCategory.GENERAL, message));
        }
    }

    public static void BotSpeak(Character fakechar, String message) {
        if (botChatTypingStyle) {
            BotChatbubbleTyping(fakechar, message, 150);
        } else {
            BotFullChat(fakechar, message);
        }
    }

    /** Sends trade-related map chat through the dedicated trade phrase book. */
    public static void BotTradeSpeak(Character fakechar, String message) {
        BotFullChat(fakechar, message, PhraseCategory.TRADE);
    }

    public static void BotChatbubble(Character fakechar, String message) {
        String localized = SoloMaplingChineseLocalization.botSpeech(message);
        fakechar.getMap().broadcastMessage(PacketCreator.getChatText(fakechar.getId(), localized, false, 1)); // Send message just bubble, no chat box
    }

    public static void BotChatbubbleTyping(Character fakechar, String message, int delayMillis) {
        BotChatbubbleTyping(fakechar, message, delayMillis, true);
    }

    // Deliberate blocking leaf primitive: the typing rhythm IS the effect and the
    // duration is data-driven (message length). Callers pick the thread.
    public static void BotChatbubbleTyping(Character fakechar, String message, int delayMillis, boolean showInChat) {
        message = SoloMaplingChineseLocalization.botSpeech(message);
        StringBuilder displayedMessage = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            displayedMessage.append(message.charAt(i)); // Add the next character

            // Broadcast the current state of the message
            fakechar.getMap().broadcastMessage(PacketCreator.getChatText(fakechar.getId(), displayedMessage.toString(), false, 1));

            // If the next character exists, add it to simulate 1-2 letters at a time
            if (i + 1 < message.length()) {
                displayedMessage.append(message.charAt(++i));
                fakechar.getMap().broadcastMessage(PacketCreator.getChatText(fakechar.getId(), displayedMessage.toString(), false, 1));
            }

            // Pause to simulate typing
            BotHelpers.blockingSleep(delayMillis);
        }

        if (showInChat) {
            // The typing animation already generated/localized this sentence once.
            broadcastFullChat(fakechar, message);
        }

    }

    // Deliberate blocking leaf primitive: line count is data-driven; callers
    // (dialogue playback) depend on this holding their thread until done.
    public static void BotDialogue(Character fakechar, List<String> dialogue) {
        if (debugSkipDialog) {
            return;
        }
        for (int i = 0; i < dialogue.size(); i++) {
            BotSpeak(fakechar, dialogue.get(i));
            if (i < dialogue.size() - 1) { // Skip sleep for the last element
                BotHelpers.blockingSleep(5000);
            }
        }
    }

    public static void BotEmote(Character fakechar) {
        int emote = generateRandomNumber(1, 22);
        BotEmote(fakechar, emote);
    }

    public static void BotEmote(Character fakechar, int emote) {
        if (fakechar != null) {
            fakechar.getMap().broadcastMessage(fakechar, PacketCreator.facialExpression(fakechar, emote), true);
            fakechar.getMap().broadcastMessage(fakechar, PacketCreator.facialExpression(fakechar, emote), fakechar.getPosition());
        }
    }

    public static void displayPlayerChatCommands(Character chr, List<String> commands) {
        displayPlayerChatCommands(chr, buildPlayerChatCommands(commands));
    }

    public static void displayPlayerChatCommands(Character chr, String str) {
        List<String> singleStr = new ArrayList<>();
        singleStr.add(str);
        displayPlayerChatCommands(chr, singleStr);
    }

    public static void displayPlayerChatCommands(Character chr, ChatCommandResult msg1) {
        // sendHint uses pixels, while String.length() counts a Han character
        // as one unit.  The old *10 conversion gave a three-option Chinese
        // menu only about 70px, so every character wrapped vertically.
        // Keep a readable menu floor and budget wider glyphs correctly.
        int maxWidth = Math.max(190, msg1.getMaxWidth() * 18 + 40);
        int duration = 25;
        chr.getClient().sendPacket(PacketCreator.sendHint(msg1.getMessage(), maxWidth, duration));
        chr.getClient().sendPacket(PacketCreator.enableActions());
    }

    public static void expirePlayerChatCommands(Character chr) {
        chr.getClient().sendPacket(PacketCreator.sendHint(".", 40, 0));
        chr.getClient().sendPacket(PacketCreator.enableActions());
    }

    public static void spawnCygnusGuide(Character chr, boolean spawn) {
        chr.getClient().sendPacket(PacketCreator.spawnGuide(spawn));
    }

    public static void talkCygnusGuide(Character chr, String msg) {
        chr.getClient().sendPacket(PacketCreator.talkGuide(msg));
    }

    public static void talkCygnusGuideCommands(Character chr, List<String> commands) {
        ChatCommandResult msg1 = buildPlayerChatCommands(commands);
        talkCygnusGuide(chr, msg1.getMessage());
    }

    public static void botSetChalkboard(Character fakechar, String chalkboardMessage) {
        fakechar.setChalkboard(SoloMaplingChineseLocalization.botSpeech(chalkboardMessage, PhraseCategory.CHALKBOARD));
        fakechar.getMap().broadcastMessage(PacketCreator.useChalkboard(fakechar, false));
        fakechar.sendPacket(PacketCreator.enableActions());
    }

    public static void botClearChalkboard(Character fakechar) {
        fakechar.setChalkboard(null);
        fakechar.getMap().broadcastMessage(PacketCreator.useChalkboard(fakechar, true));
    }

    protected static ChatCommandResult buildPlayerChatCommands(List<String> commands) {
        StringBuilder msgBuilder = new StringBuilder();
        int maxWidth = 0;

        // First pass: Calculate max width
        for (String command : commands) {
            maxWidth = Math.max(maxWidth, command.length());
        }

        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            // Pad the command to the max width
            String paddedCommand = command + " ".repeat((maxWidth - command.length()));

            msgBuilder.append(i + 1)  // Line number starts from 1
                    .append(". ")  // Add period and space
                    .append(command) // Append command
                    .append("\r\n"); // Append new line
        }

        return new ChatCommandResult(msgBuilder.toString(), commands.size(), maxWidth);
    }

    public static class ChatCommandResult {
        private final String message;
        private final int numberOfLines;
        private final int maxWidth;

        public ChatCommandResult(String message, int numberOfLines, int maxWidth) {
            this.message = message;
            this.numberOfLines = numberOfLines;
            this.maxWidth = maxWidth;
        }

        public String getMessage() {
            return message;
        }

        public int getNumberOfLines() {
            return numberOfLines;
        }

        public int getMaxWidth() {
            return maxWidth;
        }
    }
}

