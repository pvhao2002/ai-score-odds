CREATE OR REPLACE VIEW v_ou_trend AS
select event_id,
       max(case when odd_date = first_odd_date then over_odds end) as first_over_odds,
       max(case when odd_date = last_odd_date then over_odds end)  as last_over_odds
from (select event_id,
             over_odds,
             odd_date,
             min(odd_date) over (partition by event_id) as first_odd_date,
             max(odd_date) over (partition by event_id) as last_odd_date
      from odd_event
      where odd_type = 'ou') ranked
group by event_id