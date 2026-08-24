INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'battle', 0, 1, 'BattleCommand', 0
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'battle');
