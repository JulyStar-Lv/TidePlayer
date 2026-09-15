package io.github.julystar.musicapp.car.presentation.icon

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import io.github.julystar.musicapp.car.presentation.R

enum class CarIcon(@DrawableRes val resourceId: Int) {
    Home(R.drawable.car_ic_home),
    Library(R.drawable.car_ic_library),
    Playlists(R.drawable.car_ic_playlists),
    Settings(R.drawable.car_ic_settings),
    Songs(R.drawable.car_ic_songs),
    Albums(R.drawable.car_ic_albums),
    Artists(R.drawable.car_ic_artists),
    Close(R.drawable.car_ic_close),
    Previous(R.drawable.car_ic_previous),
    Play(R.drawable.car_ic_play),
    Pause(R.drawable.car_ic_pause),
    Next(R.drawable.car_ic_next),
    Search(R.drawable.car_ic_search),
    Collapse(R.drawable.car_ic_collapse),
    Fullscreen(R.drawable.car_ic_fullscreen),
    ExitFullscreen(R.drawable.car_ic_exit_fullscreen),
    Heart(R.drawable.car_ic_heart),
    HeartFilled(R.drawable.car_ic_heart_filled),
    More(R.drawable.car_ic_more),
    Queue(R.drawable.car_ic_queue),
    AddToQueue(R.drawable.car_ic_add_to_queue),
    Edit(R.drawable.car_ic_edit),
    RepeatAll(R.drawable.car_ic_repeat_all),
    RepeatOne(R.drawable.car_ic_repeat_one),
    PreviousLarge(R.drawable.car_ic_previous_large),
    PauseLarge(R.drawable.car_ic_pause_large),
    NextLarge(R.drawable.car_ic_next_large),
    Back(R.drawable.car_ic_back),
    Shuffle(R.drawable.car_ic_shuffle),
}

@Composable
fun CarIcon(
    icon: io.github.julystar.musicapp.car.presentation.icon.CarIcon,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(icon.resourceId),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}
