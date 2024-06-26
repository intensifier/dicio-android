package org.stypox.dicio.ui.util

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.Skill
import org.dicio.skill.skill.SkillInfo
import org.dicio.skill.skill.SkillOutput
import org.stypox.dicio.skills.calculator.CalculatorInfo
import org.stypox.dicio.skills.fallback.text.TextFallbackOutput
import org.stypox.dicio.skills.lyrics.LyricsInfo
import org.stypox.dicio.skills.navigation.NavigationInfo
import org.stypox.dicio.skills.navigation.NavigationOutput
import org.stypox.dicio.skills.telephone.ConfirmCallOutput
import org.stypox.dicio.skills.telephone.ConfirmedCallOutput
import org.stypox.dicio.skills.telephone.TelephoneInfo
import org.stypox.dicio.skills.timer.TimerInfo
import org.stypox.dicio.skills.timer.TimerOutput
import org.stypox.dicio.skills.weather.WeatherInfo
import org.stypox.dicio.ui.home.Interaction
import org.stypox.dicio.ui.home.InteractionLog
import org.stypox.dicio.ui.home.PendingQuestion
import org.stypox.dicio.ui.home.QuestionAnswer
import org.stypox.dicio.ui.home.SttState
import java.io.IOException


class UserInputPreviews : CollectionPreviewParameterProvider<String>(listOf(
    "",
    "When",
    "What's the weather?",
    LoremIpsum(50).values.first(),
))

class SkillInfoPreviews : CollectionPreviewParameterProvider<SkillInfo>(listOf(
    WeatherInfo,
    CalculatorInfo,
    object : SkillInfo("test") {
        override fun name(context: Context) = "Long name lorem ipsum dolor sit amet, consectetur"
        override fun sentenceExample(context: Context) = "Long sentence ".repeat(20)
        @Composable override fun icon() = rememberVectorPainter(Icons.Default.Extension)
        override fun isAvailable(ctx: SkillContext) = true
        override fun build(ctx: SkillContext) = error("not-implemented preview-only")
    },
))

class SkillOutputPreviews : CollectionPreviewParameterProvider<SkillOutput>(listOf(
    TextFallbackOutput(),
))

class InteractionLogPreviews : CollectionPreviewParameterProvider<InteractionLog>(listOf(
    InteractionLog(
        listOf(),
        null,
    ),
    InteractionLog(
        listOf(),
        PendingQuestion(
            userInput = "What's the weather?",
            continuesLastInteraction = true,
            skillBeingEvaluated = null,
        ),
    ),
    InteractionLog(
        listOf(),
        PendingQuestion(
            userInput = LoremIpsum(50).values.first(),
            continuesLastInteraction = false,
            skillBeingEvaluated = SkillInfoPreviews().values.first(),
        ),
    ),
    InteractionLog(
        listOf(
            Interaction(
                skill = NavigationInfo,
                questionsAnswers = listOf(
                    QuestionAnswer("Take me to Paris", NavigationOutput("Paris"))
                )
            ),
            Interaction(
                skill = TimerInfo,
                questionsAnswers = listOf(
                    QuestionAnswer("Set a timer", TimerOutput.SetAskDuration { TextFallbackOutput() })
                )
            )
        ),
        PendingQuestion(
            userInput = "Twenty",
            continuesLastInteraction = true,
            skillBeingEvaluated = null,
        ),
    ),
    InteractionLog(
        listOf(
            Interaction(
                skill = TelephoneInfo,
                questionsAnswers = listOf(
                    QuestionAnswer("call mom", ConfirmCallOutput("Mom", "1234567890")),
                    QuestionAnswer("yes", ConfirmedCallOutput("1234567890")),
                )
            )
        ),
        PendingQuestion(
            userInput = "lyrics i'm working on a dream",
            continuesLastInteraction = false,
            skillBeingEvaluated = LyricsInfo,
        ),
    ),
))

class SttStatesPreviews : CollectionPreviewParameterProvider<SttState>(listOf(
    SttState.NoMicrophonePermission,
    SttState.NotInitialized,
    SttState.NotAvailable,
    SttState.NotDownloaded,
    SttState.Downloading(987654, 0),
    SttState.Downloading(987654, 1234567),
    SttState.ErrorDownloading(IOException("ErrorDownloading exception")),
    SttState.Downloaded,
    SttState.Unzipping(765432, 0),
    SttState.Unzipping(3365432, 9876543),
    SttState.ErrorUnzipping(Exception("ErrorUnzipping exception")),
    SttState.NotLoaded,
    SttState.Loading(true),
    SttState.Loading(false),
    SttState.ErrorLoading(Exception("ErrorLoading exception")),
    SttState.Loaded,
    SttState.Listening,
))
