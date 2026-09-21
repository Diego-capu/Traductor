package com.antigravity.translator.domain.model

/**
 * Lifecycle and execution states of the ScreenCaptureService.
 */
enum class ServiceState {
    /**
     * Service is stopped and MediaProjection resources are not active.
     */
    STOPPED,

    /**
     * Active frame capturing, OCR, translation, and overlay rendering are running.
     */
    RUNNING,

    /**
     * Screen capture ticker is paused to save battery and network,
     * but MediaProjection remains alive to avoid re-prompting the user.
     */
    PAUSED
}
