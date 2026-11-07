/*
 * RS Launcher splash. Part of RS Launcher, a modified version of Zalith Launcher 2
 * (GPL-3.0, Copyright (C) 2025-2026 MovTery and contributors). This file is licensed under GPL-3.0 as well.
 */
package com.movtery.zalithlauncher.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private class RsParticle(val x: Float, val y: Float, val speed: Float, val size: Float, val a: Float)

private val Orange = Color(0xFFFF8A1F)

/** Shows [content] and, for about 3 seconds, the RS Launcher intro on top of it. */
@Composable
fun RsIntroHost(content: @Composable () -> Unit) {
    var showIntro by remember { mutableStateOf(true) }
    Box(Modifier.fillMaxSize()) {
        content()
        if (showIntro) RsIntro { showIntro = false }
    }
}

@Composable
fun RsIntro(onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.6f) }
    val progress = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    var t by remember { mutableStateOf(0f) }
    val particles = remember {
        List(56) { RsParticle(Random.nextFloat(), Random.nextFloat(), 0.03f + Random.nextFloat() * 0.07f, 1.5f + Random.nextFloat() * 3.5f, Random.nextFloat()) }
    }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(900)) }
        launch { scale.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
        launch {
            while (true) {
                androidx.compose.runtime.withFrameNanos { t = it / 1_000_000_000f }
            }
        }
        progress.animateTo(1f, tween(2700, easing = LinearEasing))
        delay(150)
        exit.animateTo(0f, tween(350))
        onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(exit.value)
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0907), Color(0xFF2A1608)))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            particles.forEach { p ->
                val y = (((p.y - t * p.speed) % 1f) + 1f) % 1f
                drawCircle(
                    color = Color(0xFFFFB066).copy(alpha = 0.15f + 0.35f * p.a),
                    radius = p.size * density,
                    center = Offset(p.x * size.width, y * size.height)
                )
            }
        }
        Column(
            Modifier.alpha(alpha.value).scale(scale.value),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val shape = RoundedCornerShape(32.dp)
            Box(
                Modifier
                    .size(120.dp)
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x14FFFFFF))))
                    .border(1.dp, Color(0x66FFFFFF), shape),
                contentAlignment = Alignment.Center
            ) {
                Text("RS", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Orange)
            }
            Spacer(Modifier.height(20.dp))
            Text("RS Launcher", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(24.dp))
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.width(180.dp).clip(RoundedCornerShape(50)),
                color = Orange,
                trackColor = Color(0x33FFFFFF)
            )
        }
    }
}
