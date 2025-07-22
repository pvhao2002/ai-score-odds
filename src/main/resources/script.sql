drop table if exists odds;
drop table if exists h2h;
drop table if exists events;
drop table if exists teams;

create table teams
(
    team_id   bigint auto_increment primary key,
    team_name varchar(255),
    status    enum ('active', 'inactive') default 'active'
);

drop table if exists events;
create table events
(
    event_id       bigint auto_increment primary key,
    detail_link    text,
    event_name     varchar(255),
    event_date     datetime,
    league_name    varchar(355),
    league_id      int,
    number_updated int       default 0,
    last_update    timestamp default current_timestamp on update current_timestamp,
    index idx_event (event_date, league_name, event_name),
    index idx_event_league (league_id),
    constraint event_unique unique (event_date, event_name)
);


drop table if exists odds;
create table odds
(
    event_id  bigint,
    odd_type  enum ('1x2', 'handicap', 'goals', 'corners'),
    odd_value text,
    primary key (event_id, odd_type),
    index idx_odd_event (event_id, odd_type)
);

create table h2h
(
    h2h_id      bigint auto_increment primary key,
    event_id    bigint,
    h2h_date    datetime,
    league_name varchar(255),
    home_name   varchar(255),
    away_name   varchar(255),
    home_id     bigint,
    away_id     bigint,
    result      text,
    index idx_h2h (event_id, h2h_date, league_name)
);
