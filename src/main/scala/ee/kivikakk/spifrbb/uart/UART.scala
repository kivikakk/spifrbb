package ee.kivikakk.spifrbb.uart

import chisel3._
import chisel3.util._
import ee.hrzn.chryse.platform.Platform

class UARTIO extends Bundle {
  val tx = Flipped(Decoupled(UInt(8.W)))
  val rx = Decoupled(new RXOut)
}

class UART(val baud: Int = 9600, val bufferLength: Int = 32)(implicit
    platform: Platform,
) extends Module {
  private val divisor = platform.clockHz / baud

  val io = IO(new UARTIO)
  val pins = IO(new Bundle {
    val rx = Input(Bool())
    val tx = Output(Bool())
  })

  val rx = Module(new RX(divisor))
  io.rx :<>= Queue(rx.io, bufferLength, useSyncReadMem = true)
  rx.pin := pins.rx

  val tx = Module(new TX(divisor))
  tx.io :<>= Queue.irrevocable(io.tx, bufferLength, useSyncReadMem = true)
  pins.tx := tx.pin
}
