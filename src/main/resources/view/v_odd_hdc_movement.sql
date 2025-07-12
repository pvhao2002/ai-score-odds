CREATE OR REPLACE VIEW v_odd_hdc_movement AS
SELECT event_id,
       home_line,
       home_odds,
       away_line,
       away_odds,
       odd_date,
       CONCAT(FORMAT(home_line, 2), ' ', FORMAT(home_odds, 2), ' ',
              CASE
                  WHEN home_odds > LAG(home_odds) OVER (PARTITION BY event_id ORDER BY odd_date) THEN '↑'
                  WHEN home_odds < LAG(home_odds) OVER (PARTITION BY event_id ORDER BY odd_date) THEN '↓'
                  ELSE ''
                  END) AS home_line_trend,
       CONCAT(FORMAT(away_line, 2), ' ', FORMAT(away_odds, 2), ' ',
              CASE
                  WHEN away_odds > LAG(away_odds) OVER (PARTITION BY event_id ORDER BY odd_date) THEN '↑'
                  WHEN away_odds < LAG(away_odds) OVER (PARTITION BY event_id ORDER BY odd_date) THEN '↓'
                  ELSE ''
                  END) AS away_line_trend
FROM odd_event
WHERE odd_type = 'hdc'
