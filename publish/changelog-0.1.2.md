# 0.1.2-CHANGELOG

# FIX

+ Kicked creatures no longer phase through unbreakable blocks (bedrock, barriers, and other `#minecraft:wither_immune` / negative-hardness blocks) when Disintegration or Unstable Collision is active. Hitting those blocks now uses normal wall-collision instead of forced no-physics traversal.
+ If the first impact is an unbreakable block, kick-driven block destruction is skipped entirely. Stone behind a bedrock wall stays intact.
