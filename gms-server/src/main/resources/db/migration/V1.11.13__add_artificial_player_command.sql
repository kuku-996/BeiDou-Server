INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'bots', 4, 1, 'ArtificialPlayerCommand', 4
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'bots');
