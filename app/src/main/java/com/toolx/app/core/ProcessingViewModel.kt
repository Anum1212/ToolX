package com.toolx.app.core

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProcessingState {
    data object Idle : ProcessingState
    /** [progress] is 0..1, or null when the operation can't report progress. */
    data class Running(val progress: Float?) : ProcessingState
    data class Done(val media: SavedMedia) : ProcessingState
    data class Failed(val message: String) : ProcessingState
}

/**
 * Runs one export at a time for a tool screen and survives rotation.
 * Each tool screen gets its own instance (scoped to its navigation entry).
 */
class ProcessingViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val state: StateFlow<ProcessingState> = _state.asStateFlow()

    private var job: Job? = null

    fun run(task: suspend (context: Context, onProgress: (Float) -> Unit) -> SavedMedia) {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = ProcessingState.Running(null)
            _state.value = try {
                val media = task(getApplication()) { p ->
                    if (_state.value is ProcessingState.Running) {
                        _state.value = ProcessingState.Running(p.coerceIn(0f, 1f))
                    }
                }
                ProcessingState.Done(media)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProcessingState.Failed(e.message ?: "Something went wrong")
            } catch (e: OutOfMemoryError) {
                ProcessingState.Failed("Not enough memory for this file")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = ProcessingState.Idle
    }

    /** Clears a finished/failed result, e.g. when the user picks a new input. */
    fun reset() {
        if (job?.isActive != true) _state.value = ProcessingState.Idle
    }
}
