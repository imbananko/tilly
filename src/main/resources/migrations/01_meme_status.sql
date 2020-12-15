create type meme_status as enum ('LOCAL', 'MODERATION', 'SCHEDULED', 'PUBLISHED');

alter table meme add column status meme_status not null default 'MODERATION';

create function meme_status_from_str(character varying) returns meme_status
    immutable
    strict
    language sql
as
$$
select $1::text::meme_status
$$;

create cast (character varying as meme_status) WITH FUNCTION meme_status_from_str(character varying) AS ASSIGNMENT;