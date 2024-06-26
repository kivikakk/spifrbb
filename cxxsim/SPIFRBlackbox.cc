#include <iostream>
#include <spifrbb.h>

#include <rom.cc>

/**
 * Yawonk.
 */

namespace cxxrtl_design {

struct bb_p_SPIFRBlackbox_impl : public bb_p_SPIFRBlackbox {
  const uint8_t COUNTDOWN_BETWEEN_BYTES = 2u;

  enum {
    STATE_IDLE,
    STATE_READ,
  } state;

  uint32_t address;
  uint16_t remaining;
  uint8_t countdown;

  void reset() override {
    this->state = STATE_IDLE;
    this->address = 0u;
    this->remaining = 0u;
    this->countdown = 0u;

    p_io__req__ready = wire<1>{true};
    p_io__res__bits = wire<8>{0u};
    p_io__res__valid = wire<1>{false};
  }

  bool eval(performer *performer) override {
    bool converged = true;
    bool posedge_p_clock = this->posedge_p_clock();

    if (posedge_p_clock) {
      p_io__res__valid.set(false);

      switch (this->state) {
      case STATE_IDLE: {
        if (p_io__req__valid) {
          this->address = p_io__req__bits__addr.get<uint32_t>();
          this->remaining = p_io__req__bits__len.get<uint16_t>();

          if (this->address >= spi_flash_base &&
              this->address < spi_flash_base + spi_flash_length) {
            p_io__req__ready.set(false);
            this->state = STATE_READ;
            this->countdown = COUNTDOWN_BETWEEN_BYTES;
          }
        }
        break;
      }
      case STATE_READ: {
        if (--this->countdown == 0u) {
          if (this->remaining == 0u) {
            p_io__req__ready.set(true);
            this->state = STATE_IDLE;
          } else {
            this->countdown = COUNTDOWN_BETWEEN_BYTES;
            if (this->address - spi_flash_base < spi_flash_length)
              p_io__res__bits.set(
                  spi_flash_content[this->address - spi_flash_base]);
            else
              p_io__res__bits.set(0xffu);
            p_io__res__valid.set(true);

            ++this->address;
            --this->remaining;
          }
        }
        break;
      }
      }
    }

    return converged;
  }
};

std::unique_ptr<bb_p_SPIFRBlackbox>
bb_p_SPIFRBlackbox::create(std::string name, metadata_map parameters,
                           metadata_map attributes) {
  return std::make_unique<bb_p_SPIFRBlackbox_impl>();
}

} // namespace cxxrtl_design
