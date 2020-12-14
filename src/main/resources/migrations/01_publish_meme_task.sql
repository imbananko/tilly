create table publish_meme_task(
    meme_id integer references meme (id),
    got_in_queue_time timestamp default timezone('UTC'::text, now())
);