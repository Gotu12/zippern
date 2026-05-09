package com.zipper.datingapp.auth

fun checkAccess(
    isGuest: Boolean,
    onBlocked: () -> Unit,
    action: () -> Unit
) {
    if (isGuest) {
        onBlocked()
    } else {
        action()
    }
}
