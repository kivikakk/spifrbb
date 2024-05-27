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
import ee.kivikakk.spifrbb.uart.RXOut
import ee.kivikakk.spifrbb.uart.UART
import ee.kivikakk.spifrbb.uart.UARTIO
import org.rogach.scallop._

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths

// TODO: learn more about how to use Irrevocable right.

class Top(implicit platform: Platform) extends Module {
  override def desiredName = "spifrbb"

  val imemSize = 256
  val imem     = SRAM(imemSize, UInt(8.W), 1, 1, 0)
  val wrp      = imem.writePorts(0)

  val stackyem = Module(new Stackyem(imemSize))
  stackyem.io.imem :<>= imem.readPorts(0)

  val spifrcon = Wire(new SPIFlashReaderIO)

  val reqlen = Stackyem.DEFAULT_IMEM_INIT.length
  spifrcon.req.bits.addr := 0x80_0000.U
  spifrcon.req.bits.len  := reqlen.U
  spifrcon.req.valid     := false.B
  spifrcon.res.ready     := false.B

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
      spifrcon.req.valid := true.B
      state              := State.sWaitFlash
    }
    is(State.sWaitFlash) {
      when(spifrcon.res.valid) {
        spifrcon.res.ready := true.B
        wrp.data           := spifrcon.res.bits
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
      stackyem.io.uart :<>= uart.io

      val spifr = Module(new SPIFlashReader)
      spifrcon :<>= spifr.io
      plat.resources.spiFlash.cs    := spifr.pins.cs
      plat.resources.spiFlash.clock := spifr.pins.clock
      plat.resources.spiFlash.copi  := spifr.pins.copi
      spifr.pins.cipo               := plat.resources.spiFlash.cipo
      plat.resources.spiFlash.wp    := false.B
      plat.resources.spiFlash.hold  := false.B

    case _: CXXRTLWhiteboxPlatform =>
      val io = IO(Flipped(new UARTIO))
      stackyem.io.uart :<>= io

      val spifr = Module(new SPIFlashReader)
      spifrcon :<>= spifr.io

      val wb = Module(new SPIFRWhitebox)
      wb.io.clock     := clock
      wb.io.cs        := spifr.pins.cs
      wb.io.copi      := spifr.pins.copi
      spifr.pins.cipo := wb.io.cipo

    case _: CXXRTLBlackboxPlatform =>
      val io = IO(Flipped(new UARTIO))
      stackyem.io.uart :<>= io

      val spifr = Module(new SPIFRBlackbox)
      // XXX: improve blackbox generation.
      spifr.io.clock         := clock
      spifr.io.req_bits_addr := spifrcon.req.bits.addr
      spifr.io.req_bits_len  := spifrcon.req.bits.len
      spifr.io.req_valid     := spifrcon.req.valid
      spifrcon.req.ready     := spifr.io.req_ready
      spifrcon.res.bits      := spifr.io.res_bits
      spifrcon.res.valid     := spifr.io.res_valid
      spifr.io.res_ready     := spifrcon.res.ready

    case _ =>
      throw new NotImplementedError(s"platform ${platform.id} not supported")
  }
}

object Top extends ChryseApp {
  override val name                                  = "spifrbb"
  override def genTop()(implicit platform: Platform) = new Top
  override val targetPlatforms                       = Seq(IceBreakerPlatform(ubtnReset = true))
  override val cxxrtlOptions = Some(
    CXXRTLOptions(
      platforms = Seq(new CXXRTLWhiteboxPlatform, new CXXRTLBlackboxPlatform),
      blackboxes = Seq(classOf[SPIFRWhitebox], classOf[SPIFRBlackbox]),
    ),
  )

  object rom extends ChryseSubcommand("rom") with BaseTask {
    banner("Build the Stackyem ROM image, and optionally to a file.")
    val program = opt[Boolean](descr = "Program the ROM onto the iCEBreaker")
    // TODO: multiplatform support.

    private val flashBase = 0x80_0000;

    def execute() = {
      Files.createDirectories(Paths.get(buildDir))

      val content = Stackyem.DEFAULT_IMEM_INIT.map(_.litValue.toByte)
      val path    = s"$buildDir/rom.bin"
      val fos     = new FileOutputStream(path)
      fos.write(content.toArray, 0, content.length)
      fos.close()
      println(s"wrote $path")

      // HACK: somehow hook this to the cxxsim action
      writePath(s"$buildDir/rom.cc") { wr =>
        wr.print("const uint8_t spi_flash_content[] = \"");
        wr.print(content.map(b => f"\\$b%03o").mkString)
        wr.println("\";");
        wr.println(f"const uint32_t spi_flash_base = 0x$flashBase%x;");
        wr.println(f"const uint32_t spi_flash_length = 0x${content.length}%x;");
      }

      if (rom.program()) {
        runCmd(CmdStepProgram, Seq("iceprog", "-o", f"0x$flashBase%x", path))
      }
    }
  }
  override val additionalSubcommands = Seq(rom)
}
