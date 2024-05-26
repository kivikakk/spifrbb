#include <iostream>
#include <spifrbb.h>

//
#include <rom.cc>

/**
 * Yawonk.
 */

namespace cxxrtl_design {

struct bb_p_SPIFRWhitebox_impl : public bb_p_SPIFRWhitebox {
  enum {
    STATE_IDLE,
    STATE_SELECTED_POWER_DOWN,
    STATE_SELECTED_POWERING_UP_NEEDS_DESELECT,
    STATE_DESELECTED_POWERED_UP,
    STATE_SELECTED_POWERED_UP,
    STATE_READING
  } state;

  uint32_t sr;
  uint8_t edges;
  uint32_t addr;
  uint8_t bit;

  void reset() override {
    this->state = STATE_IDLE;
    this->sr = 0u;
    this->edges = 0u;
    this->addr = 0u;
    this->bit = 0u;

    p_cipo = wire<1>{0u};
  }

  bool eval(performer *performer) override {
    bool converged = true;
    bool posedge_p_clock = this->posedge_p_clock();

    if (posedge_p_clock) {
      p_cipo.next = value<1>{0u};

      uint32_t srnext =
          ((this->sr & 0x7fffffffu) << 1) | p_copi.get<uint32_t>();

      switch (this->state) {
      case STATE_IDLE: {
        if (p_cs) {
          this->state = STATE_SELECTED_POWER_DOWN;
        }
        break;
      }
      case STATE_SELECTED_POWER_DOWN: {
        if (this->edges == 7 && (srnext & 0xffu) == 0xabu) {
          this->state = STATE_SELECTED_POWERING_UP_NEEDS_DESELECT;
        }
        break;
      }
      case STATE_SELECTED_POWERING_UP_NEEDS_DESELECT: {
        if (!p_cs) {
          this->state = STATE_DESELECTED_POWERED_UP;
        }
        break;
      }
      case STATE_DESELECTED_POWERED_UP: {
        if (p_cs) {
          this->state = STATE_SELECTED_POWERED_UP;
        }
        break;
      }
      case STATE_SELECTED_POWERED_UP: {
        if (this->edges == 31u && (srnext >> 24) == 0x03u) {
          this->addr = srnext & 0x00ffffffu;
          this->state = STATE_READING;
          // fallthrough
        } else {
          break;
        }
      }
      case STATE_READING: {
        if (this->addr >= spi_flash_base &&
            this->addr < spi_flash_base + spi_flash_length) {
          uint8_t bit = (spi_flash_content[this->addr - spi_flash_base] >>
                         (7 - this->bit)) &
                        0x1;
          if (++this->bit == 8) {
            this->bit = 0;
            ++this->addr;
          }
          p_cipo.next = value<1>{bit};
        }
        if (!p_cs) {
          this->state = STATE_IDLE;
        }
        break;
      }
      }

      if (p_cs) {
        this->sr = srnext;
        ++this->edges;
      } else {
        this->edges = 0;
      }
    }

    return converged;
  }
};

std::unique_ptr<bb_p_SPIFRWhitebox>
bb_p_SPIFRWhitebox::create(std::string name, metadata_map parameters,
                           metadata_map attributes) {
  return std::make_unique<bb_p_SPIFRWhitebox_impl>();
}

} // namespace cxxrtl_design
