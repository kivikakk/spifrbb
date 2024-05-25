package ee.kivikakk.spifrbb.uart

import chisel3._
import chisel3.util._
import ee.hrzn.chryse.platform.Platform

class UART(val baud: Int = 9600, val bufferLength: Int = 32)(implicit
    platform: Platform,
) extends Module {
  private val divisor = platform.clockHz / baud

  val txIo = IO(Flipped(Decoupled(UInt(8.W))))
  val rxIo = IO(Decoupled(new RXOut))
  val pins = IO(new Bundle {
    val rx = Input(Bool())
    val tx = Output(Bool())
  })

  val rx = Module(new RX(divisor))
  rxIo :<>= Queue(rx.io, bufferLength, useSyncReadMem = true)
  rx.pin := pins.rx

  val tx = Module(new TX(divisor))
  tx.io :<>= Queue(txIo, bufferLength, useSyncReadMem = true)
  pins.tx := tx.pin
}
