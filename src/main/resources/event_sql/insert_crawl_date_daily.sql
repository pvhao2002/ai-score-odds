DELIMITER //
DROP EVENT IF EXISTS insert_crawl_date_daily //
CREATE EVENT IF NOT EXISTS insert_crawl_date_daily
    ON SCHEDULE EVERY 1 DAY
        STARTS CURRENT_DATE + INTERVAL 1 DAY
    DO
BEGIN
        INSERT IGNORE INTO crawl_date(date)
        WITH RECURSIVE date_series
                           AS (SELECT COALESCE(DATE_ADD(MAX(event_date), INTERVAL 1 DAY),
                                               DATE('2024-08-01')) AS date_val
                               FROM event_analyst

                               UNION ALL

                               SELECT DATE_ADD(date_val, INTERVAL 1 DAY)
                               FROM date_series
                               WHERE date_val < CURDATE() - INTERVAL 1 DAY)
        SELECT DATE_FORMAT(date_val, '%Y%m%d') AS formatted_date
        FROM date_series
        WHERE NOT EXISTS (SELECT 1 FROM crawl_date c WHERE c.date = DATE_FORMAT(date_val, '%Y%m%d'));
END //

