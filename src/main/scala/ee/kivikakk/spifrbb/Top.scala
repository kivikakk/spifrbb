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

class Top(implicit platform: Platform) extends Module {
  override def desiredName = "spifrbb"

  val stackyem = Module(new Stackyem)
  val uart     = Module(new UART)

  stackyem.io.uartRx :<>= uart.rxIo
  uart.txIo :<>= stackyem.io.uartTx

  platform match {
    case platform: IceBreakerPlatform =>
      platform.resources.uart.tx := uart.pins.tx
      uart.pins.rx               := platform.resources.uart.rx

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
