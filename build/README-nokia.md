# MinecraftJ2ME for Nokia phones

Build branch for Nokia S40/S60 phones (MIDP 2.0, CLDC 1.1, JSR-184 M3G, JSR-75 files).
The jar holds only the game classes and resources; the phone supplies the APIs.

    MIDP_API=<dir with MIDP 2.0 compile-only classes> sh build/build-nokia.sh

Compile-only inputs: `libs/*.jar` (M3G, JSR-75, JSR-82, Nokia touch API stubs) and
`build/compile-stubs` (StreamConnection, StreamConnectionNotifier, DatagramConnection, Datagram).
`build/cpcheck.py` validates every class before the jar is written.

Not tested on a device yet.
