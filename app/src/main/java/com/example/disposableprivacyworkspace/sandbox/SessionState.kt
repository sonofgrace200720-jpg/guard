package com.example.disposableprivacyworkspace.sandbox

enum class SessionState { CREATING, STARTING, ACTIVE, NETWORK_ERROR, DESTROYING, DESTROYED, FAILED }

data class SandboxSession(val id: String, val state: SessionState, val networkMode: NetworkMode)

enum class NetworkMode { CLOUDFLARE, TOR }
