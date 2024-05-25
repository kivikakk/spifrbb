package ee.kivikakk.spifrbb

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

  it should "x" in {
    simulate(new Stackyem) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)
    }
  }
}
