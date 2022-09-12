create table wedding_registration
(
    id               integer primary key autoincrement,
    name             text    not null,
    amount           integer not null,
    food             text    not null,
    track_suggestion text,
    spotify_id       text,
    other            text
);