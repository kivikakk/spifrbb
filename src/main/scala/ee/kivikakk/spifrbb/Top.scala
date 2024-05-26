package ee.kivikakk.spifrbb

import _root_.circt.stage.ChiselStage
import chisel3._
import chisel3.util._
import ee.hrzn.chryse.ChryseApp
import ee.hrzn.chryse.ChryseSubcommand
import ee.hrzn.chryse.platform.Platform
import ee.hrzn.chryse.platform.cxxrtl.CXXRTLOptions
import ee.hrzn.chryse.platform.cxxrtl.CXXRTLPlatform
import ee.hrzn.chryse.platform.ice40.IceBreakerPlatform
import ee.hrzn.chryse.tasks.BaseTask
import ee.kivikakk.spifrbb.stackyem.Instruction
import ee.kivikakk.spifrbb.stackyem.Stackyem
import ee.kivikakk.spifrbb.uart.UART
import org.rogach.scallop._

import java.io.FileOutputStream

// TODO: learn more about how to use Irrevocable right.

class Top(implicit platform: Platform) extends Module {
  override def desiredName = "spifrbb"

  val imemSize = 256
  val imem     = SRAM(imemSize, UInt(8.W), 1, 1, 0)
  val wrp      = imem.writePorts(0)

  val stackyem = Module(new Stackyem(imemSize))
  stackyem.io.imem :<>= imem.readPorts(0)

  val spifr  = Module(new SPIFlashReader)
  val reqlen = Stackyem.DEFAULT_IMEM_INIT.length
  spifr.io.req.bits.addr := 0x80_0000.U
  spifr.io.req.bits.len  := reqlen.U
  spifr.io.req.valid     := false.B
  spifr.io.res.ready     := false.B

  val our_addr = RegInit(0.U(unsignedBitLength(imemSize - 1).W))
  wrp.address := our_addr
  wrp.data    := 0.U
  wrp.enable  := false.B

  object State extends ChiselEnum {
    val sInit, sWaitFlash, sDone = Value
  }
  val state = RegInit(State.sInit)
  stackyem.io.en := false.B
  switch(state) {
    is(State.sInit) {
      spifr.io.req.valid := true.B
      state              := State.sWaitFlash
    }
    is(State.sWaitFlash) {
      when(spifr.io.res.valid) {
        spifr.io.res.ready := true.B
        wrp.data           := spifr.io.res.bits
        wrp.enable         := true.B
        our_addr           := Mux(our_addr =/= (reqlen - 1).U, our_addr + 1.U, our_addr)
        state              := Mux(our_addr =/= (reqlen - 1).U, State.sWaitFlash, State.sDone)
      }
    }
    is(State.sDone) {
      stackyem.io.en := true.B
    }
  }

  // TODO NEXT: put an enable on stackyem (start it off in an idle state),
  // load into imem from SPIFR.

  platform match {
    case plat: IceBreakerPlatform =>
      val uart = Module(new UART)
      plat.resources.uart.tx := uart.pins.tx
      uart.pins.rx           := plat.resources.uart.rx

      stackyem.io.uartRx :<>= uart.rxIo
      uart.txIo :<>= stackyem.io.uartTx

      plat.resources.spiFlash.cs    := spifr.pins.cs
      plat.resources.spiFlash.clock := spifr.pins.clock
      plat.resources.spiFlash.copi  := spifr.pins.copi
      spifr.pins.cipo               := plat.resources.spiFlash.cipo
      plat.resources.spiFlash.wp    := false.B
      plat.resources.spiFlash.hold  := false.B

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
  override val targetPlatforms                       = Seq(IceBreakerPlatform(ubtnReset = true))
  override val cxxrtlOptions                         = Some(CXXRTLOptions(clockHz = 3_000_000))

  object rom extends ChryseSubcommand("rom") with BaseTask {
    banner("Build the Stackyem ROM image, and optionally to a file.")
    val program = opt[Boolean](descr = "Program the ROM onto the iCEBreaker")
    // TODO: multiplatform support.

    def execute() = {
      val content = Stackyem.DEFAULT_IMEM_INIT
      val path    = s"$buildDir/rom.bin"
      val fos     = new FileOutputStream(path)
      fos.write(content.map(_.litValue.toByte).toArray, 0, content.length)
      fos.close()
      println(s"wrote $path")

      if (rom.program()) {
        runCmd(CmdStepProgram, Seq("iceprog", "-o", "0x800000", path))
      }
    }
  }
  override val additionalSubcommands = Seq(rom)
}
