package com.chsdngm.tilly.handlers

//@RunWith(SpringJUnit4ClassRunner::class)
class MemeHandlerTest {

//  @MockBean
//  private lateinit var voteRepository: VoteRepository
//
//  @MockBean
//  private lateinit var memeMatcher: MemeMatcher
//
//  @MockBean
//  private lateinit var userRepository: UserRepository
//
//  @MockBean
//  private lateinit var imageRepository: ImageRepository
//
//  @MockBean
//  private lateinit var memeRepository: MemeRepository
//
//  @SpyBean
//  private lateinit var handler: MemeHandler
//
//  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
//  val update: Update = mock(Update::class.java)
//
//  @Test
//  fun shouldSaveSenderInfoWhenSendingMeme() {
//    val sender = mock(User::class.java).apply {
//      `when`(this.mention()).thenReturn("mention")
//      `when`(this.id).thenReturn(123)
//    }
//
//    val photo = mock(PhotoSize::class.java).apply {
//      `when`(this.fileSize).thenReturn(0)
//      `when`(this.fileId).thenReturn("file_id")
//    }
//
//    val sentChatMessage = mock(Message::class.java).apply {
//      `when`(this.messageId).thenReturn(333)
//    }
//
//    `when`(update.message.from).thenReturn(sender)
//    `when`(update.message.chatId).thenReturn(0L)
//    `when`(update.message.messageId).thenReturn(0)
//    `when`(update.message.photo).thenReturn(listOf(photo))
//    `when`(memeRepository.save(any(MemeEntity::class.java))).thenReturn(MemeEntity(333, 123, "file_id", null, null, null))
//
//    doReturn(File("pathname")).`when`(handler).download("file_id")
//    doReturn(sentChatMessage).`when`(handler).sendMemeToChat(any(MemeUpdate::class.java))
//    doReturn(Message()).`when`(handler).sendReplyToMeme(any(MemeUpdate::class.java))
//
//    handler.handle(MemeUpdate(this.update))
//    verify(userRepository, times(1)).saveIfNotExists(sender)
//  }
//
//  private fun <T> any(type: Class<T>): T = Mockito.any(type)
}
