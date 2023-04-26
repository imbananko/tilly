alter table meme add column published timestamp;
update meme set published = created where published is null and channel_message_id is not null;