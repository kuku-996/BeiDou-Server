-- Disable starter AP auto assignment so beginners below level 10 keep their level-up AP
-- and can manually distribute STR/DEX/INT/LUK from the AP window.
UPDATE `game_config`
SET `config_value` = 'false',
    `update_time` = '2026-07-16 00:00:00'
WHERE `config_code` = 'use_auto_assign_starters_ap';

UPDATE `lang_resources`
SET `lang_value` = '10级前新手是否自动分配属性点（false=可手动分配）'
WHERE `lang_type` = 'zh-CN'
  AND `lang_base` = 'game_config'
  AND `lang_code` = 'use_auto_assign_starters_ap';

UPDATE `lang_resources`
SET `lang_value` = 'Whether beginners below level 10 have AP automatically assigned; false allows manual AP distribution.'
WHERE `lang_type` = 'en-US'
  AND `lang_base` = 'game_config'
  AND `lang_code` = 'use_auto_assign_starters_ap';
