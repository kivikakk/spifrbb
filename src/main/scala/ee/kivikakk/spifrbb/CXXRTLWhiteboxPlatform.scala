package ee.kivikakk.spifrbb

import ee.hrzn.chryse.platform.cxxrtl.CXXRTLPlatform

class CXXRTLWhiteboxPlatform
    extends CXXRTLPlatform("wb")
    with PlatformSpecific {
  val clockHz      = 3_000_000
  var romFlashBase = BigInt("00800000", 16)
}
