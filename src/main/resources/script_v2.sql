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
    event_id              int not null auto_increment primary key,
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

    is_clear_hdc    enum ('yes', 'no', 'draw'),
    is_clear_ou     enum ('yes', 'no', 'draw'),
    is_clear_corner enum ('yes', 'no', 'draw'),
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
create table odd_event
(
    odd_id     bigint primary key auto_increment,
    event_id   int,
    odd_type   enum ('1x2', 'ou', 'hdc', 'corner'),
    odd_date   datetime,
    line       varchar(25),
    home_odds  decimal(6, 2),
    draw_odds  decimal(6, 2),
    away_odds  decimal(6, 2),
    over_odds  decimal(6, 2),
    under_odds decimal(6, 2),
    constraint unique_event unique (event_id, odd_type)
);

alter table odd_analyst
    add column status enum ('done', 'in_progress', 'pending', 'fail') default 'pending';
alter table event_analyst
    add column link text;
ALTER TABLE event_crawl
    ADD COLUMN worker    VARCHAR(64),
    ADD COLUMN pick_time DATETIME;
alter table event_crawl
    modify status enum ('pending', 'in_progress', 'failed', 'picked') default 'pending';
alter table event_analyst
    add column ft_total_goal int default 0,
    add column ht_total_goal int default 0,
    add column total_corner  int default 0;
alter table odd_event
    add column open_odd boolean default false;
alter table odd_event
    add index idx_odd_line (event_id, odd_type, line, open_odd, odd_date);

drop table if exists kira_league;
create table if not exists kira_league
(
    league_id   int primary key auto_increment,
    league_name varchar(100) unique,
    is_main     boolean default false
);

alter table event_analyst
    add column league_id int;

drop table if exists line;
create table if not exists line
(
    line_id    int auto_increment primary key,
    line       varchar(25),
    line_float float
);

alter table odd_event
    add column home_line decimal(10, 8) null after line;
alter table odd_event
    add column away_line decimal(10, 8) null after home_line;
alter table event_analyst
    add index idx_home_away_league (league_name, home_team, away_team);
alter table event_analyst
    add column home_line_movement  text
        comment 'Xu hướng kèo chấp HDC của đội nhà, ví dụ: 0.25 1.85 ↑',
    add column away_line_movement  text
        comment 'Xu hướng kèo chấp HDC của đội khách, ví dụ: 0.25 1.85 ↑',
    add column over_line_movement  text
        comment 'Xu hướng kèo Tài của trận đấu, ví dụ: 2.5 1.85 ↑',
    add column under_line_movement text
        comment 'Xu hướng kèo Xỉu của trận đấu, ví dụ: 2.5 1.85 ↑',
    add column first_home_odds     float
        comment 'Tỷ lệ kèo chấp HDC của đội nhà tại thời điểm đầu trận',
    add column last_home_odds      float
        comment 'Tỷ lệ kèo chấp HDC của đội nhà tại thời điểm cuối trận',
    add column first_away_odds     float
        comment 'Tỷ lệ kèo chấp HDC của đội khách tại thời điểm đầu trận',
    add column last_away_odds      float
        comment 'Tỷ lệ kèo chấp HDC của đội khách tại thời điểm cuối trận',
    add column first_over_odds     float
        comment 'Tỷ lệ kèo Tài tại thời điểm đầu trận',
    add column last_over_odds      float
        comment 'Tỷ lệ kèo Tài tại thời điểm cuối trận',
    add column first_under_odds    float
        comment 'Tỷ lệ kèo Xỉu tại thời điểm đầu trận',
    add column last_under_odds     float
        comment 'Tỷ lệ kèo Xỉu tại thời điểm cuối trận',
    add column first_hdc           varchar(25),
    add column last_hdc            varchar(25),
    add column first_ou            varchar(25),
    add column last_ou             varchar(25);

create table router_setting
(
    crawl_setting_id int auto_increment primary key,
    node             varchar(100) unique,
    url              varchar(255),
    is_active        tinyint(1) default 0,
    last_update      datetime   default current_timestamp on update current_timestamp
);

create table pc
(
    pc_id    int auto_increment
        primary key,
    pc_name  varchar(255) null,
    event_id int          null,
    message  text         null,
    status   varchar(50)  null,
    index pc_name (pc_name, event_id)
);

drop table if exists schedule_manager;
create table schedule_manager
(
    schedule_name varchar(255),
    host_name     varchar(255),
    status        enum ('active', 'inactive') default 'inactive',
    last_update   timestamp                   default current_timestamp on update current_timestamp,
    primary key (schedule_name, host_name)
);
