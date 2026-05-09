package com.zipper.datingapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.zipper.datingapp.R

enum class FuturisticConfirmCloseStyle {
    /** Neon accent confirm — normal leave / close flows. */
    Default,

    /** Emphasized danger — forfeit / destructive confirmation. */
    Destructive,
}

/**
 * Replacements for stock [androidx.compose.material3.AlertDialog] on “Close?” flows:
 * dark glass panel, teal–violet gradient ring, pill actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuturisticConfirmCloseDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    titleContent: (@Composable () -> Unit)? = null,
    textContent: (@Composable () -> Unit)? = null,
    confirmStyle: FuturisticConfirmCloseStyle = FuturisticConfirmCloseStyle.Default,
    cancelLabelResId: Int = R.string.action_cancel,
    confirmLabelResId: Int = R.string.dialog_confirm_close_confirm,
    properties: DialogProperties = DialogProperties(),
) {
    val ringBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF00E5FF),
            Color(0xFF8B7CFF),
            Color(0xFFFF4081),
        ),
    )
    val panelShape = RoundedCornerShape(24.dp)
    val panelBg = Color(0xFF14141C).copy(alpha = 0.96f)
    val pillShape = RoundedCornerShape(50)

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = properties,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(ringBrush)
                .padding(2.dp)
                .clip(panelShape)
                .background(panelBg)
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (titleContent != null) {
                    titleContent()
                } else {
                    Text(
                        text = stringResource(R.string.dialog_confirm_close_title),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = 0.3.sp,
                        lineHeight = 26.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (textContent != null) {
                    textContent()
                } else {
                    Text(
                        text = stringResource(R.string.dialog_confirm_close_message),
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                    )
                }
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        shape = pillShape,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.38f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White.copy(alpha = 0.92f),
                        ),
                    ) {
                        Text(
                            text = stringResource(cancelLabelResId),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    when (confirmStyle) {
                        FuturisticConfirmCloseStyle.Default -> {
                            Button(
                                onClick = onConfirm,
                                modifier = Modifier
                                    .clip(pillShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF00ACC1), Color(0xFF7E57C2)),
                                        ),
                                        pillShape,
                                    ),
                                shape = pillShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.White,
                                ),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp,
                                ),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = stringResource(confirmLabelResId),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                        FuturisticConfirmCloseStyle.Destructive -> {
                            Button(
                                onClick = onConfirm,
                                shape = pillShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD32F2F).copy(alpha = 0.92f),
                                    contentColor = Color.White,
                                ),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp,
                                ),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = stringResource(confirmLabelResId),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
