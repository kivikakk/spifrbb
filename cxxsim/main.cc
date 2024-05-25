#include <cassert>
#include <fstream>
#include <iostream>
#include <optional>
#include <random>

#include <cxxrtl/cxxrtl_vcd.h>
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
  std::optional<std::string> vcd_out = std::nullopt;
  bool debug = false;

  for (int i = 1; i < argc; ++i) {
    if (strcmp(argv[i], "--vcd") == 0 && argc >= (i + 2)) {
      vcd_out = std::string(argv[++i]);
    } else if (strcmp(argv[i], "--debug") == 0) {
      debug = true;
    } else {
      std::cerr << "unknown argument \"" << argv[i] << "\"" << std::endl;
      return 2;
    }
  }

  if (vcd_out.has_value()) {
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

  int rc = 0;
  bool done = false;
  for (int i = 0; i < 10000 && !done; ++i) {
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

    step();

    top.p_io__uart__tx__valid.set(false);

    if (debug)
      std::cout << "a(" << (int)a << "), b(" << (int)b << "), c(" << (int)c
                << ")" << std::endl;

    step();

    top.p_io__uart__rx__ready.set(true);

    // 0: seen nothing. 1: seen 'a'. 2: seen 'c'. 3: seen 2nd 'c'.
    int progress = 0;

    for (int j = 0; j < 1000; ++j) {
      if (top.p_io__uart__rx__valid) {
        uint8_t val = top.p_io__uart__rx__bits.get<uint8_t>();
        if (debug)
          std::cout << "got valid on j(" << j << "): val(" << (int)val << ")"
                    << std::endl;
        if (progress == 0 && val == a) {
          progress = 1;
        } else if (progress == 1 && val == c) {
          progress = 2;
        } else if (progress == 2 && val == c) {
          step(); // Step to ack before we reset ready.
          top.p_io__uart__rx__ready.set(false);
          progress = 3;
          break;
        } else {
          std::cerr << "unexpected uart on cycle " << (vcd_time >> 1) << ": a("
                    << (int)a << "), "
                    << "b(" << (int)b << "), c(" << (int)c << "), progress("
                    << progress << "), val(" << (int)val << ")" << std::endl;
          rc = 1;
          done = true;
          break;
        }
      }
      step();
    }

    if (progress != 3) {
      rc = 1;
      std::cerr << "didn't progress on cycle " << (vcd_time >> 1) << ": a("
                << (int)a << "), "
                << "b(" << (int)b << "), c(" << (int)c << "), progress("
                << progress << ")" << std::endl;
      done = true;
    }
  }

  std::cout << "finished on cycle " << (vcd_time >> 1) << std::endl;

  if (vcd_out.has_value()) {
    std::ofstream of(*vcd_out);
    of << vcd.buffer;
  }

  return rc;
}
