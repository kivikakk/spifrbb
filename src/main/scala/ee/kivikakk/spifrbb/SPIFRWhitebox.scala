package ee.kivikakk.spifrbb

import chisel3._
import chisel3.experimental.ExtModule

class SPIFRWhitebox extends ExtModule {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val cs    = Input(Bool())
    val copi  = Input(Bool())
    val cipo  = Output(Bool())
  })
}
