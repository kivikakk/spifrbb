package ee.kivikakk.spifrbb

import _root_.circt.stage.ChiselStage
import chisel3._
import chisel3.util._
import ee.hrzn.chryse.ChryseApp
import ee.hrzn.chryse.platform.Platform
import ee.hrzn.chryse.platform.cxxrtl.CXXRTLOptions
import ee.hrzn.chryse.platform.cxxrtl.CXXRTLPlatform
import ee.hrzn.chryse.platform.ice40.IceBreakerPlatform

import uart.UART
import stackyem.Stackyem

// TODO: learn more about how to use Irrevocable right.

class Top(implicit platform: Platform) extends Module {
  override def desiredName = "spifrbb"

  val stackyem = Module(new Stackyem)

  platform match {
    case plat: IceBreakerPlatform =>
      val uart = Module(new UART)
      plat.resources.uart.tx := uart.pins.tx
      uart.pins.rx           := plat.resources.uart.rx

      stackyem.io.uartRx :<>= uart.rxIo
      uart.txIo :<>= stackyem.io.uartTx

    case plat: CXXRTLPlatform =>
      // We're not measuring the UART, so expose the UART module interface
      // directly instead of the pins. Skip the error part of the RX interface.
      // Note that we flip the names so they make sense from CXXRTL's point of
      // view — this is *its* IO, not ours.
      val io = IO(new Bundle {
        val uart_tx = Flipped(Decoupled(UInt(8.W)))
        val uart_rx = Decoupled(UInt(8.W))
      })

      io.uart_rx :<>= stackyem.io.uartTx

      stackyem.io.uartRx.valid     := io.uart_tx.valid
      stackyem.io.uartRx.bits.byte := io.uart_tx.bits
      stackyem.io.uartRx.bits.err  := false.B
      io.uart_tx.ready             := stackyem.io.uartRx.ready

    case _ =>
      throw new NotImplementedError(s"platform ${platform.id} not supported")
  }
}

object Top extends ChryseApp {
  override val name                                  = "spifrbb"
  override def genTop()(implicit platform: Platform) = new Top
  override val targetPlatforms                       = Seq(IceBreakerPlatform())
  override val cxxrtlOptions                         = Some(CXXRTLOptions(clockHz = 3_000_000))
}
