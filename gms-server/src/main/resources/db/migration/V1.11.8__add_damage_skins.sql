CREATE TABLE IF NOT EXISTS damageskin_catalog (
    skinId INT NOT NULL,
    priceMesos BIGINT NOT NULL DEFAULT 10000000,
    PRIMARY KEY (skinId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS damageskin_inventory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    characterId INT NOT NULL,
    skinId INT NOT NULL,
    acquiredAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_damage_skin_owner (characterId, skinId),
    KEY idx_damage_skin_character (characterId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS damage_skin_state (
    characterId INT NOT NULL,
    activeSkinId INT NOT NULL DEFAULT 0,
    PRIMARY KEY (characterId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
