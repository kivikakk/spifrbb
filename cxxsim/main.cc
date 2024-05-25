#include <cassert>
#include <fstream>
#include <iostream>

#include <cxxrtl/cxxrtl_vcd.h>
#include <random>
#include <spifrbb.h>

int main(int argc, char **argv) {
  cxxrtl_design::p_spifrbb top;
  debug_items di;
  top.debug_info(&di, nullptr, "top ");

  bool do_vcd = argc >= 3 && std::string(argv[1]) == "--vcd";
  cxxrtl::vcd_writer vcd;
  uint64_t vcd_time = 0;
  if (do_vcd)
    vcd.add(di);

  top.p_reset.set(true);
  top.p_clock.set(true);
  top.step();
  vcd.sample(vcd_time++);
  top.p_clock.set(false);
  top.step();
  vcd.sample(vcd_time++);
  top.p_reset.set(false);

  std::random_device rd;
  std::mt19937 mt(rd());
  std::uniform_int_distribution<uint8_t> dist;

  // Stackyem's default program echoes byte 3n+0, drops byte 3n+1, and echos
  // byte 3n+2 twice. Generate a bunch of random data and make sure it does
  // that.

  for (int i = 0; i < 10; ++i) {
    uint8_t b = dist(mt);
  }

  std::cout << "finished on cycle " << (vcd_time >> 1) << std::endl;

  if (do_vcd) {
    std::ofstream of(argv[2]);
    of << vcd.buffer;
  }

  return 0;
}
