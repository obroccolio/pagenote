package io.pagenote.android

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderMenuModelTest {
  @Test
  fun usesDrawerBelow600DpAndSidebarAt600Dp() {
    assertEquals(ReaderMenuLayout.COMPACT_DRAWER, ReaderMenuLayout.forWidth(599))
    assertEquals(ReaderMenuLayout.PERMANENT_SIDEBAR, ReaderMenuLayout.forWidth(600))
  }

  @Test
  fun keepsDesktopMenuGroupOrder() {
    assertEquals(
      listOf(
        ReaderMenuGroup.FILE,
        ReaderMenuGroup.AI_ANNOTATIONS,
        ReaderMenuGroup.EDIT,
        ReaderMenuGroup.PARAGRAPH,
        ReaderMenuGroup.FORMAT,
        ReaderMenuGroup.INSERT,
        ReaderMenuGroup.READING,
        ReaderMenuGroup.HANDWRITING,
        ReaderMenuGroup.WORD_EDITING,
        ReaderMenuGroup.SETTINGS,
        ReaderMenuGroup.HELP,
      ),
      ReaderMenuGroup.values().toList(),
    )
  }
}
