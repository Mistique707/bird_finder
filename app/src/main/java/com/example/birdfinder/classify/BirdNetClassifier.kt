package com.example.birdfinder.classify

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

/**
 * BirdNET 2.4 (Global 6K) TFLite classifier.
 *
 * Verified I/O (`woheller69/whoBIRD-TFlite`):
 *   - Audio model input  : float32 `[1, 144000]` raw mono PCM @ 48 kHz, int16 cast
 *                          to float without normalization.
 *   - Audio model output : float32 `[1, N]` raw logits, sigmoid applied here.
 *   - Meta  model input  : float32 `[1, 3]` = `[lat, lon, weekEncoded]`.
 *   - Meta  model output : float32 `[1, N]` per-class presence prior in `[0,1]`.
 *
 * Final per-class confidence = sigmoid(logits) * metaPrior (if a prior is set).
 *
 * @property useMetaModel when false, classifier returns sigmoid(logits) only.
 */
class BirdNetClassifier(
    context: Context,
    audioModelAsset: String = "BirdNET_GLOBAL_6K_V2.4_Model_FP16.tflite",
    metaModelAsset: String = "BirdNET_GLOBAL_6K_V2.4_MData_Model_V2_FP16.tflite",
    labelsAsset: String = "labels_en.txt",
    private val useMetaModel: Boolean = true,
) : BirdClassifier {

    override val modelName: String = "BirdNET"
    override val modelVersion: String =
        audioModelAsset.removePrefix("BirdNET_").removeSuffix(".tflite")
    override val sampleRateHz: Int = 48_000
    override val inputSamples: Int = 144_000

    private val mutex = Mutex()
    private val labels: Labels = Labels.fromAssets(context.applicationContext, labelsAsset)

    private val audioInterpreter: Interpreter = newInterpreter(context, audioModelAsset)
    private val metaInterpreter: Interpreter? =
        if (useMetaModel) runCatching { newInterpreter(context, metaModelAsset) }.getOrNull() else null

    private val numClasses: Int

    /** Buffer reused per inference for the audio model input. */
    private val audioInput: ByteBuffer =
        ByteBuffer.allocateDirect(inputSamples * 4).order(ByteOrder.nativeOrder())

    /** Buffer reused per inference for the audio model output. */
    private val audioOutput: ByteBuffer

    /** Cached presence prior; null when no location has been set or meta disabled. */
    private var metaPrior: FloatArray? = null

    init {
        val inShape = audioInterpreter.getInputTensor(0).shape()
        val outShape = audioInterpreter.getOutputTensor(0).shape()
        require(inShape.size == 2 && inShape[0] == 1 && inShape[1] == inputSamples) {
            "Unexpected audio input shape ${inShape.toList()}; expected [1, $inputSamples]"
        }
        require(outShape.size == 2 && outShape[0] == 1) {
            "Unexpected audio output shape ${outShape.toList()}"
        }
        numClasses = outShape[1]
        require(numClasses == labels.size) {
            "Model has $numClasses classes but labels file has ${labels.size} entries"
        }
        audioOutput = ByteBuffer.allocateDirect(numClasses * 4).order(ByteOrder.nativeOrder())

        metaInterpreter?.let { meta ->
            val mIn = meta.getInputTensor(0).shape()
            val mOut = meta.getOutputTensor(0).shape()
            require(mIn.size == 2 && mIn[0] == 1 && mIn[1] == 3) {
                "Unexpected meta input shape ${mIn.toList()}; expected [1, 3]"
            }
            require(mOut.size == 2 && mOut[0] == 1 && mOut[1] == numClasses) {
                "Meta output ${mOut.toList()} doesn't match audio output [1, $numClasses]"
            }
        }
    }

    override suspend fun classify(
        window: FloatArray,
        minConfidence: Float,
    ): List<Detection> = withContext(Dispatchers.Default) {
        require(window.size == inputSamples) {
            "window has ${window.size} samples; expected $inputSamples"
        }
        mutex.withLock {
            audioInput.rewind()
            for (s in window) audioInput.putFloat(s)
            audioInput.rewind()

            audioOutput.rewind()
            audioInterpreter.run(audioInput, audioOutput)
            audioOutput.rewind()

            val prior = metaPrior
            val out = ArrayList<Detection>(8)
            for (i in 0 until numClasses) {
                val logit = audioOutput.float
                var p = sigmoid(logit)
                if (prior != null) p *= prior[i]
                if (p >= minConfidence) {
                    val label = labels[i]
                    if (label.isBirdLike) {
                        out += Detection(
                            speciesCommon = label.common,
                            speciesScientific = label.scientific,
                            confidence = p,
                        )
                    }
                }
            }
            out.sortByDescending { it.confidence }
            out
        }
    }

    override suspend fun setLocationPrior(
        latitude: Double,
        longitude: Double,
        dayOfYearUtc: Int,
    ) = withContext(Dispatchers.Default) {
        val meta = metaInterpreter ?: return@withContext
        mutex.withLock {
            val input = ByteBuffer.allocateDirect(3 * 4).order(ByteOrder.nativeOrder())
            input.putFloat(latitude.toFloat())
            input.putFloat(longitude.toFloat())
            input.putFloat(WeekEncoding.encode(dayOfYearUtc))
            input.rewind()
            val output = ByteBuffer.allocateDirect(numClasses * 4).order(ByteOrder.nativeOrder())
            meta.run(input, output)
            output.rewind()
            val prior = FloatArray(numClasses)
            for (i in 0 until numClasses) prior[i] = output.float
            metaPrior = prior
        }
    }

    override fun close() {
        audioInterpreter.close()
        metaInterpreter?.close()
    }

    private fun sigmoid(x: Float): Float = (1.0f / (1.0f + exp(-x)))

    private fun newInterpreter(context: Context, asset: String): Interpreter {
        val fd = context.assets.openFd(asset)
        val mapped: MappedByteBuffer = FileInputStream(fd.fileDescriptor).channel.use { ch ->
            ch.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
        val opts = Interpreter.Options().apply {
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
            // TODO: try NnApiDelegate / GpuDelegate as opt-in.
        }
        return Interpreter(mapped, opts)
    }
}
