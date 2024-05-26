package ee.kivikakk.spifrbb

import chisel3._
import chisel3.util._
import chisel3.util.switch
import ee.kivikakk.spifrbb.uart.RXOut

import scala.language.implicitConversions

// Default program:
// 1. Echo one byte.
// 2. Drop next byte.
// 3. Echo following byte twice.
object Stackyem {
  val DEFAULT_IMEM_INIT = Seq(
    Instruction.ReadUart,
    Instruction.WriteUart,
    Instruction.ReadUart,
    Instruction.Drop,
    Instruction.ReadUart,
    Instruction.Dup,
    Instruction.WriteUart,
    Instruction.WriteUart,
    Instruction.ResetPC,
  )
}

class StackyemDebugIO(imemSize: Int, stackSize: Int) extends Bundle {
  val pc    = Output(UInt(unsignedBitLength(imemSize - 1).W))
  val stack = Output(Vec(stackSize, UInt(8.W)))
  val sp    = Output(UInt(unsignedBitLength(stackSize - 1).W))
}

class Stackyem(
    imemSize: Int,
    stackSize: Int = 32,
    uartBufferSize: Int = 8,
) extends Module {
  if (imemSize < 2)
    // Otherwise we can't construct a PC and things get weird.
    throw new IllegalArgumentException(
      "imem must be at least 2 elements long",
    )
  if (stackSize < 2)
    // As above.
    throw new IllegalArgumentException(
      "stack must be at least 2 elements deep",
    )

  val io = IO(new Bundle {
    val en     = Input(Bool())
    val uartTx = Decoupled(UInt(8.W))
    val uartRx = Flipped(Decoupled(new RXOut))
    val imem =
      Flipped(new MemoryReadPort(UInt(8.W), unsignedBitLength(imemSize - 1)))
  })

  val debugIo = IO(new StackyemDebugIO(imemSize, stackSize))

  private val pc = RegInit(0.U(unsignedBitLength(imemSize - 1).W))
  debugIo.pc := pc

  private val stack = RegInit(VecInit(Seq.fill(stackSize)(0.U(8.W))))
  debugIo.stack := stack

  private val sp = RegInit(0.U(unsignedBitLength(stackSize - 1).W))
  debugIo.sp := sp

  private val rx =
    Queue.irrevocable(io.uartRx, uartBufferSize, useSyncReadMem = true)

  // I feel sure there's a shorthand for this ...
  private val tx = Module(
    new Queue(UInt(8.W), uartBufferSize, useSyncReadMem = true),
  )
  io.uartTx :<>= tx.io.deq

  tx.io.enq.bits  := 0.U
  tx.io.enq.valid := false.B
  rx.ready        := false.B

  object State extends ChiselEnum {
    val sHold, sAdvancePC, sAct, sImm, sReadUart = Value
  }
  private val state = RegInit(State.sHold)

  io.imem.enable  := true.B
  io.imem.address := pc

  switch(state) {
    is(State.sHold) {
      state := Mux(io.en, State.sAdvancePC, State.sHold)
    }
    is(State.sAdvancePC) {
      pc    := pc + 1.U
      state := State.sAct
    }
    is(State.sAct) {
      val insn = io.imem.data
      state := State.sAdvancePC
      when(insn === Instruction.ReadUart) {
        state := State.sReadUart
      }.elsewhen(insn === Instruction.WriteUart) {
        tx.io.enq.bits  := stack(sp - 1.U)
        tx.io.enq.valid := true.B
        sp              := sp - 1.U

        pc    := pc + 1.U
        state := State.sAct
      }.elsewhen(insn === Instruction.Dup) {
        stack(sp) := stack(sp - 1.U)
        sp        := sp + 1.U

        pc    := pc + 1.U
        state := State.sAct
      }.elsewhen(insn === Instruction.Drop) {
        sp := sp - 1.U

        pc    := pc + 1.U
        state := State.sAct
      }.elsewhen(insn === Instruction.Imm) {
        pc    := pc + 1.U
        state := State.sImm
      }.elsewhen(insn === Instruction.ResetPC) {
        state := State.sAct
        pc    := 0.U
      }
    }
    is(State.sImm) {
      stack(sp) := io.imem.data
      sp        := sp + 1.U

      pc    := pc + 1.U
      state := State.sAct
    }
    is(State.sReadUart) {
      when(rx.valid) {
        rx.ready  := true.B
        stack(sp) := Mux(rx.bits.err, 0xff.U(8.W), rx.bits.byte)
        sp        := sp + 1.U

        pc    := pc + 1.U
        state := State.sAct
      }
    }
  }
}

// ChiselEnum width is always inferred, so ResetPC being 0xff is what happens to
// ensure it's 8 bits wide here. Lol.
object Instruction extends ChiselEnum {
  val ReadUart  = Value(0x01.U)
  val WriteUart = Value(0x02.U)
  val Dup       = Value(0x03.U)
  val Drop      = Value(0x04.U)
  val Imm       = Value(0x05.U)
  val ResetPC   = Value(0xff.U)

  implicit def instruction2UInt(insn: Instruction.Type): UInt =
    insn.asUInt
}
