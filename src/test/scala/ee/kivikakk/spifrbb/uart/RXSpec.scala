package ee.kivikakk.spifrbb.uart

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class RXSpec extends AnyFlatSpec {
  behavior.of("RX")

  private def pokeBits(
      c: RX,
      bits: Seq[Int],
      forceStart: Boolean = false,
  ): Unit = {
    c.io.ready.poke(true.B)

    for {
      (bit, bitIx) <- bits.zipWithIndex
      i            <- 0 until 3
    } {
      if (forceStart && bitIx == 0 && i == 0) {
        c.pin.poke(false.B)
      } else {
        c.pin.poke((bit == 1).B)
      }
      c.io.valid.expect(false.B)
      c.clock.step()
    }

    // Wait to get into sFinish, accounting for the synchronisation delay and
    // extra time from finishing 'late'.
    for { i <- 0 until 3 } {
      c.io.valid.expect(false.B)
      c.clock.step()
    }
  }

  it should "receive a byte" in {
    simulate(new RX(divisor = 3)) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      pokeBits(c, Seq(0, 1, 0, 1, 0, 1, 1, 0, 0, 1))

      c.io.valid.expect(true.B)
      c.io.bits.byte.expect("b10101100".U)
      c.io.bits.err.expect(false.B)

      c.clock.step()
      c.io.valid.expect(false.B)
    }
  }

  it should "report a bad START" in {
    simulate(new RX(divisor = 3)) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      pokeBits(c, Seq(1, 1, 0, 1, 0, 1, 1, 0, 0, 1), forceStart = true)

      c.io.valid.expect(true.B)
      c.io.bits.byte.expect("b10101100".U)
      c.io.bits.err.expect(true.B)
    }
  }

  it should "make a difference if forceStart = false" in {
    simulate(new RX(divisor = 3)) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      pokeBits(c, Seq(1, 1, 0, 1, 0, 1, 1, 0, 0, 1))

      c.io.valid.expect(false.B)
    }
  }
}
