package ee.kivikakk.spifrbb

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import chisel3.util._
import ee.hrzn.chryse.platform.Platform
import ee.kivikakk.spifrbb.uart.RXOut
import ee.kivikakk.spifrbb.uart.UARTIO
import org.scalatest.flatspec.AnyFlatSpec

class StackyemStaticMem(imem: Seq[Data], stackSize: Int) extends Module {
  val stackyem = Module(
    new Stackyem(imemSize = imem.length, stackSize = stackSize),
  )

  val io = IO(Flipped(new UARTIO))
  stackyem.io.uart :<>= io

  val debugIo = IO(
    new StackyemDebugIO(imemSize = imem.length, stackSize = stackSize),
  )
  debugIo :<>= stackyem.debugIo

  private val rom = VecInit(imem.map(_.asUInt))
  stackyem.io.en := true.B
  stackyem.io.imem.data := RegEnable(
    rom(stackyem.io.imem.address),
    0.U,
    stackyem.io.imem.enable,
  )
}

class StackyemSpec extends AnyFlatSpec {
  behavior.of("Stackyem")

  implicit val plat: Platform = new Platform {
    val id      = "stackyemspec"
    val clockHz = 8
  }

  it should "read a byte onto the stack and dup it" in {
    simulate(
      new StackyemStaticMem(
        imem = Seq(Instruction.ReadUart, Instruction.Dup),
        stackSize = 2,
      ),
    ) { c =>
      c.reset.poke(true)
      c.clock.step()
      c.reset.poke(false)

      c.debugIo.stack(0).expect(0)
      c.debugIo.stack(1).expect(0)

      c.debugIo.pc.expect(0)

      c.clock.step(2)
      c.debugIo.pc.expect(1, "pc adv")

      c.clock.step()
      // Still waiting for UART.
      c.debugIo.pc.expect(1, "pc wait")
      c.debugIo.sp.expect(0, "sp wait")

      c.io.rx.bits.byte.poke(0xac)
      c.io.rx.bits.err.poke(false)
      c.io.rx.valid.poke(true)

      c.clock.step()
      c.debugIo.pc.expect(1, "pc wait")
      c.debugIo.sp.expect(0, "sp wait")

      c.clock.step()
      c.debugIo.pc.expect(1, "pc wait")
      c.debugIo.sp.expect(1, "sp adv")
      c.debugIo.stack(0).expect(0xac, "stack set")

      c.clock.step(2)
      c.debugIo.pc.expect(0, "pc wait")
      c.debugIo.sp.expect(0, "sp wrap")
      c.debugIo.stack(1).expect(0xac, "stack still")
    }
  }

  it should "write to uart" in {
    simulate(
      new StackyemStaticMem(
        imem = Seq(Instruction.Imm, 0x45.U, Instruction.WriteUart),
        stackSize = 2,
      ),
    ) { c =>
      c.reset.poke(true)
      c.clock.step()
      c.reset.poke(false)

      c.debugIo.pc.expect(0)
      c.debugIo.sp.expect(0)
      c.debugIo.stack(0).expect(0)

      c.clock.step(4)
      c.debugIo.pc.expect(2, "pc adv")
      c.debugIo.sp.expect(1, "sp adv")
      c.debugIo.stack(0).expect(0x45, "stack set")
      c.io.tx.bits.expect(0, "uart tx bits wait")
      c.io.tx.valid.expect(0, "uart tx valid wait")

      c.clock.step(2)
      c.io.tx.bits.expect(0x45, "uart tx bits set")
      c.io.tx.valid.expect(1, "uart tx valid set")

      c.clock.step()
      c.io.tx.bits.expect(0x45, "uart tx bits still set")
      c.io.tx.valid.expect(1, "uart tx valid still set")

      c.io.tx.ready.poke(true)

      c.clock.step()
      c.io.tx.ready.poke(false)
      c.io.tx.bits.expect(0, "uart tx bits reset")
      c.io.tx.valid.expect(0, "uart tx valid reset")
    }
  }
}
