package ee.kivikakk.spifrbb

import chisel3._
import chisel3.experimental.ExtModule

class SPIFRBlackbox extends ExtModule {
  val clock = IO(Input(Clock()))
  val io    = IO(new SPIFlashReaderIO)
}
