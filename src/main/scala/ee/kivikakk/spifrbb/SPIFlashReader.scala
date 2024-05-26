package ee.kivikakk.spifrbb

import chisel3._
import chisel3.util._

class SPIFlashReaderIO extends Bundle {
  val req = Flipped(Decoupled(new Bundle {
    val addr = Output(UInt(24.W))
    val len  = Output(UInt(16.W))
  }))
  val res = Decoupled(UInt(8.W))
}

class SPIPinsIO extends Bundle {
  val copi  = Output(Bool())
  val cipo  = Input(Bool())
  val cs    = Output(Bool())
  val clock = Output(Clock())
}

class SPIFlashReader extends Module {
  val io   = IO(new SPIFlashReaderIO)
  val pins = IO(new SPIPinsIO)
}
