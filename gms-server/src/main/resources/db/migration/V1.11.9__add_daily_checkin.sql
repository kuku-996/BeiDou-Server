CREATE TABLE IF NOT EXISTS `daily_checkin_state` (
    `characterId` INT NOT NULL,
    `checkinDay` INT NOT NULL DEFAULT 0,
    `claimedMask` INT NOT NULL DEFAULT 0,
    `lastClaim` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`characterId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
