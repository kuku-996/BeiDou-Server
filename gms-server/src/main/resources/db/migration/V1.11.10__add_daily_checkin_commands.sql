INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'daily', 0, 1, 'CheckinCommand', 0
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'daily');

INSERT INTO command_info (syntax, level, enabled, clazz, default_level)
SELECT 'checkin', 0, 1, 'CheckinCommand', 0
WHERE NOT EXISTS (SELECT 1 FROM command_info WHERE syntax = 'checkin');
