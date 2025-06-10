drop table if exists crawl_date;
create table crawl_date
(
    id         int not null auto_increment primary key,
    date       varchar(255),
    status     enum ('pending', 'in_progress', 'completed', 'failed', 'regen') default 'pending',
    created_at timestamp                                                       default current_timestamp,
    constraint unique_crawl_date unique (date)
);

drop table if exists `event_crawl`;
create table if not exists `event_crawl`
(
    id          int not null auto_increment primary key,
    event_name  varchar(255),
    event_date  varchar(255),
    detail_link text,
    status      enum ('pending', 'in_progress', 'failed', 'regen') default 'pending',
    created_at  timestamp                                          default current_timestamp,
    constraint unique_event_crawl unique (event_name, event_date)
);

drop table if exists event_analyst;
create table event_analyst
(
    id              int not null auto_increment primary key,
    event_name      varchar(255),
    home_team       varchar(255),
    away_team       varchar(255),
    league_name     varchar(255),
    event_date      datetime,

    ht_home_score   int,
    ht_away_score   int,
    ft_home_score   int,
    ft_away_score   int,

    ht_score_str    varchar(255),
    ft_score_str    varchar(255),

    home_corner     int,
    away_corner     int,
    corner_str      varchar(255),

    is_clear_hdc    enum ('yes', 'no'),
    is_clear_ou     enum ('yes', 'no'),
    is_clear_corner enum ('yes', 'no'),
    index idx_event (event_date, event_name),
    constraint unique_event unique (event_date, event_name)
);



drop table if exists odd_analyst;
create table if not exists odd_analyst
(
    event_id  int,
    odd_type  enum ('hdc', 'ou', '1x2', 'corner'),
    odd_value text,
    primary key (event_id, odd_type)
);

drop table if exists odd_event;
create table odd_event (
                           odd_id bigint primary key auto_increment,
                           event_id int,
                           odd_type enum('1x2', 'ou', 'hdc', 'corner'),
                           odd_date datetime,
                           line varchar(25),
                           home_odds decimal(6,2),
                           draw_odds decimal(6,2),
                           away_odds decimal(6,2),
                           over_odds decimal(6,2),
                           under_odds decimal(6,2),
                           constraint unique_event unique (event_id, odd_type)
);

alter table odd_analyst
    add column status enum ('done', 'in_progress', 'pending', 'fail') default 'pending';
