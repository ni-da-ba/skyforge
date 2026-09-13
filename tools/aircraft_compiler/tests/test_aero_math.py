from __future__ import annotations

import math
import sys
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path: sys.path.insert(0,str(ROOT))

from aero_math import *


class AeroMathTests(unittest.TestCase):
    def test_dynamic_pressure_and_lift_identity(self):
        self.assertAlmostEqual(dynamic_pressure(1.225,45.0),1240.3125,places=8)
        self.assertAlmostEqual(lift_force(1.225,45.0,16.0,0.55),10914.75,places=6)

    def test_trapezoid_geometry(self):
        area=trapezoid_area(10.0,2.0,1.0)
        self.assertAlmostEqual(area,15.0)
        self.assertAlmostEqual(aspect_ratio(10.0,area),100.0/15.0)
        self.assertAlmostEqual(mean_aerodynamic_chord(2.0,1.0),14.0/9.0,places=12)

    def test_induced_drag_relation(self):
        expected=0.55**2/(math.pi*0.8*8.0)
        self.assertAlmostEqual(induced_drag_coefficient(0.55,8.0,0.8),expected,places=14)

    def test_mass_center(self):
        self.assertAlmostEqual(mass_center([(2.0,1.0),(1.0,4.0)]),2.0)

    def test_tail_volume_definitions(self):
        self.assertAlmostEqual(horizontal_tail_volume(4.0,3.0,16.0,1.5),0.5)
        self.assertAlmostEqual(vertical_tail_volume(2.0,4.0,16.0,10.0),0.05)


if __name__ == "__main__": unittest.main()
