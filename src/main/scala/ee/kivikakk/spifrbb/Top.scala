package ee.kivikakk.spifrbb

import _root_.circt.stage.ChiselStage
import chisel3._
import chisel3.util._
import ee.hrzn.chryse.ChryseApp
import ee.hrzn.chryse.platform.ElaboratablePlatform
import ee.hrzn.chryse.platform.Platform
import ee.hrzn.chryse.platform.cxxrtl.CXXRTLOptions
import ee.hrzn.chryse.platform.cxxrtl.CXXRTLPlatform
import ee.hrzn.chryse.platform.ecp5.LFE5U_45F
import ee.hrzn.chryse.platform.ecp5.ULX3SPlatform
import ee.hrzn.chryse.platform.ice40.IceBreakerPlatform
import ee.hrzn.chryse.tasks.BaseTask
import ee.kivikakk.spifrbb.uart.RXOut
import ee.kivikakk.spifrbb.uart.UART
import ee.kivikakk.spifrbb.uart.UARTIO
import org.rogach.scallop._

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths

class Top(implicit platform: Platform) extends Module {
  override def desiredName = "spifrbb"

  val imemSize = 256
  val imem     = SRAM(imemSize, UInt(8.W), 1, 1, 0)
  val wrp      = imem.writePorts(0)

  val stackyem = Module(new Stackyem(imemSize))
  stackyem.io.imem :<>= imem.readPorts(0)

  val spifrcon = Wire(new SPIFlashReaderIO)

  val reqlen = Stackyem.DEFAULT_IMEM_INIT.length
  spifrcon.req.bits.addr := platform
    .asInstanceOf[PlatformSpecific]
    .romFlashBase
    .U
  spifrcon.req.bits.len := reqlen.U
  spifrcon.req.valid    := false.B
  spifrcon.res.ready    := false.B

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

    case plat: ULX3SPlatform =>
      // Something a bit wonky: it seems to get a byte before we actually write
      // to it. Experimentally that byte is FF, or more likely, a read error.
      // Seems fixable — maybe the UART briefly reads low before it goes high.
      val uart = Module(new UART)
      plat.resources.uart.tx := uart.pins.tx
      uart.pins.rx           := plat.resources.uart.rx
      stackyem.io.uart :<>= uart.io
      plat.resources.uartTxEnable := true.B

      val spifr = Module(new SPIFlashReader)
      spifrcon :<>= spifr.io
      plat.resources.spiFlash.cs    := spifr.pins.cs
      plat.resources.spiFlash.clock := spifr.pins.clock
      plat.resources.spiFlash.copi  := spifr.pins.copi
      spifr.pins.cipo               := plat.resources.spiFlash.cipo
      plat.resources.spiFlash.wp    := false.B
      plat.resources.spiFlash.hold  := false.B

      val wonk = RegInit(false.B)
      val ctr  = RegInit(10_000_000.U)
      when(ctr === 0.U) {
        ctr  := 10_000_000.U
        wonk := ~wonk
      }.otherwise(ctr := ctr - 1.U)

      plat.resources.led0 := wonk
      plat.resources.led1 := ~wonk
      plat.resources.led2 := wonk
      plat.resources.led3 := ~wonk
      plat.resources.led4 := wonk
      plat.resources.led5 := ~wonk
      plat.resources.led6 := wonk
      plat.resources.led7 := ~wonk

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
      spifr.clock := clock
      spifrcon :<>= spifr.io

    case _ =>
      throw new NotImplementedError(s"platform ${platform.id} not supported")
  }
}

trait PlatformSpecific { var romFlashBase: BigInt }

object Top extends ChryseApp {
  override val name                                  = "spifrbb"
  override def genTop()(implicit platform: Platform) = new Top
  override val targetPlatforms = Seq(
    new IceBreakerPlatform(ubtnReset = true) with PlatformSpecific {
      var romFlashBase = BigInt("00800000", 16)
    },
    new ULX3SPlatform(LFE5U_45F) with PlatformSpecific {
      var romFlashBase = BigInt("00100000", 16)
    },
  )
  override val cxxrtlOptions = Some(
    CXXRTLOptions(
      platforms = Seq(new CXXRTLWhiteboxPlatform, new CXXRTLBlackboxPlatform),
      blackboxes = Seq(classOf[SPIFRWhitebox], classOf[SPIFRBlackbox]),
      buildHooks = Seq(rom.generate),
    ),
  )
  override val additionalSubcommands = Seq(rom)

  object rom
      extends this.ChryseSubcommand("rom", addBoardOption = true)
      with BaseTask {
    banner("Build the Stackyem ROM image, and optionally to a file.")
    val program = opt[Boolean](descr = "Program the ROM onto the iCEBreaker")

    def generate(platform: ElaboratablePlatform): String = {
      val romFlashBase = platform.asInstanceOf[PlatformSpecific].romFlashBase

      Files.createDirectories(Paths.get(buildDir))

      val content = Stackyem.DEFAULT_IMEM_INIT.map(_.litValue.toByte)
      val binPath = s"$buildDir/rom.bin"
      val fos     = new FileOutputStream(binPath)
      fos.write(content.toArray, 0, content.length)
      fos.close()
      println(s"wrote $binPath")

      // platform arg is only used for CXXRTL soooooooo TOD1
      writePath(s"$buildDir/rom.cc") { wr =>
        wr.print("const uint8_t spi_flash_content[] = \"");
        wr.print(content.map(b => f"\\$b%03o").mkString)
        wr.println("\";");
        wr.println(f"const uint32_t spi_flash_base = 0x$romFlashBase%x;");
        wr.println(f"const uint32_t spi_flash_length = 0x${content.length}%x;");
      }

      binPath
    }

    def execute() = {
      val platform =
        if (targetPlatforms.length > 1)
          targetPlatforms.find(_.id == this.board.get()).get
        else
          targetPlatforms(0)

      val binPath      = generate(platform)
      val romFlashBase = platform.asInstanceOf[PlatformSpecific].romFlashBase
      if (rom.program()) {
        runCmd(
          CmdStepProgram,
          Seq("iceprog", "-o", f"0x$romFlashBase%x", binPath),
        )
      }
    }
  }
}
