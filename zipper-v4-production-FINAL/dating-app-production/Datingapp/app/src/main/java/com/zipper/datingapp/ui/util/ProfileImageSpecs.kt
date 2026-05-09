package com.zipper.datingapp.ui.util

import coil.size.Size

/** Shared Coil decode sizes so discovery grid, prefetch, and small surfaces stay consistent. */
object ProfileImageSpecs {

    /** Primary thumbnail for discovery grid cards — balances sharpness vs cellular bandwidth. */
    val DECK_THUMBNAIL_SIZE: Size = Size(720, 1024)

    /** Live PIP poster (~118×158 dp); downsampling avoids full-resolution pulls on cellular. */
    val PIP_POSTER_SIZE: Size = Size(320, 428)

    /** Profile sheet avatar circle (~100 dp); aligned with existing SubcomposeAsyncImage usage. */
    val PROFILE_HEADER_AVATAR_SIZE: Size = Size(400, 400)
}
