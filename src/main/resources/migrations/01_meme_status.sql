alter table meme
    add column status varchar not null default 'MODERATION';

update meme
set status = 'PUBLISHED'
where channel_message_id is not null;
