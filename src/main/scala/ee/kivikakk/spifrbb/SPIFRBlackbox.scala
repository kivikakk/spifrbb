package ee.kivikakk.spifrbb

import chisel3._
import chisel3.experimental.ExtModule

class SPIFRBlackbox extends BlackBox {
  // TODO: val io = IO(new SPIFlashReaderIO { val clock = Input(Clock()) }).
  // Needs better blackbox gen in Chryse.

  val io = IO(new Bundle {
    val clock = Input(Clock())

    val req_bits_addr = Input(UInt(24.W))
    val req_bits_len  = Input(UInt(16.W))
    val req_valid     = Input(Bool())
    val req_ready     = Output(Bool())

    val res_bits  = Output(UInt(8.W))
    val res_valid = Output(Bool())
    val res_ready = Input(Bool())
  })
}
