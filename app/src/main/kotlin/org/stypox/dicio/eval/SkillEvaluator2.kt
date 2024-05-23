package org.stypox.dicio.eval

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dicio.skill.context.SkillContext
import org.dicio.skill.skill.SkillOutput
import org.dicio.skill.util.WordExtractor
import org.stypox.dicio.io.graphical.ErrorSkillOutput
import org.stypox.dicio.io.graphical.MissingPermissionsSkillOutput
import org.stypox.dicio.io.input.InputEvent
import org.stypox.dicio.io.input.InputEventsModule
import org.stypox.dicio.io.input.SttInputDevice
import org.stypox.dicio.skills.SkillHandler2
import org.stypox.dicio.ui.main.Interaction
import org.stypox.dicio.ui.main.InteractionLog
import org.stypox.dicio.ui.main.PendingQuestion
import org.stypox.dicio.ui.main.QuestionAnswer

class SkillEvaluator2(
    scope: CoroutineScope,
    private val skillContext: SkillContext,
    skillHandler: SkillHandler2,
    private val inputEventsModule: InputEventsModule,
    private val sttInputDevice: SttInputDevice?,
) {

    private val _state = MutableStateFlow(
        InteractionLog(
            interactions = listOf(),
            pendingQuestion = null,
        )
    )
    val state: StateFlow<InteractionLog> = _state

    private val skillRanker = SkillRanker(
        skillHandler.standardSkillBatch,
        skillHandler.fallbackSkill,
    )

    // must be kept up to date even when the activity is recreated, for this reason it is `var`
    var permissionRequester: suspend (Array<String>) -> Boolean = { false }

    init {
        scope.launch(Dispatchers.Default) {
            // receive input events
            inputEventsModule.events.collect(::processInputEvent)
        }
    }

    private suspend fun processInputEvent(event: InputEvent) {
        when (event) {
            is InputEvent.Error -> {
                addErrorInteractionFromPending(event.throwable)
            }
            is InputEvent.Final -> {
                _state.value = _state.value.copy(
                    pendingQuestion = PendingQuestion(
                        userInput = event.utterances[0],
                        continuesLastInteraction = skillRanker.hasAnyBatches(),
                        skillBeingEvaluated = null,
                    )
                )
                evaluateMatchingSkill(event.utterances)
            }
            InputEvent.None -> {
                _state.value = _state.value.copy(pendingQuestion = null)
            }
            is InputEvent.Partial -> {
                _state.value = _state.value.copy(
                    pendingQuestion = PendingQuestion(
                        userInput = event.utterance,
                        // the next input can be a continuation of the last interaction only if the
                        // last skill invocation provided some skill batches (which are the only way
                        // to continue an interaction/conversation)
                        continuesLastInteraction = skillRanker.hasAnyBatches(),
                        skillBeingEvaluated = null,
                    )
                )
            }
        }
    }

    private suspend fun evaluateMatchingSkill(utterances: List<String>) {
        val (chosenInput, chosenSkill, isFallback) = try {
            utterances.firstNotNullOfOrNull { input: String ->
                val inputWords = WordExtractor.extractWords(input)
                val normalizedWords = WordExtractor.normalizeWords(inputWords)
                skillRanker.getBest(skillContext, input, inputWords, normalizedWords)
                    ?.let { Triple(input, it, false) }
            } ?: run {
                val inputWords = WordExtractor.extractWords(utterances[0])
                val normalizedWords = WordExtractor.normalizeWords(inputWords)
                Triple(
                    utterances[0],
                    skillRanker.getFallbackSkill(
                        skillContext, utterances[0], inputWords, normalizedWords),
                    true
                )
            }
        } catch (throwable: Throwable) {
            addErrorInteractionFromPending(throwable)
            return
        }
        val skillInfo = chosenSkill.skill.correspondingSkillInfo

        _state.value = _state.value.copy(
            pendingQuestion = PendingQuestion(
                userInput = chosenInput,
                // the skill ranker would have discarded all batches, if the chosen skill was not
                // the continuation of the last interaction (since continuing an
                // interaction/conversation is done through the stack of batches)
                continuesLastInteraction = skillRanker.hasAnyBatches(),
                skillBeingEvaluated = chosenSkill.skill.correspondingSkillInfo,
            )
        )

        try {
            val permissions = skillInfo.neededPermissions.toTypedArray()
            if (permissions.isNotEmpty() && !permissionRequester(permissions)) {
                // permissions were not granted, show message
                addInteractionFromPending(MissingPermissionsSkillOutput(skillInfo))
                return
            }

            val output = chosenSkill.generateOutput(skillContext)

            addInteractionFromPending(output)
            output.getSpeechOutput(skillContext).let {
                if (it.isNotBlank()) {
                    withContext (Dispatchers.Main) {
                        skillContext.speechOutputDevice.speak(it)
                    }
                }
            }

            val nextSkills = output.getNextSkills(skillContext)
            if (nextSkills.isEmpty()) {
                if (!isFallback) {
                    // current conversation has ended, reset to the default batch of skills
                    skillRanker.removeAllBatches()
                }
            } else {
                skillRanker.addBatchToTop(nextSkills)
                sttInputDevice?.tryLoad(true)
            }

        } catch (throwable: Throwable) {
            addErrorInteractionFromPending(throwable)
            return
        }
    }

    private fun addErrorInteractionFromPending(throwable: Throwable) {
        Log.e(TAG, "Error while evaluating skills", throwable)
        addInteractionFromPending(ErrorSkillOutput(throwable, true))
    }

    private fun addInteractionFromPending(skillOutput: SkillOutput) {
        val log = _state.value
        val pendingUserInput = log.pendingQuestion?.userInput
        val pendingContinuesLastInteraction = log.pendingQuestion?.continuesLastInteraction
            ?: skillRanker.hasAnyBatches()
        val pendingSkill = log.pendingQuestion?.skillBeingEvaluated
        val questionAnswer = QuestionAnswer(pendingUserInput, skillOutput)

        _state.value = log.copy(
            interactions = log.interactions.toMutableList().also { inters ->
                if (pendingContinuesLastInteraction && inters.isNotEmpty()) {
                    inters[inters.size - 1] = inters[inters.size - 1].let { i -> i.copy(
                        questionsAnswers = i.questionsAnswers.toMutableList()
                            .apply { add(questionAnswer) }
                    ) }
                } else {
                    inters.add(
                        Interaction(
                            skill = pendingSkill,
                            questionsAnswers = listOf(questionAnswer)
                        )
                    )
                }
            },
            pendingQuestion = null,
        )
    }

    companion object {
        val TAG = SkillEvaluator2::class.simpleName
    }
}