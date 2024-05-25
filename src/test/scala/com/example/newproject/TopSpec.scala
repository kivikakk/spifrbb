package com.example.newproject

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import ee.hrzn.chryse.platform.Platform
import org.scalatest.flatspec.AnyFlatSpec

class TopSpec extends AnyFlatSpec {
  behavior.of("Blinker")

  implicit val plat: Platform = new Platform {
    val id      = "topspec"
    val clockHz = 8
  }

  it should "blink the light" in {
    simulate(new Blinker) { c =>
      // ledr should be low or high according to 'expected', where each
      // element represents 1/4th of a second. ledg should always be high.
      //
      // This mirrors the cxxsim test.
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      for {
        ledr <- Seq(0, 1, 1, 0, 0, 1, 1, 0)
        _    <- 0 until (plat.clockHz / 4)
      } {
        c.io.ledr.expect((ledr == 1).B)
        c.io.ledg.expect(true.B)
        c.clock.step()
      }
    }
  }
}
