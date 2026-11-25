# Create: Wire Rider

Ride **Create: Electro Energetics** power lines like a zipline using a Create wrench (or any item tagged as a wrench).

## What it does

- **Grab & ride:** Right-click a wire while holding a wrench to hook on, or hold right-click while falling to catch a wire mid-air.
- **Physics & controls:** Speed follows wire slope. Use **W / S** to push or brake, **A / D** to hop onto adjacent parallel wires, **Space** to leap off with momentum, and **Shift** to drop down.
- **Pole transitions:** Automatically carries you across poles onto the next connected cable.
- **Live-wire hazards:**
  - Insulated wires are safe unless their insulation is overvolted.
  - Bare energized wires spark on the wrench jaws and warn you with a small brass badge next to the crosshair (which moves to the wrench in third person).
  - Riding too low over solid blocks or water arcs to the ground and shocks you based on actual line voltage (arc distance increases in rain).
  - Standing still on high-voltage bare lines (`1500V+`) slowly builds up a static arc that shocks you if you stay idle for too long.
  - Overheated wires (`75C+`) smoke and burn on contact.
- **Configurable:** Common and client configs (`create_wire_rider-common.toml` / `create_wire_rider-client.toml`) let you adjust speed, hazard thresholds, sounds, camera tilt, highlight, and HUD.

## Controls

- **Right-click** (with wrench): Hook onto a wire / hold in mid-air to grab.
- **W / S**: Accelerate / brake along the wire.
- **A / D**: Switch to a parallel wire.
- **Space**: Leap off along travel/look direction.
- **Shift**: Drop straight down.

## Requirements

- Minecraft 1.21.1 (NeoForge 21.1.84+)
- Create 6.0.0+
- Create: Electro Energetics 1.1.0+

## License

MIT
