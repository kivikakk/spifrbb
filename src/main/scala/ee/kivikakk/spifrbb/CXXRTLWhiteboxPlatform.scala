package ee.kivikakk.spifrbb

import ee.hrzn.chryse.platform.cxxrtl.CXXRTLPlatform

class CXXRTLWhiteboxPlatform extends CXXRTLPlatform("wb") {
  val clockHz = 3_000_000
}
