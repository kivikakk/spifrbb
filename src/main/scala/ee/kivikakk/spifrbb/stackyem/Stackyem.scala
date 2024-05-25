package ee.kivikakk.spifrbb.stackyem

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
  val DEFAULT_IMEM_INIT = Seq[Data](
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

class Stackyem(
    imemInit: Seq[Data] = Stackyem.DEFAULT_IMEM_INIT,
    stackSize: Int = 32,
    uartBufferSize: Int = 8,
) extends Module {
  if (imemInit.length < 2)
    // Otherwise we can't construct a PC and things get weird.
    throw new IllegalArgumentException(
      "imemInit must be at least 2 elements long",
    )
  if (stackSize < 2)
    // As above.
    throw new IllegalArgumentException(
      "stack must be at least 2 elements deep",
    )

  val io = IO(new Bundle {
    val uartTx = Decoupled(UInt(8.W))
    val uartRx = Flipped(Decoupled(new RXOut))
  })

  val debugIo = IO(new Bundle {
    val pc    = Output(UInt(unsignedBitLength(imemInit.length - 1).W))
    val stack = Output(Vec(stackSize, UInt(8.W)))
    val sp    = Output(UInt(unsignedBitLength(stackSize - 1).W))
  })

  private val imem = VecInit(imemInit)
  private val pc   = RegInit(0.U(unsignedBitLength(imemInit.length - 1).W))
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
    val sLocatePC, sReadUart = Value
  }
  private val state = RegInit(State.sLocatePC)

  switch(state) {
    is(State.sLocatePC) {
      pc := pc + 1.U
      val el = imem(pc)
      when(el === Instruction.ReadUart) {
        state := State.sReadUart
      }.elsewhen(el === Instruction.WriteUart) {
        tx.io.enq.bits  := stack(sp - 1.U) & ~0x04.U(8.W)
        tx.io.enq.valid := true.B
        sp              := sp - 1.U
      }.elsewhen(el === Instruction.Dup) {
        stack(sp) := stack(sp - 1.U)
        sp        := sp + 1.U
      }.elsewhen(el === Instruction.Drop) {
        sp := sp - 1.U
      }.elsewhen(el === Instruction.Imm) {
        stack(sp) := imem(pc + 1.U)
        sp        := sp + 1.U
        pc        := pc + 2.U
      }.elsewhen(el === Instruction.ResetPC) {
        pc := 0.U
      }
    }
    is(State.sReadUart) {
      when(rx.valid) {
        rx.ready  := true.B
        stack(sp) := Mux(rx.bits.err, 0xff.U(8.W), rx.bits.byte)
        sp        := sp + 1.U
        state     := State.sLocatePC
      }
    }
  }
}

object Instruction extends Enumeration {
  protected case class InstructionVal(repr: UInt) extends super.Val {}

  implicit def value2InstructionVal(v: Value): InstructionVal =
    v.asInstanceOf[InstructionVal]
  implicit def instructionVal2Data(v: InstructionVal): Data =
    v.repr

  val ReadUart  = InstructionVal(0x01.U(8.W))
  val WriteUart = InstructionVal(0x02.U(8.W))
  val Dup       = InstructionVal(0x03.U(8.W))
  val Drop      = InstructionVal(0x04.U(8.W))
  val Imm       = InstructionVal(0x05.U(8.W))
  val ResetPC   = InstructionVal(0xff.U(8.W))
}
