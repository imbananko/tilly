ALTER TABLE telegram_user ADD COLUMN IF NOT EXISTS distributed_moderation_group_id INTEGER;

CREATE TABLE IF NOT EXISTS distributed_moderation_group
(
	id serial not null constraint distributed_moderation_group_pk primary key,
	name text
);

CREATE TABLE IF NOT EXISTS distributed_moderation_event
(
	meme_id integer not null,
	moderator_id bigint not null,
	chat_message_id bigint,
	moderation_group_id integer,
	constraint distributed_moderation_event_pk primary key (meme_id, moderator_id)
);
