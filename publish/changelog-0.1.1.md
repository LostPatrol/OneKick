# 0.1.1-CHANGELOG

# CHANGE

+ Reaction and Aerodynamics recoil is stronger than 0.1.0.
  + Kicking a block with Reaction still uses a 0.44 coefficient (2× the original 0.22).
  + Kicking a creature with Reaction now uses the same 0.44 coefficient (was 0.14).
  + Kicking the air with Aerodynamics now uses a 0.50 coefficient. Recoil strength no longer scales with Aerodynamics level; the level still only limits how many air kicks you get after leaving the ground.

# FIX

+ Reaction from kicking a creature now pushes you opposite your look direction, including vertically. It no longer flattens to a horizontal backward shove, and looking straight up or down at a creature still produces recoil.
+ High-charge Reaction no longer gets capped to the same height as a Charge V kick. Player recoil is synced with an unclamped custom packet instead of vanilla entity-motion packets (which clamp each axis to ±3.9).
