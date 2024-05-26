package ee.kivikakk.spifrbb

import ee.hrzn.chryse.platform.cxxrtl.CXXRTLPlatform

class CXXRTLBlackboxPlatform
    extends CXXRTLPlatform(id = "bb", clockHz = 3_000_000) {}
