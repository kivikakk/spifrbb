package ee.kivikakk.spifrbb.uart

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import ee.hrzn.chryse.platform.Platform
import org.scalatest.flatspec.AnyFlatSpec

class UARTSpec extends AnyFlatSpec {
  behavior.of("UART")

  // These tests are *really* ugly, but they work for now. Need more clarity.

  implicit val platform: Platform = new Platform {
    val id      = "uartspec"
    val clockHz = 3
  }

  it should "receive a byte" in {
    simulate(new UART(baud = 1)) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      // Assert START and hold for one bit.
      c.pins.rx.poke(false.B)

      c.rxIo.valid.expect(false.B)

      c.clock.step(3)

      // Generate a byte and play it out. Ensure we remain not `rdy`.
      val input = (new scala.util.Random).nextInt(256)
      for {
        bitIx <- 7 to 0 by -1
        i     <- 0 until 3
      } {
        c.pins.rx.poke(((input & (1 << bitIx)) != 0).B)
        c.rxIo.valid.expect(false.B)
        c.clock.step()
      }

      // Assert STOP and hold for one bit; wait for sync and processing delay (?).
      c.pins.rx.poke(true.B)

      for { i <- 0 until 7 } {
        c.rxIo.valid.expect(false.B)
        c.clock.step()
      }

      // Check received OK.
      c.rxIo.ready.poke(true.B)

      c.rxIo.valid.expect(true.B)
      c.rxIo.bits.byte.expect(input)

      // Ensure we can move to the next byte.
      c.clock.step()
      c.rxIo.ready.poke(false.B)
      c.rxIo.valid.expect(false.B)
    }
  }

  it should "transmit a byte" in {
    simulate(new UART(baud = 1)) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      // Generate a byte and request it to be sent.
      val input = (new scala.util.Random).nextInt(256)
      c.txIo.bits.poke(input.U)
      c.txIo.valid.poke(true.B)

      c.pins.tx.expect(true.B)

      c.clock.step()
      c.txIo.valid.poke(false.B)

      c.pins.tx.expect(true.B)

      // Watch START.
      for { i <- 0 until 3 } {
        c.clock.step()
        c.pins.tx.expect(false.B)
      }

      // Check for each bit in turn.
      for {
        bitIx <- 7 to 0 by -1
        i     <- 0 until 3
      } {
        c.clock.step()
        c.pins.tx.expect(((input & (1 << bitIx)) != 0).B)
      }

      // Watch STOP.
      for { i <- 0 until 3 } {
        c.clock.step()
        c.pins.tx.expect(true.B)
      }
    }
  }
}
