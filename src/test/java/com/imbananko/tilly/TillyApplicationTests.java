package com.imbananko.tilly;

import com.imbananko.tilly.model.MemeEntity;
import com.imbananko.tilly.model.VoteEntity;
import com.imbananko.tilly.model.VoteEntity.Value;
import com.imbananko.tilly.repository.MemeRepository;
import com.imbananko.tilly.repository.VoteRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TillyApplicationTests {

  @Autowired
  private VoteRepository voteRepository;

  @Autowired
  private MemeRepository memeRepository;

  @Test
  public void asdasd() {
    MemeEntity meme = MemeEntity.builder()
            .authorUsername("test")
            .targetChatId(0L)
            .fileId("test").build();

    memeRepository.save(meme);


    System.out.println("asd");

    boolean exists = voteRepository.exists(VoteEntity.builder()
        .username("imbananko")
        .chatId(-398093305L)
        .fileId("AgADAgADYqsxG0AT4EtNPzzY5Pk_BQPptw8ABAEAAwIAA20AA5KPAwABFgQ")
        .value(Value.UP)
        .build());

    System.out.println(exists);

    VoteEntity entity = VoteEntity.builder()
        .username("test")
        .chatId(-0L)
        .fileId("test")
        .value(Value.UP)
        .build();

    voteRepository.insertOrUpdate(entity);
    voteRepository.delete(entity);
  }

}
