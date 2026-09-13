# 0.1.3-CHANGELOG

# FIX

+ Fixed compatibility with Player Animation Library and The Awakening. The player's head and body no longer appear inside the first-person camera during ability animations.
+ When Player Animation Library is installed, One Kick now preserves its player-model animation hooks and applies the third-person kick through an isolated animation layer.
+ While another Player Animation Library animation controls first person, One Kick's first-person kick leg is temporarily hidden and returns after that animation ends.
+ Player Animation Library remains optional. Behavior without it is unchanged.
