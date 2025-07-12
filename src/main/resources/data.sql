insert into kira_league(league_name)
select distinct league_name
from event_analyst
order by league_name;

update event_analyst ea
    inner join kira_league kl on kl.league_name = ea.league_name
set ea.league_id = kl.league_id
where ea.league_id is null;


insert into line(line, line_float)
SELECT CASE
           WHEN MOD(ABS(line_float), 1) = 0.25 THEN
               CONCAT(
                       IF(line_float < 0, '-', ''),
                       FLOOR(ABS(line_float)), '/', FLOOR(ABS(line_float)) + 0.5
               )
           WHEN MOD(ABS(line_float), 1) = 0.5 THEN
               CONCAT(
                       IF(line_float < 0, '-', ''),
                       FLOOR(ABS(line_float)) + 0.5
               )
           WHEN MOD(ABS(line_float), 1) = 0.75 THEN
               CONCAT(
                       IF(line_float < 0, '-', ''),
                       FLOOR(ABS(line_float)) + 0.5, '/', FLOOR(ABS(line_float)) + 1
               )
           ELSE
               CAST(line_float AS CHAR)
           END AS line,
       line_float
FROM (SELECT ROUND(n * 0.25 - 30, 2) AS line_float
      FROM (SELECT ROW_NUMBER() OVER () - 1 AS n
            FROM information_schema.columns
            LIMIT 521) AS seq
      WHERE ROUND(n * 0.25 - 30, 2) <= 100) x;

update odd_event
set home_line =
        CASE
            WHEN INSTR(line, '#') = 0 THEN NULL
            -- Nếu là số, vẫn dùng số gốc, không đổi
            WHEN SUBSTRING_INDEX(line, '#', 1) REGEXP '^[-+]?\\d+(\\.\\d+)?$'
                THEN CAST(SUBSTRING_INDEX(line, '#', 1) AS DECIMAL(10, 8))
            -- Nếu là -a/b
            WHEN SUBSTRING_INDEX(line, '#', 1) REGEXP '^-[0-9]+(\\.[0-9]+)?/[0-9]+(\\.[0-9]+)?$'
                THEN -1 * (
                (ABS(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(line, '#', 1), '/', 1) AS DECIMAL(10, 8)))
                    + ABS(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(line, '#', 1), '/', -1) AS DECIMAL(10, 8))))
                    / 2
                )
            -- Nếu là +a/b hoặc a/b (không có dấu - phía trước)
            WHEN SUBSTRING_INDEX(line, '#', 1) REGEXP '^\\+?[0-9]+(\\.[0-9]+)?/[0-9]+(\\.[0-9]+)?$'
                THEN (
                (ABS(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(line, '#', 1), '/', 1) AS DECIMAL(10, 8)))
                    + ABS(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(line, '#', 1), '/', -1) AS DECIMAL(10, 8))))
                    / 2
                )
            ELSE NULL
            END,
    away_line=
        CASE
            WHEN INSTR(line, '#') = 0 THEN NULL
            -- Nếu là số, vẫn lấy số gốc
            WHEN SUBSTRING_INDEX(line, '#', -1) REGEXP '^[-+]?\\d+(\\.\\d+)?$'
                THEN CAST(SUBSTRING_INDEX(line, '#', -1) AS DECIMAL(10, 8))
            -- Nếu là -a/b
            WHEN SUBSTRING_INDEX(line, '#', -1) REGEXP '^-[0-9]+(\\.[0-9]+)?/[0-9]+(\\.[0-9]+)?$'
                THEN -1 * (
                (ABS(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(line, '#', -1), '/', 1) AS DECIMAL(10, 8)))
                    + ABS(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(line, '#', -1), '/', -1) AS DECIMAL(10, 8))))
                    / 2
                )
            -- Nếu là +a/b hoặc a/b (không có dấu - phía trước)
            WHEN SUBSTRING_INDEX(line, '#', -1) REGEXP '^\\+?[0-9]+(\\.[0-9]+)?/[0-9]+(\\.[0-9]+)?$'
                THEN (
                (ABS(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(line, '#', -1), '/', 1) AS DECIMAL(10, 8)))
                    + ABS(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(line, '#', -1), '/', -1) AS DECIMAL(10, 8))))
                    / 2
                )
            ELSE NULL
            END
where odd_type = 'hdc';