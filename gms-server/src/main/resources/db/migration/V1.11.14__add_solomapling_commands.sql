-- SoloMapling GM tooling. These commands are intentionally GM-only and are
-- loaded through the existing command_info registry.
INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'bot', 4, 1, 'ArtificialPlayerCommand', 4
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'bot');

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'move', 4, 1, 'BotMoveCommand', 4
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'move');

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'env', 4, 1, 'EnvironmentCommand', 4
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'env');

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'betafmshop', 4, 1, 'ArtificialFreeMarketCommand', 4
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'betafmshop');

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'fmbot', 4, 1, 'FMBotCommand', 4
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'fmbot');

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'gcmove', 4, 1, 'GCMoveCommand', 4
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'gcmove');

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'opq', 4, 1, 'OPQCommands', 4
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'opq');

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'reactor', 4, 1, 'ReactorCommands', 4
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'reactor');

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'test', 4, 1, 'TestDevCommand', 4
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'test');

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'tradebot', 4, 1, 'TradeBotTestCommand', 4
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'tradebot');
