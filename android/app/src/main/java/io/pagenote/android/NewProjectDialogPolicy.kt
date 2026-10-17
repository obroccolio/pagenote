package io.pagenote.android

internal object NewProjectDialogPolicy {
  fun canDismiss(isCreating: Boolean, isChoosingDirectory: Boolean): Boolean =
    !isCreating && !isChoosingDirectory
}
