#include <cassert>
#include <fstream>
#include <iostream>

#include <cxxrtl/cxxrtl_vcd.h>
#include <random>
#include <spifrbb.h>

static cxxrtl_design::p_spifrbb top;
static cxxrtl::vcd_writer vcd;
static uint64_t vcd_time = 0;

void step() {
  top.p_clock.set(true);
  top.step();
  vcd.sample(vcd_time++);
  top.p_clock.set(false);
  top.step();
  vcd.sample(vcd_time++);
}

int main(int argc, char **argv) {
  bool do_vcd = argc >= 3 && std::string(argv[1]) == "--vcd";
  if (do_vcd) {
    debug_items di;
    top.debug_info(&di, nullptr, "top ");
    vcd.add(di);
  }

  top.p_reset.set(true);
  step();
  top.p_reset.set(false);

  std::random_device rd;
  std::mt19937 mt(rd());
  std::uniform_int_distribution<uint8_t> dist;

  // Stackyem's default program echoes byte 3n+0, drops byte 3n+1, and echos
  // byte 3n+2 twice. Generate a bunch of random data and make sure it does
  // that.

  bool done = false;
  for (int i = 0; i < 10 && !done; ++i) {
    uint8_t a = dist(mt);
    top.p_io__uart__tx__bits.set(a);
    top.p_io__uart__tx__valid.set(true);

    step();

    uint8_t b = dist(mt);
    top.p_io__uart__tx__bits.set(b);
    top.p_io__uart__tx__valid.set(true);

    step();

    uint8_t c = dist(mt);
    top.p_io__uart__tx__bits.set(c);
    top.p_io__uart__tx__valid.set(true);

    int progress =
        0; // 0: seen nothing. 1: seen 'a'. 2: seen 'c'. 3: seen 2nd 'c'.
    top.p_io__uart__rx__ready.set(true);

    for (int j = 0; j < 1000; ++j) {
      if (top.p_io__uart__rx__valid) {
        uint8_t val = top.p_io__uart__rx__bits.get<uint8_t>();
        if (progress == 0 && val == a) {
          progress = 1;
        } else if (progress == 1 && val == c) {
          progress = 2;
        } else if (progress == 2 && val == c) {
          top.p_io__uart__rx__ready.set(false);
          progress = 3;
          break;
        } else {
          std::cerr << "unexpected uart on cycle " << (vcd_time >> 1) << ": a("
                    << (int)a << "), "
                    << "b(" << (int)b << "), c(" << (int)c << "), progress("
                    << progress << "), uart(" << (int)val << ")" << std::endl;
          done = true;
          break;
        }
      }
      step();
    }

    if (progress != 3) {
      std::cerr << "didn't progress on cycle " << (vcd_time >> 1) << ": a("
                << (int)a << "), "
                << "b(" << (int)b << "), c(" << (int)c << "), progress("
                << progress << ")" << std::endl;
      done = true;
    }
  }

  std::cout << "finished on cycle " << (vcd_time >> 1) << std::endl;

  if (do_vcd) {
    std::ofstream of(argv[2]);
    of << vcd.buffer;
  }

  return 0;
}
