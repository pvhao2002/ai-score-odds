CREATE OR REPLACE VIEW v_hdc_trend AS
select event_id,
       max(case when odd_date = first_odd_date then home_odds end) as first_home_odds,
       max(case when odd_date = last_odd_date then home_odds end)  as last_home_odds
from (select event_id,
             home_odds,
             odd_date,
             min(odd_date) over (partition by event_id) as first_odd_date,
             max(odd_date) over (partition by event_id) as last_odd_date
      from odd_event
      where odd_type = 'hdc') ranked
group by event_id