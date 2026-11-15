#!/system/bin/sh
# Keeps relays on 127.0.0.1:2222 and :2223 -> the Terminal app's VM sshd (Antigravity dials one of these; the Terminal app does not forward them).
# Each relay finds the VM's current address itself, per connection.
( while true; do /system/bin/gb-portrelay 2223 auto 2223; sleep 5; done ) &
while true; do /system/bin/gb-portrelay 2222 auto 2222; sleep 5; done
