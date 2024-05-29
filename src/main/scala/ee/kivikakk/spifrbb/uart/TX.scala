package ee.kivikakk.spifrbb.uart

import chisel3._
import chisel3.util._

class TX(private val divisor: Int) extends Module {
  val io  = IO(Flipped(Irrevocable(UInt(8.W))))
  val pin = IO(Output(Bool()))

  object State extends ChiselEnum {
    val sIdle, sTx = Value
  }
  private val state = RegInit(State.sIdle)

  private val timerReg   = RegInit(0.U(unsignedBitLength(divisor - 1).W))
  private val counterReg = RegInit(0.U(unsignedBitLength(9).W))
  private val shiftReg   = RegInit(0.U(10.W))

  pin      := true.B
  io.ready := false.B

  switch(state) {
    is(State.sIdle) {
      io.ready := true.B
      when(io.valid) {
        timerReg   := (divisor - 1).U
        counterReg := 9.U
        shiftReg   := 1.U(1.W) ## io.bits ## 0.U(1.W)
        state      := State.sTx
      }
    }
    is(State.sTx) {
      pin      := shiftReg(0)
      timerReg := timerReg - 1.U
      when(timerReg === 0.U) {
        timerReg   := (divisor - 1).U
        counterReg := counterReg - 1.U
        shiftReg   := 0.U(1.W) ## shiftReg(9, 1)
        when(counterReg === 0.U) {
          state := State.sIdle
        }
      }
    }
  }
}
