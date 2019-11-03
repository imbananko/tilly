create table if not exists explanation_request
(
    user_id integer not null,
    chat_id bigint not null,
    message_id integer not null,
    explain_reply_message_id integer not null,
    explain_till timestamp default timezone('MSK'::text, now()),
    primary key (user_id, chat_id, explain_reply_message_id)
);

create index explain_till_idx on explanation_request(explain_till);

--
--
-- ROLLBACK
--
--
drop table explanation_request;