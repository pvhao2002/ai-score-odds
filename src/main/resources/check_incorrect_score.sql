select event_id
     , IF(REPLACE(ht_score_str, 'HT ', '') = CONCAT(ht_home_score, '-', ht_away_score), 'CORRECT',
          'INCORRECT')                                                                        AS check_ht
     , IF(ft_score_str = CONCAT(ft_home_score, ' - ', ft_away_score), 'CORRECT', 'INCORRECT') AS check_ft
     , IF(corner_str = CONCAT(home_corner, ' - ', away_corner), 'CORRECT', 'INCORRECT')       AS check_corner
     , ht_score_str
     , ht_home_score
     , ht_away_score
     , ft_score_str
     , ft_home_score
     , ft_away_score
     , corner_str
     , home_corner
     , away_corner
from event_analyst
GROUP BY event_id
HAVING check_ht = 'INCORRECT'
    OR check_ft = 'INCORRECT'
    OR check_corner = 'INCORRECT';
