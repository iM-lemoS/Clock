package io.github.imlemos.clock.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.glass.GlassBackdrop
import io.github.imlemos.clock.core.designsystem.glass.GlassSurface
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

/** Round glass button, e.g. the floating "+". The icon uses the accent: it is an action point. */
@Composable
fun GlassIconButton(
    backdrop: GlassBackdrop,
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    tint: Color = ClockTheme.colors.accent,
) {
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .size(size)
            .clip(CapsuleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = tint, modifier = Modifier.size(32.dp))
    }
}
