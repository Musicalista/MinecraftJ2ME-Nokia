# Logic benchmark (headless)

Generates a world from a fixed seed, runs N ticks with a player and prints timings and a checksum
of the block state. No rendering, no M3G. Runs are reproducible: same seed and ticks, same checksum.

    javac -cp <classes>:<libs> -d out build/bench/Bench.java
    java -cp <classes>:out Bench 12345 20000
    java -Dbench.cp=1000 -Dbench.cpFrom=10000 -cp <classes>:out Bench 12345 20000   # checkpoints

Determinism: the benchmark pins every source of randomness the game uses for a single run:
- Level.random, Mth.RND, Mob.rnd and the player's rnd, reseeded from the tick index;
- Level.randValue (random-tick generator), which the constructor seeds from an unseeded Random.
  This last one caused the late divergence seen around tick 10000: the value changes between
  launches, so random ticks hit different blocks. The game itself keeps that behaviour.

Checked: 5 runs of 50000 ticks, baseline and optimized builds, all give the same checksum.
