package org.gms.server.artificial.soloport.ArtificialPlayer.BotTypes;

import org.gms.client.Character;
import org.gms.client.inventory.Item;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotCommandsPack.SocialCommands;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotMessagingSystem.ChatMessage;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotMessagingSystem.MessageQueue;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotSM;
import org.gms.server.artificial.soloport.ArtificialPlayer.BotTradeSystem.BotTradeSM;
import org.gms.server.artificial.soloport.FreeMarket.FMItem;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.gms.server.artificial.soloport.ArtificialPlayer.BotTypeManager.BotType.NX_MERCHANT_BOT;
import static org.gms.server.artificial.soloport.ArtificialPlayer.BotTypeManager.convertBotType;
import static org.gms.server.artificial.soloport.BotLogger.log;
import static org.gms.server.artificial.soloport.Environment.PlatformPlacement.botMoveToPlatformAnyUnoccupiedSpotDynamic;
import static org.gms.server.artificial.soloport.Environment.PlatformPlacement.getCurrentPlatform;
import static org.gms.server.artificial.soloport.Environment.PlatformPlacement.getMainPlatformIds;
import static org.gms.server.artificial.soloport.FreeMarket.ArtificialShopGenerator.generateDarkScrollsList;
import static org.gms.server.artificial.soloport.FreeMarket.ArtificialShopGenerator.generateItem;
import static org.gms.server.artificial.soloport.FreeMarket.ArtificialShopGenerator.generatePotionsList;
import static org.gms.server.artificial.soloport.FreeMarket.ArtificialShopGenerator.generateScrollsList;
import static org.gms.server.artificial.soloport.FreeMarket.ArtificialShopGenerator.generateThiefStarsList;
import static org.gms.server.artificial.soloport.FreeMarket.FMEconomyManager.priceAdjustmentRules;
import static org.gms.server.artificial.soloport.itemPool.ItemInformationProviderUtilities.getItemName;
import static org.gms.server.artificial.soloport.itemPool.ItemUtilities.getItemMarketValue;
import static org.gms.server.artificial.soloport.server.SoloMaplingUtilities.getRandomElement;
import static org.gms.server.artificial.soloport.server.SoloMaplingUtilities.random;
import static org.gms.server.artificial.soloport.server.SoloMaplingUtilities.rollChanceInverse;

public class SellingMerchantBot extends BotSM {
    private SellingState sellingState = SellingState.RESET;
    private List<String> hint = Collections.singletonList(getChr().getName());
    private List<FMItem> itemsToSell;
    private int itemIndex = 0;
    private boolean movedDuringAdvertise = false;

    private enum SellingState {
        RESET,
        SELECT_ITEM,
        ADVERTISE,
        CHECK_TRADES,
        IDLE_ACTIONS
    }

    private static final List<String> MARKET_FLAVOR_MESSAGES = List.of(
            "路过可以看看我的商品，价格好商量！",
            "诚信出售，欢迎直接交易。",
            "清理背包中，有需要的可以来问价。",
            "冒险用品持续补货，欢迎选购！",
            "不急着出，合适的价格就成交。",
            "出售的物品都在交易栏，欢迎看看。"
    );

    public SellingMerchantBot(Character character) {
        super(character);
        dialoguePath = "MerchantBotDialogue.yaml";
        botType = "MerchantBot";
    }

    private void resetState() {
        itemIndex = 0;
        loadItemList();
        sellingState = SellingState.RESET;
    }

    private void loadItemList() {
        Supplier<List<FMItem>>[] generators = new Supplier[]{
                () -> generateScrollsList("A"),
                () -> generateDarkScrollsList("A"),
                () -> generateThiefStarsList("A"),
                () -> generatePotionsList("S")
        };
        itemsToSell = generators[random.nextInt(generators.length)].get();
    }

    private FMItem getCurrentItem() {
        if (itemsToSell == null || itemIndex >= itemsToSell.size()) {
            return null;
        }
        return itemsToSell.get(itemIndex);
    }

    private void selectNextItem() {
        if (itemsToSell == null || itemsToSell.isEmpty()) {
            loadItemList();
        }

        itemIndex++;
        if (itemIndex >= itemsToSell.size()) {
            itemIndex = 0;
            loadItemList();
        }

        FMItem currItem = getCurrentItem();
        if (currItem == null) {
            return;
        }

        Item item = generateItem(currItem.getItemId(), 1, 1);
        getTradeInventory().setItemForSaleMain(item);
        getTradeWants().resetTradeWants();
        int rawValue = getItemMarketValue(item);
        int adjValue = priceAdjustmentRules((int) (rawValue * 0.9));
        getTradeWants().setMesoWanted(adjValue);
        setTradeMode(BotTradeSM.TradeMode.SELLING);

        resetLastTradeResult();
        resetLastTradedCharacter();
    }

    private void advertise() {
        FMItem itm = getCurrentItem();
        if (itm == null) {
            return;
        }
        String itemName = getItemName(itm.getItemId());
        if (itemName != null) {
            String msg = buildSellingMessage(itemName);
            SocialCommands.BotSpeak(getChr(), msg);
        }
    }

    static String buildSellingMessage(String itemName) {
        List<String> prefixes = List.of("出售>", "卖>", "诚意出售>");
        List<String> suffixes = List.of("欢迎交易", "价格可商量", "有需要请直接交易", "先到先得", "欢迎询价");
        return (getRandomElement(prefixes) + " " + itemName + "，" + getRandomElement(suffixes))
                .replace("[", "").replace("]", "");
    }

    // Dynamic movement lands on the exact picked pixel, so the old nudgeAwayFromOverlap
    // band-aid (recorded paths piling bots onto fixed endpoints) is no longer needed here.
    private boolean tryPlatformShuffleWhileAdvertising() {
        if (rollChanceInverse(10)) {
            botMoveToPlatformAnyUnoccupiedSpotDynamic(getChr(), getCurrentPlatform(getChr()));
            return true;
        } else if (rollChanceInverse(20)) {
            botMoveToPlatformAnyUnoccupiedSpotDynamic(getChr(), getRandomElement(List.of("m1", "m5")));
            return true;
        } else if (rollChanceInverse(30)) {
            botMoveToPlatformAnyUnoccupiedSpotDynamic(getChr(), getRandomElement(List.of("m1", "m2")));
            return true;
        } else if (rollChanceInverse(70)) {
            int currentMap = getChr().getMapId();
            botMoveToPlatformAnyUnoccupiedSpotDynamic(getChr(), getRandomElement(getMainPlatformIds(currentMap)));
            return true;
        }
        return false;
    }

    private void handleIdleActions() {
        if (movedDuringAdvertise) {
            movedDuringAdvertise = false;
            return;
        }
        if (rollChanceInverse(10)) {
            botMoveToPlatformAnyUnoccupiedSpotDynamic(getChr(), getCurrentPlatform(getChr()));
        } else if (rollChanceInverse(20)) {
            botMoveToPlatformAnyUnoccupiedSpotDynamic(getChr(), getRandomElement(List.of("m1", "m5")));
        } else if (rollChanceInverse(30)) {
            botMoveToPlatformAnyUnoccupiedSpotDynamic(getChr(), getRandomElement(List.of("m1", "m2")));
        } else if (rollChanceInverse(70)) {
            int currentMap = getChr().getMapId();
            botMoveToPlatformAnyUnoccupiedSpotDynamic(getChr(), getRandomElement(getMainPlatformIds(currentMap)));
        }
    }

    private boolean tryConvertToNXMerchant() {
        if (rollChanceInverse(100)) {
            convertBotType(getChr(), NX_MERCHANT_BOT);
            return true;
        }
        return false;
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
        getDebugger().debugLoggingFull(
                String.format("%s SellingMerchantBot: %s", getChr().getName(), sellingState),
                String.format("%s", sellingState));

        switch (sellingState) {
            case RESET:
                resetState();
                sellingState = SellingState.SELECT_ITEM;
                break;
            case SELECT_ITEM:
                selectNextItem();
                sellingState = SellingState.ADVERTISE;
                break;
            case ADVERTISE:
                if (rollChanceInverse(25)) {
                    SocialCommands.BotSpeak(getChr(), getRandomElement(MARKET_FLAVOR_MESSAGES));
                } else {
                    advertise();
                }
                movedDuringAdvertise = tryPlatformShuffleWhileAdvertising();
                sellingState = SellingState.CHECK_TRADES;
                break;
            case CHECK_TRADES:
                checkForTrades();
                sellingState = SellingState.IDLE_ACTIONS;
                break;
            case IDLE_ACTIONS:
                handleIdleActions();
                if (tryConvertToNXMerchant()) {
                    return;
                }
                sellingState = SellingState.SELECT_ITEM;
                break;
            default:
                log("Unexpected state: " + sellingState);
                state = BotState.FINISHED;
                throw new IllegalStateException("Unexpected state: " + sellingState);
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

