package ee.kivikakk.spifrbb

import ee.hrzn.chryse.platform.cxxrtl.CXXRTLPlatform

class CXXRTLBlackboxPlatform extends CXXRTLPlatform("bb") {
  val clockHz = 3_000_000
}
