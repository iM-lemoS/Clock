package io.github.imlemos.clock.core.designsystem.component

import io.github.imlemos.clock.core.designsystem.rememberHaptics
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.imlemos.clock.core.designsystem.glass.CapsuleShape
import io.github.imlemos.clock.core.designsystem.glass.GlassBackdrop
import io.github.imlemos.clock.core.designsystem.glass.GlassSurface
import io.github.imlemos.clock.core.designsystem.theme.ClockTheme

data class NavBarItem(
    val key: Any,
    @param:DrawableRes val icon: Int,
    val label: String,
)

/** Floating capsule navigation bar made of glass. */
@Composable
fun FloatingNavBar(
    backdrop: GlassBackdrop,
    items: List<NavBarItem>,
    selectedKey: Any?,
    onSelect: (NavBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(backdrop = backdrop, modifier = modifier.heightIn(min = 64.dp)) {
        // Equal widths edge to edge: the outer tabs sit in the corners, the others evenly between.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                NavBarButton(item = item, selected = item.key == selectedKey, onClick = { onSelect(item) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavBarButton(item: NavBarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ClockTheme.colors
    val haptics = rememberHaptics()
    val iconTint by animateColorAsState(if (selected) colors.accent else colors.contentMuted, label = "navIcon")
    val labelColor by animateColorAsState(if (selected) colors.content else colors.contentMuted, label = "navLabel")
    val pill by animateColorAsState(
        if (selected) colors.content.copy(alpha = if (colors.isDark) 0.12f else 0.08f) else Color.Transparent,
        label = "navPill",
    )
    Column(
        modifier = modifier
            .clip(CapsuleShape)
            .background(pill, CapsuleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = {
                if (!selected) haptics.tick()
                onClick()
            })
            .heightIn(min = 52.dp)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(item.icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        // Long labels ("Temporizadores") shrink to fit rather than being cut.
        BasicText(
            text = item.label,
            style = ClockTheme.typography.caption.copy(color = labelColor),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = ClockTheme.typography.caption.fontSize),
        )
    }
}
