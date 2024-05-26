package ee.kivikakk.spifrbb

import chisel3._
import chisel3.util._
import ee.hrzn.chryse.platform.Platform

class SPIFlashReaderIO extends Bundle {
  val req = Flipped(Decoupled(new Bundle {
    val addr = Output(UInt(24.W))
    val len  = Output(UInt(16.W))
  }))
  val res = Decoupled(UInt(8.W))
}

class SPIPinsIO extends Bundle {
  val copi  = Output(Bool())
  val cipo  = Input(Bool())
  val cs    = Output(Bool())
  val clock = Output(Clock())
}

class SPIFlashReader(implicit platform: Platform) extends Module {
  val io   = IO(new SPIFlashReaderIO)
  val pins = IO(new SPIPinsIO)

  // tRES1 (/CS High to Standby Mode without ID Read) and tDP (/CS High to
  // Power-down Mode) are both max 3us.
  val tres1_tdp_cycles = (platform.clockHz * 3.0 / 1_000_000.0).toInt + 1

  val sr = RegInit(0.U(32.W))
  val snd_bitcount = RegInit(
    0.U(unsignedBitLength(Seq(32, tres1_tdp_cycles).max - 1).W),
  )
  val rcv_bitcount  = RegInit(0.U(unsignedBitLength(7).W))
  val rcv_bytecount = RegInit(0.U(16.W))

  val cs = RegInit(false.B)
  pins.cs := cs

  pins.copi   := sr(31)
  pins.clock  := (cs & ~clock.asBool).asClock
  io.res.bits := sr(7, 0)
  val resValid = RegInit(false.B)
  resValid     := false.B
  io.res.valid := resValid

  object State extends ChiselEnum {
    val sIdle, sPowerDownRelease, sWaitTres1, sSendCmd, sReceiving, sPowerDown =
      Value
  }
  val state = RegInit(State.sIdle)
  io.req.ready := state === State.sIdle
  switch(state) {
    is(State.sIdle) {
      when(io.req.valid) {
        cs           := true.B
        sr           := "h_ab00_0000".U(32.W)
        snd_bitcount := 31.U
        state        := State.sPowerDownRelease
      }
    }
    is(State.sPowerDownRelease) {
      snd_bitcount := snd_bitcount - 1.U
      sr           := sr(30, 0) ## 1.U(1.W)
      when(snd_bitcount === 0.U) {
        cs           := false.B
        snd_bitcount := (tres1_tdp_cycles - 1).U
        state        := State.sWaitTres1
      }
    }
    is(State.sWaitTres1) {
      snd_bitcount := snd_bitcount - 1.U
      when(snd_bitcount === 0.U) {
        cs            := true.B
        sr            := 0x03.U(8.W) ## io.req.bits.addr
        snd_bitcount  := 31.U
        rcv_bitcount  := 7.U
        rcv_bytecount := io.req.bits.len - 1.U
        state         := State.sSendCmd
      }
    }
    is(State.sSendCmd) {
      snd_bitcount := snd_bitcount - 1.U
      sr           := sr(30, 0) ## 1.U(1.W)
      when(snd_bitcount === 0.U) {
        state := State.sReceiving
      }
    }
    is(State.sReceiving) {
      rcv_bitcount := rcv_bitcount - 1.U
      sr           := sr(30, 0) ## pins.cipo
      when(rcv_bitcount === 0.U) {
        rcv_bytecount := rcv_bytecount - 1.U
        rcv_bitcount  := 7.U
        resValid      := true.B
        when(rcv_bytecount === 0.U) {
          cs           := false.B
          snd_bitcount := (tres1_tdp_cycles - 1).U
          state        := State.sPowerDown
        }.otherwise {
          state := State.sReceiving
        }
      }
    }
    is(State.sPowerDown) {
      snd_bitcount := snd_bitcount - 1.U
      when(snd_bitcount === 0.U) {
        state := State.sIdle
      }
    }
  }
}
