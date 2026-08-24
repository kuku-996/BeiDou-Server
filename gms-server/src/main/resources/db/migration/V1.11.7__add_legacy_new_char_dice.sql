INSERT INTO `game_config` (`config_type`, `config_sub_type`, `config_clazz`, `config_code`, `config_value`, `config_desc`)
SELECT 'server', 'Game Mechanics', 'java.lang.Boolean', 'use_legacy_new_char_dice', 'false',
       'Enable the GMS062-style Adventurer creation dice extension; requires a compatible GMS083 client.'
WHERE NOT EXISTS (
    SELECT 1 FROM `game_config` WHERE `config_code` = 'use_legacy_new_char_dice'
);
