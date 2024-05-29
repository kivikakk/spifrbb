package ee.kivikakk.spifrbb

import ee.hrzn.chryse.ChryseApp
import ee.hrzn.chryse.platform.Platform
import ee.hrzn.chryse.platform.cxxrtl.CXXRTLOptions
import ee.hrzn.chryse.platform.cxxrtl.CXXRTLPlatform
import ee.hrzn.chryse.platform.ecp5.LFE5U_45F
import ee.hrzn.chryse.platform.ecp5.ULX3SPlatform
import ee.hrzn.chryse.platform.ice40.IceBreakerPlatform
import ee.hrzn.chryse.tasks.BaseTask
import org.rogach.scallop._

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths

trait PlatformSpecific {
  var romFlashBase: BigInt

  def programROM(binPath: String): Unit
}

object App extends ChryseApp {
  override val name                                  = "spifrbb"
  override def genTop()(implicit platform: Platform) = new Top
  override val targetPlatforms = Seq(
    new IceBreakerPlatform(ubtnReset = true) with PlatformSpecific {
      var romFlashBase = BigInt("00800000", 16)

      def programROM(binPath: String) =
        programROMImpl(this.romFlashBase, binPath)

      object programROMImpl extends BaseTask {
        def apply(romFlashBase: BigInt, binPath: String): Unit = {
          runCmd(
            CmdStepProgram,
            Seq("iceprog", "-o", f"0x$romFlashBase%x", binPath),
          )
        }
      }
    },
    new ULX3SPlatform(LFE5U_45F) with PlatformSpecific {
      var romFlashBase = BigInt("00100000", 16)

      def programROM(binPath: String) =
        programROMImpl(this.romFlashBase, binPath)

      object programROMImpl extends BaseTask {
        def apply(romFlashBase: BigInt, binPath: String): Unit = {
          runCmd(
            CmdStepProgram,
            Seq(
              "openFPGALoader",
              "-v",
              "-b",
              "ulx3s",
              "-f",
              "-o",
              f"0x$romFlashBase%x",
              binPath,
            ),
          )
        }
      }
    },
  )
  override val cxxrtlOptions = Some(
    CXXRTLOptions(
      platforms = Seq(new CXXRTLWhiteboxPlatform, new CXXRTLBlackboxPlatform),
      blackboxes = Seq(classOf[SPIFRWhitebox], classOf[SPIFRBlackbox]),
      buildHooks = Seq(rom.generateCxxrtl),
    ),
  )
  override val additionalSubcommands = Seq(rom)

  object rom extends this.ChryseSubcommand("rom") with BaseTask {
    banner("Build the Stackyem ROM image, and optionally program it.")
    val program =
      if (targetPlatforms.length > 1)
        choice(
          targetPlatforms.map(_.id),
          name = "program",
          argName = "board",
          descr = s"Program the ROM onto the board.", // + " Choices: ..."
        )
      else
        opt[Boolean](descr = s"Program the ROM onto ${targetPlatforms(0).id}")

    def generate(): String = {
      Files.createDirectories(Paths.get(buildDir))

      val content = Stackyem.DEFAULT_IMEM_INIT.map(_.litValue.toByte)
      val binPath = s"$buildDir/rom.bin"
      val fos     = new FileOutputStream(binPath)
      fos.write(content.toArray, 0, content.length)
      fos.close()
      println(s"wrote $binPath")

      binPath
    }

    def generateCxxrtl(platform: CXXRTLPlatform): Unit = {
      val romFlashBase = platform.asInstanceOf[PlatformSpecific].romFlashBase
      val content      = Stackyem.DEFAULT_IMEM_INIT.map(_.litValue.toByte)
      writePath(s"$buildDir/rom.cc") { wr =>
        wr.print("const uint8_t spi_flash_content[] = \"");
        wr.print(content.map(b => f"\\$b%03o").mkString)
        wr.println("\";");
        wr.println(f"const uint32_t spi_flash_base = 0x$romFlashBase%x;");
        wr.println(f"const uint32_t spi_flash_length = 0x${content.length}%x;");
      }
    }

    def execute() = {
      val binPath = generate()

      if (rom.program.isDefined) {
        val platform =
          if (targetPlatforms.length > 1)
            targetPlatforms
              .find(_.id == rom.program().asInstanceOf[String])
              .get
          else
            targetPlatforms(0)

        platform.asInstanceOf[PlatformSpecific].programROM(binPath)
      }
    }
  }
}
