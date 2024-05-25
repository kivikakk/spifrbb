package ee.kivikakk.spifrbb.uart

import chisel3._

class RXOut extends Bundle {
  val byte = UInt(8.W)
  val err  = Bool()
}
