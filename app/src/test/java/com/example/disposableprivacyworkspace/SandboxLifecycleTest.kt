package com.example.disposableprivacyworkspace

import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.disposableprivacyworkspace.sandbox.SessionState

class SandboxLifecycleTest {
 @Test fun statesAreExplicit(){ assertEquals("ACTIVE", SessionState.ACTIVE.name); assertEquals("DESTROYED", SessionState.DESTROYED.name) }
}
