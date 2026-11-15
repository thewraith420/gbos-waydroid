# Input Device Configuration for the input-bridge virtual keyboard (vendor 0x1209, product 0xab00).
# Without this, Android classifies the uinput keyboard as an INTERNAL/built-in keyboard and then
# DISABLES it whenever the (host) lid switch reports closed — EventHub kept dropping it (device
# Generation climbed as it flapped enabled/disabled), so keystrokes emitted to the evdev node but never
# dispatched. Declaring it external makes Android ignore the lid state and keep the keyboard enabled.
device.internal = 0
keyboard.builtIn = 0
