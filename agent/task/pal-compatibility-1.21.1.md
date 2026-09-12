# PAL compatibility task (Minecraft 1.21.1)

Implement the smallest optional integration with Player Animation Library so The Awakening's
first-person ability rendering does not expose the player's head. Preserve the existing renderer
and first-person kick behavior exactly when PAL is absent. When PAL is present, allow One Kick's
right-leg pose in third person and accept that the kick leg is temporarily hidden while another
PAL animation owns the first-person model pass.
