-- Version: 25
DELIMITER //
DROP PROCEDURE IF EXISTS `up_GetCurrentUserBlockingByEvent_2` //
CREATE PROCEDURE `up_GetCurrentUserBlockingByEvent_2`(IN pEventId BIGINT, IN pUserId INT)
BEGIN
    DECLARE vLevelPriority INT(11);
    SELECT level_name + 0 INTO vLevelPriority FROM user_account WHERE user_id = pUserID;

    DROP TABLE IF EXISTS tmp_user_current_blocking;
    CREATE TEMPORARY TABLE tmp_user_current_blocking
    (
        user_id          INT(11),
        user_code        VARCHAR(100),
        level_name       ENUM ('PO','CO','PART','CORP','SMA','MA', 'AG', 'PL'),
        last_update_by   VARCHAR(100),
        last_update_date timestamp
    ) ENGINE = MyISAM;

    INSERT INTO tmp_user_current_blocking
    SELECT SQL_CALC_FOUND_ROWS ui.user_id, IF(ui.nickname_status = 'CHANGED', concat(ui.user_code,' (',ui.login_id, ')'), ui.user_code) login_id, ui.level_name, wus.last_update_by, wus.last_update_date
    FROM user_path_info parent
             STRAIGHT_JOIN whitelist_unblock_schedule wus ON parent.user_id = wus.user_id
             INNER JOIN user_info ui ON ui.user_id = wus.user_id
			 LEFT JOIN follow_bet_company f ON parent.parent_id = f.user_id
    WHERE parent.parent_id = pUserId
      AND wus.event_id = pEventId
      AND f.user_id IS NULL
      AND parent.parent_id <> parent.user_id
      AND ui.status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE', 'BLOCKED');

	SET @total_rows := FOUND_ROWS();
	IF @total_rows > 0 THEN
		SELECT GROUP_CONCAT(IF(ui.nickname_status = 'CHANGED', concat(ui.user_code,' (',ui.login_id, ')'), ui.user_code) SEPARATOR '/') as all_parent_codes,
			   tmp.user_id,
			   tmp.user_code,
			   tmp.level_name,
			   IF(wc.last_update_date IS NULL, IF(tmp.level_name ='PO' OR (tmp.level_name + 0) <= vLevelPriority, 'Upline', tmp.last_update_by), 'Competition') last_update_by,
			   IF(wc.last_update_date IS NULL, tmp.last_update_date, wc.last_update_date) last_update_date,
			   ua.level_name as blocked_by_level
		FROM tmp_user_current_blocking tmp
				 INNER JOIN user_path_info up ON tmp.user_id = up.user_id AND up.parent_id <> up.user_id
				 INNER JOIN user_info ui ON up.parent_id = ui.user_id
				 INNER JOIN event e ON e.event_id=pEventId
				 LEFT JOIN whitelist_competitions wc ON wc.competition_id=e.competition_id AND wc.is_block=1 AND wc.user_id=tmp.user_id AND e.sport_id=wc.sport_id
				 LEFT JOIN user_account ua ON ua.user_code = tmp.last_update_by
		WHERE up.parent_id >= pUserId
		GROUP BY tmp.user_id
		ORDER BY tmp.level_name ASC, tmp.user_code ASC;
	ELSE
		INSERT INTO tmp_user_current_blocking
		SELECT ui.user_id, IF(ui.nickname_status = 'CHANGED', concat(ui.user_code,' (',ui.login_id, ')'), ui.user_code) login_id, ui.level_name, wus.last_update_by, wus.last_update_date
		FROM user_path_info parent
		STRAIGHT_JOIN whitelist_competitions wus ON parent.user_id = wus.user_id AND wus.is_block=1
		INNER JOIN user_info ui ON ui.user_id = wus.user_id
		INNER JOIN event e ON e.competition_id = wus.competition_id AND e.sport_id=wus.sport_id
		LEFT JOIN follow_bet_company f ON parent.parent_id = f.user_id
		WHERE parent.parent_id = pUserId
		  AND e.event_id = pEventId
		  AND f.user_id IS NULL
		  AND parent.parent_id <> parent.user_id
		  AND ui.status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE', 'BLOCKED');

		SELECT GROUP_CONCAT(IF(ui.nickname_status = 'CHANGED', concat(ui.user_code,' (',ui.login_id, ')'), ui.user_code) SEPARATOR '/') as all_parent_codes,
			   tmp.user_id,
			   tmp.user_code,
			   tmp.level_name,
			   'Competition' last_update_by,
			   tmp.last_update_date last_update_date,

               IF(ua.level_name is null, ui2.level_name, ua.level_name) blocked_by_level
		FROM tmp_user_current_blocking tmp
		INNER JOIN user_path_info up ON tmp.user_id = up.user_id AND up.parent_id <> up.user_id
		INNER JOIN user_info ui ON up.parent_id = ui.user_id
		LEFT JOIN user_account ua ON ua.user_code = tmp.last_update_by
        left join sub_user su on su.user_code = tmp.last_update_by
		left join user_info ui2 on su.parent_id = ui2.user_id
		WHERE up.parent_id >= pUserId
		GROUP BY tmp.user_id
		ORDER BY tmp.level_name ASC, tmp.user_code ASC;

	END IF;
END
//
