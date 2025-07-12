CREATE OR REPLACE VIEW v_odd_ou_movement AS
SELECT event_id,
       line,
       over_odds,
       under_odds,
       odd_date,
       CONCAT(line, ' ', FORMAT(over_odds, 2), ' ', FORMAT(under_odds, 2), ' ',
              CASE
                  WHEN over_odds > LAG(over_odds) OVER (PARTITION BY event_id ORDER BY odd_date) THEN '↑'
                  WHEN over_odds < LAG(over_odds) OVER (PARTITION BY event_id ORDER BY odd_date) THEN '↓'
                  ELSE ''
                  END) AS line_ou_trend
FROM odd_event
WHERE odd_type = 'ou'
