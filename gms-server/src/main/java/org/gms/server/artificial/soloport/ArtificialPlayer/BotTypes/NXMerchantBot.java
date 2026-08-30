package org.gms.server.artificial.soloport.ArtificialPlayer.BotTypes;

import org.gms.client.Character;
import org.gms.server.Trade;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotCommandsPack.SocialCommands;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotMessagingSystem.ChatMessage;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotMessagingSystem.MessageQueue;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotSM;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotTradeSystem.BotTradeSM;
import org.gms.server.artificial.soloport.server.BotTiming;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.gms.server.artificial.soloport.ArtificialPlayer.BotCommandsPack.MapleMessengerCommands.botLeaveMessenger;
import static org.gms.server.artificial.soloport.ArtificialPlayer.BotCommandsPack.MapleMessengerCommands.botSendChatFull;
import static org.gms.server.artificial.soloport.ArtificialPlayer.BotCommandsPack.MapleMessengerCommands.isMessengerInviteAccepted;
import static org.gms.server.artificial.soloport.ArtificialPlayer.BotCommandsPack.MapleMessengerCommands.sendMessengerInviteComplete;
import static org.gms.server.artificial.soloport.ArtificialPlayer.BotTypeManager.BotType.BUYING_MERCHANT_BOT;
import static org.gms.server.artificial.soloport.ArtificialPlayer.BotTypeManager.BotType.SELLING_MERCHANT_BOT;
import static org.gms.server.artificial.soloport.ArtificialPlayer.BotTypeManager.convertBotType;
import static org.gms.server.artificial.soloport.BotLogger.log;
import static org.gms.server.artificial.soloport.Environment.PlatformPlacement.botMoveToPlatformAnyUnoccupiedSpotDynamic;
import static org.gms.server.artificial.soloport.Environment.PlatformPlacement.getCurrentPlatform;
import static org.gms.server.artificial.soloport.Environment.PlatformPlacement.getMainPlatformIds;
import static org.gms.server.artificial.soloport.FreeMarket.ArtificialShopGenerator.generateItem;
import static org.gms.server.artificial.soloport.server.NXCodeManager.createCompleteNXCode;
import static org.gms.server.artificial.soloport.server.NXCodeManager.generateGiftCardCode;
import static org.gms.server.artificial.soloport.server.SoloMaplingUtilities.getRandomElement;
import static org.gms.server.artificial.soloport.server.SoloMaplingUtilities.random;
import static org.gms.server.artificial.soloport.server.SoloMaplingUtilities.rollChanceInverse;
import static org.gms.server.artificial.soloport.server.SoloMaplingUtilities.waitForCondition;

public class NXMerchantBot extends BotSM {
    private NXState nxState = NXState.SETUP;
    private List<String> hint = Collections.singletonList(getChr().getName());
    private int advertiseCycles = 0;
    private static final int MAX_ADVERTISE_CYCLES = 15;

    private enum NXState {
        SETUP,
        ADVERTISE,
        CHECK_TRADES,
        DELIVER_CODE,
        CONVERT_BACK
    }

    private static final List<String> MARKET_FLAVOR_MESSAGES = List.of(
            "点券兑换服务，价格写在聊天栏中。",
            "需要点券兑换的冒险家可以直接交易。",
            "兑换完成后会通过密友发送兑换码。",
            "诚信点券兑换，欢迎询价。"
    );

    public NXMerchantBot(Character character) {
        super(character);
        dialoguePath = "MerchantBotDialogue.yaml";
        botType = "MerchantBot";
    }

    private void setupNXSale() {
        // Use a filler item as the visual representation in trade
        org.gms.client.inventory.Item filler = generateItem(4031865, 1, 100);
        getTradeInventory().setItemForSaleMain(filler);
        getTradeWants().resetTradeWants();
        int fiftyMill = 50_000_000;
        getTradeWants().setMesoWanted(fiftyMill);
        setTradeMode(BotTradeSM.TradeMode.SELLING);
        resetLastTradeResult();
        resetLastTradedCharacter();
    }

    private void advertise() {
        List<String> messages = List.of(
                "出售> 10000 点券兑换码，5000万金币，欢迎交易！",
                "点券兑换> 10000 点券，5000万金币，诚信交易。",
                "出售> 10000 点券兑换码，有需要请直接交易。",
                "点券兑换服务：10000 点券 / 5000万金币。"
        );
        SocialCommands.BotSpeak(getChr(), getRandomElement(messages));
    }

    private void deliverNXCode() {
        if (getLastTradeResult() != Trade.TradeResult.SUCCESSFUL) {
            convertBack();
            return;
        }

        SocialCommands.BotSpeak(getChr(), "正在发送密友邀请，请接受邀请。 ");
        sendMessengerInviteComplete(getChr(), getLastTradedCharacter());

        boolean accepted = waitForCondition(
                () -> isMessengerInviteAccepted(getChr(), getLastTradedCharacter())
        );

        if (accepted) {
            String nxCode = generateGiftCardCode();
            createCompleteNXCode(nxCode);

            botSendChatFull(getChr(), "这是 10000 点券兑换码，请记录完整内容，输入时不要包含连字符。", 3000);
            botSendChatFull(getChr(), nxCode, 7000);
            botSendChatFull(getChr(), "兑换完成，祝你游戏愉快！", 2000);

            BotTiming.after(2000, () -> botLeaveMessenger(getChr()));
            waitFor(2500); // hold CONVERT_BACK until the messenger leave lands
        } else {
            SocialCommands.BotSpeak(getChr(), "未接受密友邀请，本次兑换已取消。 ");
        }

        resetLastTradeResult();
        resetLastTradedCharacter();
    }

    // Dynamic movement lands on the exact picked pixel, so the old nudgeAwayFromOverlap
    // band-aid (recorded paths piling bots onto fixed endpoints) is no longer needed here.
    private boolean tryPlatformShuffle() {
        if (rollChanceInverse(15)) {
            botMoveToPlatformAnyUnoccupiedSpotDynamic(getChr(), getCurrentPlatform(getChr()));
            return true;
        } else if (rollChanceInverse(40)) {
            botMoveToPlatformAnyUnoccupiedSpotDynamic(getChr(), getRandomElement(List.of("m1", "m5")));
            return true;
        }
        return false;
    }

    private void convertBack() {
        if (random.nextBoolean()) {
            convertBotType(getChr(), SELLING_MERCHANT_BOT);
        } else {
            convertBotType(getChr(), BUYING_MERCHANT_BOT);
        }
    }

    @Override
    public void updateState() {
        super.updateState();
        if (checkIfNotRunningOrPaused()) {
            return;
        }
        if (getState() == BotState.TRADING) {
            return;
        }
        // Skip straight to delivery if trade completed while we were in TRADING state
        if (getLastTradeResult() == Trade.TradeResult.SUCCESSFUL && nxState != NXState.DELIVER_CODE && nxState != NXState.CONVERT_BACK) {
            nxState = NXState.DELIVER_CODE;
        }

        getDebugger().debugLoggingFull(
                String.format("%s NXMerchantBot: %s", getChr().getName(), nxState),
                String.format("%s", nxState));

        switch (nxState) {
            case SETUP:
                setupNXSale();
                nxState = NXState.ADVERTISE;
                break;
            case ADVERTISE:
                // 4% (1/25) Chance to advertise flavor, 96% chance to advertise NX
                if (rollChanceInverse(25)) {
                    SocialCommands.BotSpeak(getChr(), getRandomElement(MARKET_FLAVOR_MESSAGES));
                } else {
                    advertise();
                }
                nxState = NXState.CHECK_TRADES;
                break;
            case CHECK_TRADES:
                checkForTrades();
                advertiseCycles++;
                tryPlatformShuffle();
                if (getLastTradeResult() == Trade.TradeResult.SUCCESSFUL) {
                    nxState = NXState.DELIVER_CODE;
                } else if (advertiseCycles >= MAX_ADVERTISE_CYCLES) {
                    nxState = NXState.CONVERT_BACK;
                } else {
                    nxState = NXState.ADVERTISE;
                }
                break;
            case DELIVER_CODE:
                deliverNXCode();
                nxState = NXState.CONVERT_BACK;
                break;
            case CONVERT_BACK:
                convertBack();
                break;
            default:
                log("Unexpected state: " + nxState);
                state = BotState.FINISHED;
                throw new IllegalStateException("Unexpected state: " + nxState);
        }
    }

    @Override
    public void displayCommands(Character chr) {
        SocialCommands.displayPlayerChatCommands(chr, hint);
    }

    @Override
    public void processMessages() {
        try {
            ChatMessage message = MessageQueue.getInstance().getMessageWithTimeout("secondary", 1, TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

