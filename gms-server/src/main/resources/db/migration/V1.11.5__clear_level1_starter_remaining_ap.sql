-- Newly created beginner characters should not start with spare AP.
-- Clear the leftover AP already produced by the old manual-starter branch.
UPDATE `characters`
SET `ap` = 0
WHERE `level` = 1
  AND `job` IN (0, 1000, 2000)
  AND `ap` > 0;
