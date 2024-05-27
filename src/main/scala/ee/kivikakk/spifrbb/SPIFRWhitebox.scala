package ee.kivikakk.spifrbb

import chisel3._

class SPIFRWhitebox extends BlackBox {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val cs    = Input(Bool())
    val copi  = Input(Bool())
    val cipo  = Output(Bool())
  })
}
