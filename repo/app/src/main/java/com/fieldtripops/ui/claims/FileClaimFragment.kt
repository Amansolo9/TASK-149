package com.fieldtripops.ui.claims

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.fieldtripops.R
import com.fieldtripops.attachment.AttachmentImageCache
import com.fieldtripops.attachment.ImageDecoder
import com.fieldtripops.attachment.PendingAttachment
import com.fieldtripops.databinding.FragmentFileClaimBinding
import com.fieldtripops.domain.booking.ClaimValidator
import com.fieldtripops.domain.model.ClaimClassification
import com.fieldtripops.domain.model.ClaimStyle
import com.fieldtripops.ui.util.gone
import com.fieldtripops.ui.util.textString
import com.fieldtripops.ui.util.visible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class FileClaimFragment : Fragment() {

    private var _binding: FragmentFileClaimBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FileClaimViewModel by viewModel()
    private val imageCache: AttachmentImageCache by inject()

    private val pickFile = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> handlePickedFile(uri) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileClaimBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.attachEvidenceButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "image/jpeg", "image/png", "image/heic", "image/webp", "application/pdf"
                ))
            }
            pickFile.launch(intent)
        }

        binding.submitButton.setOnClickListener {
            val style = when (binding.styleGroup.checkedRadioButtonId) {
                R.id.styleNotDelivered -> ClaimStyle.SERVICE_NOT_DELIVERED
                R.id.stylePartial -> ClaimStyle.PARTIAL_DELIVERY
                else -> ClaimStyle.REFUND_ONLY
            }
            val classification = when (binding.classGroup.checkedRadioButtonId) {
                R.id.classLate -> ClaimClassification.CUSTOMER_LATE_ARRIVAL
                R.id.classSafety -> ClaimClassification.SAFETY_CONCERN
                R.id.classPricing -> ClaimClassification.PRICING_DISCREPANCY
                else -> ClaimClassification.PROVIDER_NO_SHOW
            }
            viewModel.submit(
                bookingOrderId = binding.bookingIdInput.textString(),
                style = style,
                classification = classification,
                description = binding.descriptionInput.textString()
            )
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is FileClaimViewModel.State.Idle -> binding.errorsText.gone()
                is FileClaimViewModel.State.Submitting -> {
                    binding.errorsText.gone()
                    binding.submitButton.isEnabled = false
                }
                is FileClaimViewModel.State.Submitted -> findNavController().popBackStack()
                is FileClaimViewModel.State.Error -> {
                    binding.submitButton.isEnabled = true
                    binding.errorsText.text = state.message
                    binding.errorsText.visible()
                }
            }
        }
    }

    private fun handlePickedFile(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val cr = requireContext().contentResolver
            val mime = cr.getType(uri) ?: "application/octet-stream"
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "evidence"
            val bytes = withContext(Dispatchers.IO) {
                cr.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            } ?: run {
                binding.errorsText.text = "Could not read attachment"
                binding.errorsText.visible(); return@launch
            }
            if (bytes.size > ClaimValidator.MAX_ATTACHMENT_BYTES) {
                binding.errorsText.text = "Attachment exceeds 10 MB limit"
                binding.errorsText.visible(); return@launch
            }
            if (!ClaimValidator.ALLOWED_MIME_TYPES.contains(mime.lowercase())) {
                binding.errorsText.text = "Unsupported attachment type: $mime"
                binding.errorsText.visible(); return@launch
            }
            val attachment = PendingAttachment(
                fileName = name, mimeType = mime, data = bytes
            )
            viewModel.stageAttachment(attachment)
            binding.attachmentSummary.text = "Attached: ${viewModel.attachmentCount}"

            // Show preview for image attachments using downsampled decode + LRU cache
            if (isImageMime(mime)) {
                addImagePreview(attachment.id, bytes)
            } else {
                addNonImagePreview(name, mime)
            }
        }
    }

    private fun addImagePreview(attachmentId: String, bytes: ByteArray) {
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = loadOrDecodeImage(attachmentId, bytes)
            if (bitmap != null) {
                val imageView = ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 200
                    ).apply { topMargin = 8 }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Attachment preview"
                    setImageBitmap(bitmap)
                }
                binding.previewContainer.addView(imageView)
            }
        }
    }

    private suspend fun loadOrDecodeImage(key: String, bytes: ByteArray): Bitmap? {
        // Check LRU cache first
        imageCache.get(key)?.let { return it }
        // Decode with downsampling off main thread
        val decoded = ImageDecoder.decodeFromBytes(bytes) ?: return null
        // Store in cache for repeated access
        imageCache.put(key, decoded)
        return decoded
    }

    private fun addNonImagePreview(fileName: String, mimeType: String) {
        val label = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
            text = "$fileName ($mimeType)"
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }
        binding.previewContainer.addView(label)
    }

    private fun isImageMime(mime: String): Boolean =
        mime.startsWith("image/")

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
