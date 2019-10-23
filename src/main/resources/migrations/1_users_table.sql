create table if not exists users (
    user_id bigint NOT NULL,
    username varchar(255),
    first_name varchar(255),
    last_name varchar(255),
    PRIMARY KEY (user_id)
);

alter table meme add column if not exists meme_id varchar(255);
alter table meme add column if not exists sender_id bigint;

alter table vote add column if not exists meme_id varchar(255);
alter table vote add column if not exists voter_id bigint;


--
-- rollback
--
drop table if exists users;

alter table meme drop column meme_id;
alter table meme drop column sender_id;

alter table vote drop column meme_id;
alter table vote drop column sender_id;