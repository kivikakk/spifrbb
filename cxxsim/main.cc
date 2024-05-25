#include <cassert>
#include <fstream>
#include <iostream>

#include <cxxrtl/cxxrtl_vcd.h>
#include <newproject.h>

int main(int argc, char **argv) {
  cxxrtl_design::p_newproject top;
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

  // ledr should be low or high according to 'expected', where each element
  // represents 1/4th of a second. ledg should always be high.
  //
  // This mirrors the TopSpec test in Scala.
  std::vector<int> expected = {0, 1, 1, 0, 0, 1, 1, 0};
  for (auto ledr : expected) {
    for (int i = 0; i < (CLOCK_HZ / 4); ++i) {
      assert(top.p_io__ledr.get<int>() == ledr);
      assert(top.p_io__ledg);

      top.p_clock.set(true);
      top.step();
      vcd.sample(vcd_time++);
      top.p_clock.set(false);
      top.step();
      vcd.sample(vcd_time++);
    }
  }

  std::cout << "finished on cycle " << (vcd_time >> 1) << std::endl;

  if (do_vcd) {
    std::ofstream of(argv[2]);
    of << vcd.buffer;
  }

  return 0;
}
