# Logic benchmark (headless)

Generates a world from a fixed seed, runs N ticks with a player and prints timings and a checksum
of the block state. No rendering, no M3G.

    javac -cp <classes>:<libs> -d out build/bench/Bench.java
    java -cp <classes>:out Bench 12345 20000
    java -Dbench.cp=1000 -Dbench.cpFrom=10000 -cp <classes>:out Bench 12345 20000   # checkpoints

Determinism status:
- World generation is deterministic (same checksum every run).
- Ticks are deterministic up to ~10000 ticks in all runs tried.
- Beyond that, about 1 run in 5 diverges (first divergence between ticks 10330 and 10340 with 20000
  ticks). Ruled out: unseeded Random (Level.random, Mob.rnd, Player rnd, Mth.RND are all reseeded),
  clock reads and memory-dependent code in the logic, threads, JIT (-Xint diverges too),
  Math.cos in Level.updateSkyDarken (StrictMath copy still diverges). Not yet identified.
- Use checkpoints up to 10000 ticks to validate changes until the source is found.
