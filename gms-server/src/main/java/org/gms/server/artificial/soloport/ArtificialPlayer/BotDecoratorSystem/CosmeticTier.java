package org.gms.server.artificial.soloport.ArtificialPlayer.BotDecoratorSystem;

/**
 * Represents the desirability/quality tier of a cosmetic item (hair, eyes, etc.).
 * Separate from BotTier 鈥?bot tier describes the player archetype,
 * cosmetic tier describes how fashionable/desirable the look is.
 *
 * 3 tiers for hair/eyes. Equipment can define its own tier scale (e.g. 4 tiers)
 * since each cosmetic category independently defines what makes sense.
 */
public enum CosmeticTier {
    PREMIUM,   // Popular, stylish 鈥?cash shop exclusive looks
    STANDARD,  // Solid, normal 鈥?common salon results
    BASIC      // Starter/default 鈥?character creation defaults
}

