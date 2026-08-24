-- Native GMS083 adventurer creation dice. This migration is intentionally
-- newer than the current production schema history (2.1.33).
INSERT INTO `game_config`(`config_type`, `config_sub_type`, `config_clazz`, `config_code`, `config_value`, `config_desc`, `update_time`)
SELECT 'server', 'Game Mechanics', 'java.lang.Boolean', 'enable_native_adventurer_dice', 'true', 'enable_native_adventurer_dice', NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM `game_config` WHERE `config_code` = 'enable_native_adventurer_dice'
);

INSERT INTO `lang_resources`(`lang_type`, `lang_base`, `lang_code`, `lang_value`, `lang_extend`)
SELECT 'zh-CN', 'game_config', 'enable_native_adventurer_dice', '启用GMS083冒险家创建角色原生投骰属性（需配套客户端DLL）', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `lang_resources` WHERE `lang_type` = 'zh-CN' AND `lang_code` = 'enable_native_adventurer_dice'
);

INSERT INTO `lang_resources`(`lang_type`, `lang_base`, `lang_code`, `lang_value`, `lang_extend`)
SELECT 'en-US', 'game_config', 'enable_native_adventurer_dice', 'Enable native adventurer creation dice (matching client DLL required)', NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `lang_resources` WHERE `lang_type` = 'en-US' AND `lang_code` = 'enable_native_adventurer_dice'
);
