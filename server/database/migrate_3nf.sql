-- Stop all game servers and take a full backup before running this script.
-- MySQL DDL commits implicitly. On interruption, fix the cause and rerun.
USE tank_game;

DROP PROCEDURE IF EXISTS migrate_3nf;
DELIMITER //
CREATE PROCEDURE migrate_3nf()
BEGIN
    DECLARE old_statistics INT DEFAULT 0;
    DECLARE old_outcome INT DEFAULT 0;
    DECLARE finished BOOLEAN DEFAULT FALSE;
    DECLARE fk_name VARCHAR(64);
    DECLARE outcome_keys CURSOR FOR
        SELECT DISTINCT constraint_name FROM information_schema.key_column_usage
        WHERE table_schema = DATABASE() AND table_name = 'matches'
          AND column_name IN ('winner_username', 'loser_username')
          AND referenced_table_name IS NOT NULL;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = TRUE;

    SELECT COUNT(*) INTO old_statistics FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'player_accounts'
      AND column_name IN ('matches_played', 'score', 'wins', 'losses', 'draws');
    SELECT COUNT(*) INTO old_outcome FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'matches'
      AND column_name IN ('winner_username', 'loser_username', 'is_draw');

    IF old_statistics NOT IN (0, 5) OR old_outcome NOT IN (0, 3) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Partial legacy schema: inspect or restore backup before migrating.';
    END IF;

    IF EXISTS (SELECT 1 FROM match_players WHERE score < 0 OR result NOT IN ('WIN', 'LOSS', 'DRAW'))
       OR EXISTS (
           SELECT m.match_id FROM matches m LEFT JOIN match_players mp ON mp.match_id = m.match_id
           WHERE m.status = 'COMPLETED' GROUP BY m.match_id
           HAVING COUNT(mp.username) <> 2
               OR NOT ((SUM(mp.result = 'WIN') = 1 AND SUM(mp.result = 'LOSS') = 1)
                       OR SUM(mp.result = 'DRAW') = 2)
       ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid match results: migration stopped without dropping data.';
    END IF;

    IF old_statistics = 5 THEN
        IF EXISTS (
            SELECT 1 FROM player_accounts a LEFT JOIN (
                SELECT mp.username, COUNT(*) AS played, SUM(mp.result = 'WIN') AS wins,
                       SUM(mp.result = 'LOSS') AS losses, SUM(mp.result = 'DRAW') AS draws
                FROM match_players mp JOIN matches m ON m.match_id = mp.match_id
                WHERE m.status = 'COMPLETED' GROUP BY mp.username
            ) s ON s.username = a.username
            WHERE a.matches_played <> COALESCE(s.played, 0)
               OR a.wins <> COALESCE(s.wins, 0) OR a.losses <> COALESCE(s.losses, 0)
               OR a.draws <> COALESCE(s.draws, 0) OR a.score <> 3 * a.wins + a.draws
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Statistics differ from history: reconcile missing results before migrating.';
        END IF;
    END IF;

    IF old_outcome = 3 THEN
        IF EXISTS (
            SELECT 1 FROM matches m WHERE m.status = 'COMPLETED' AND (
                (m.is_draw = TRUE AND (m.winner_username IS NOT NULL OR m.loser_username IS NOT NULL
                    OR EXISTS (SELECT 1 FROM match_players mp WHERE mp.match_id = m.match_id AND mp.result <> 'DRAW')))
                OR (m.is_draw = FALSE AND (
                    NOT EXISTS (SELECT 1 FROM match_players mp WHERE mp.match_id = m.match_id
                        AND mp.username = m.winner_username AND mp.result = 'WIN')
                    OR NOT EXISTS (SELECT 1 FROM match_players mp WHERE mp.match_id = m.match_id
                        AND mp.username = m.loser_username AND mp.result = 'LOSS')))
            )
        ) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Match outcome differs from participants: reconcile before migrating.';
        END IF;
    END IF;

    -- Support both named SQL constraints and JDBC-generated foreign key names.
    OPEN outcome_keys;
    drop_keys: LOOP
        FETCH outcome_keys INTO fk_name;
        IF finished THEN LEAVE drop_keys; END IF;
        SET @ddl = CONCAT('ALTER TABLE matches DROP FOREIGN KEY `', REPLACE(fk_name, '`', '``'), '`');
        PREPARE drop_key FROM @ddl;
        EXECUTE drop_key;
        DEALLOCATE PREPARE drop_key;
    END LOOP;
    CLOSE outcome_keys;

    IF old_outcome = 3 THEN
        ALTER TABLE matches DROP COLUMN winner_username, DROP COLUMN loser_username, DROP COLUMN is_draw;
    END IF;
    IF old_statistics = 5 THEN
        ALTER TABLE player_accounts DROP COLUMN matches_played, DROP COLUMN score,
            DROP COLUMN wins, DROP COLUMN losses, DROP COLUMN draws;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE() AND table_name = 'match_players'
          AND constraint_name = 'chk_match_players_score') THEN
        ALTER TABLE match_players ADD CONSTRAINT chk_match_players_score CHECK (score >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE() AND table_name = 'match_players'
          AND constraint_name = 'chk_match_players_result') THEN
        ALTER TABLE match_players ADD CONSTRAINT chk_match_players_result CHECK (result IN ('WIN', 'LOSS', 'DRAW'));
    END IF;
END//
DELIMITER ;
CALL migrate_3nf();
DROP PROCEDURE migrate_3nf;
