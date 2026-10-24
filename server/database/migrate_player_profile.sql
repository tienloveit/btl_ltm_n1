-- Email compatibility migration. For existing legacy databases, run migrate_3nf.sql afterwards.
CREATE DATABASE IF NOT EXISTS tank_game
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE tank_game;

CREATE TABLE IF NOT EXISTS player_accounts (
    username VARCHAR(24) NOT NULL PRIMARY KEY,
    display_name VARCHAR(40) NOT NULL,
    email VARCHAR(254) NULL,
    password_salt BINARY(16) NOT NULL,
    password_hash BINARY(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS matches (
    match_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS match_players (
    match_id BIGINT NOT NULL,
    username VARCHAR(24) NOT NULL,
    score INT NOT NULL DEFAULT 0,
    result VARCHAR(8) NOT NULL,
    PRIMARY KEY (match_id, username),
    CONSTRAINT fk_match_players_match
        FOREIGN KEY (match_id) REFERENCES matches (match_id),
    CONSTRAINT fk_match_players_account
        FOREIGN KEY (username) REFERENCES player_accounts (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP PROCEDURE IF EXISTS migrate_player_profile;
DELIMITER //
CREATE PROCEDURE migrate_player_profile()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'player_accounts'
          AND column_name = 'email'
    ) THEN
        ALTER TABLE player_accounts ADD COLUMN email VARCHAR(254) NULL AFTER display_name;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'player_accounts'
          AND index_name = 'uq_player_accounts_email'
    ) THEN
        CREATE UNIQUE INDEX uq_player_accounts_email ON player_accounts (email);
    END IF;
END//
DELIMITER ;

CALL migrate_player_profile();
DROP PROCEDURE migrate_player_profile;