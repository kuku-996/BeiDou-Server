-- Requested starter baseline: level-1 beginner characters start at 4/4/4/4 with no spare AP.
UPDATE `characters`
SET `str` = 4,
    `dex` = 4,
    `int` = 4,
    `luk` = 4,
    `ap` = 0
WHERE `level` = 1
  AND `job` IN (0, 1000, 2000);
