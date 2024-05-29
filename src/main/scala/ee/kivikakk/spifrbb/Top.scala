package ee.kivikakk.spifrbb

import chisel3._
import chisel3.util._
import ee.hrzn.chryse.platform.Platform
import ee.hrzn.chryse.platform.cxxrtl.CXXRTLPlatform
import ee.hrzn.chryse.platform.ecp5.ULX3SPlatform
import ee.hrzn.chryse.platform.ice40.IceBreakerPlatform
import ee.kivikakk.spifrbb.uart.UART
import ee.kivikakk.spifrbb.uart.UARTIO

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

      plat.resources.leds(0) := wonk
      plat.resources.leds(1) := ~wonk
      plat.resources.leds(2) := wonk
      plat.resources.leds(3) := ~wonk
      plat.resources.leds(4) := wonk
      plat.resources.leds(5) := ~wonk
      plat.resources.leds(6) := wonk
      plat.resources.leds(7) := ~wonk

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

      // HACK: work around CXXRTL bug. PR here but who knows if it'll get
      // merged: https://github.com/YosysHQ/yosys/pull/4417
      dontTouch(spifrcon.req.ready)

    case _ =>
      throw new NotImplementedError(s"platform ${platform.id} not supported")
  }
}
