package io.pagenote.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewProjectDialogPolicyTest {
  @Test
  fun pickerAndCreateWorkPreventDismissal() {
    assertFalse(NewProjectDialogPolicy.canDismiss(isCreating = true, isChoosingDirectory = false))
    assertFalse(NewProjectDialogPolicy.canDismiss(isCreating = false, isChoosingDirectory = true))
  }

  @Test
  fun idleDialogCanBeDismissed() {
    assertTrue(NewProjectDialogPolicy.canDismiss(isCreating = false, isChoosingDirectory = false))
  }
}
