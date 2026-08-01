package com.privatemessenger.webrtc

import android.content.Context
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class WebRTCClient(
    private val context: Context,
    private val observer: PeerConnection.Observer
) {
    private val rootEglBase: EglBase = EglBase.create()
    
    val eglBaseContext: EglBase.Context
        get() = rootEglBase.eglBaseContext
        
    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val videoCapturer by lazy { buildVideoCapturer() }
    
    private var peerConnection: PeerConnection? = null
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val localAudioSource by lazy { peerConnectionFactory.createAudioSource(MediaConstraints()) }
    
    private val localVideoTrack: VideoTrack
    private val localAudioTrack: AudioTrack
    
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    
    private var isClosed = false

    init {
        initWebRTC()
        localVideoTrack = peerConnectionFactory.createVideoTrack("local_video_track", localVideoSource)
        localAudioTrack = peerConnectionFactory.createAudioTrack("local_audio_track", localAudioSource)
    }

    private fun initWebRTC() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        val videoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val videoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        
        return PeerConnectionFactory.builder()
            .setVideoEncoderFactory(videoEncoderFactory)
            .setVideoDecoderFactory(videoDecoderFactory)
            .setAudioDeviceModule(audioDeviceModule)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    private fun buildVideoCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    fun startLocalVideo(renderer: SurfaceViewRenderer) {
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
        videoCapturer?.initialize(surfaceTextureHelper, context, localVideoSource.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)
        
        localVideoTrack.addSink(renderer)
    }

    fun initPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, observer)
        
        peerConnection?.addTrack(localVideoTrack)
        peerConnection?.addTrack(localAudioTrack)
    }

    fun call(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createOffer(sdpObserver, constraints)
    }

    fun answer(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        peerConnection?.createAnswer(sdpObserver, constraints)
    }

    fun setRemoteDescription(sessionDescription: SessionDescription, observer: SdpObserver? = null) {
        peerConnection?.setRemoteDescription(observer ?: object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sessionDescription)
    }

    fun setLocalDescription(sessionDescription: SessionDescription, observer: SdpObserver? = null) {
        peerConnection?.setLocalDescription(observer ?: object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        }, sessionDescription)
    }

    fun addIceCandidate(iceCandidate: IceCandidate) {
        peerConnection?.addIceCandidate(iceCandidate)
    }

    fun toggleVideo(enabled: Boolean) {
        localVideoTrack.setEnabled(enabled)
    }

    fun toggleAudio(enabled: Boolean) {
        localAudioTrack.setEnabled(enabled)
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        try { videoCapturer?.stopCapture() } catch(e:Exception){}
        try { videoCapturer?.dispose() } catch(e:Exception){}
        try { localVideoSource.dispose() } catch(e:Exception){}
        try { localAudioSource.dispose() } catch(e:Exception){}
        try { peerConnection?.close() } catch(e:Exception){}
        try { peerConnectionFactory.dispose() } catch(e:Exception){}
        try { rootEglBase.release() } catch(e:Exception){}
    }
}
