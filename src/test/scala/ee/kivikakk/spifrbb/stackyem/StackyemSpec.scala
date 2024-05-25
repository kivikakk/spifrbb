package ee.kivikakk.spifrbb.stackyem

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import ee.hrzn.chryse.platform.Platform
import org.scalatest.flatspec.AnyFlatSpec

class StackyemSpec extends AnyFlatSpec {
  behavior.of("Stackyem")

  implicit val plat: Platform = new Platform {
    val id      = "stackyemspec"
    val clockHz = 8
  }

  it should "read a byte onto the stack and dup it" in {
    simulate(
      new Stackyem(
        imemInit = Seq(Instruction.ReadUart, Instruction.Dup),
        stackSize = 2,
      ),
    ) { c =>
      c.reset.poke(true)
      c.clock.step()
      c.reset.poke(false)

      c.debugIo.stack(0).expect(0)
      c.debugIo.stack(1).expect(0)

      c.debugIo.pc.expect(0)

      c.clock.step()
      c.debugIo.pc.expect(1, "pc adv")

      c.clock.step()
      // Still waiting for UART.
      c.debugIo.pc.expect(1, "pc wait")
      c.debugIo.sp.expect(0, "sp wait")

      c.io.uartRx.bits.byte.poke(0xac)
      c.io.uartRx.bits.err.poke(false)
      c.io.uartRx.valid.poke(true)

      c.clock.step()
      c.debugIo.pc.expect(1, "pc wait")
      c.debugIo.sp.expect(0, "sp wait")

      c.clock.step()
      c.debugIo.pc.expect(1, "pc wait")
      c.debugIo.sp.expect(1, "sp adv")
      c.debugIo.stack(0).expect(0xac)

      c.clock.step()
      c.debugIo.pc.expect(0, "pc wrap")
      c.debugIo.sp.expect(0, "sp wrap")
      c.debugIo.stack(1).expect(0xac)
    }
  }

  it should "write to uart" in {
    simulate(
      new Stackyem(
        imemInit = Seq(Instruction.Imm, 0x45.U, Instruction.WriteUart),
        stackSize = 2,
      ),
    ) { c =>
      c.reset.poke(true)
      c.clock.step()
      c.reset.poke(false)

      c.debugIo.pc.expect(0)
      c.debugIo.sp.expect(0)
      c.debugIo.stack(0).expect(0)

      c.clock.step()
      c.debugIo.pc.expect(2, "pc adv")
      c.debugIo.sp.expect(1, "sp adv")
      c.debugIo.stack(0).expect(0x45)
      // Stackyem doesn't do any buffering of its UART itself, so this gets set
      // immediately (combinationally).
      c.io.uartTx.bits.expect(0x45)
      c.io.uartTx.valid.expect(1)

      c.clock.step()
      c.io.uartTx.bits.expect(0)
      c.io.uartTx.valid.expect(0)
    }
  }
}
