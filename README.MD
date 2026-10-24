# Minecraft Pocket Edition 0.6.1 for J2ME (Nokia Edition)

Port e demake otimizado de **Minecraft: Pocket Edition 0.6.1** para **Java ME (J2ME)** com aceleração 3D via **M3G (JSR-184)**.

---

## ⚡ Otimizações Implementadas
- **Zero-GC Primitive Collections:** Estruturas de dados `IntMap` e `IntSet` com chave inteira primitiva (open-addressing) substituindo `Hashtable` e eliminando alocações de `new Integer()` no loop de renderização e consultas de Tile Entities.
- **Frame Pacing & CPU Starvation Fix:** Substituição do loop descontrolado com `Thread.yield()` por um regulador adaptativo com temporização precisa de quadros e limites configuráveis (30 FPS para economia de bateria, 60 FPS ou Uncapped). Evita superaquecimento e elimina estalos no áudio e latência de entrada.
- **Explosion Optimization:** Cálculo de propagação de explosões livre de alocações no heap.

---

## 🎮 Controles Universais (Touch / Botões / Ambos)
Configurável em **Options > Custom > Control mode**:
1. **Auto (Hybrid):** Entrada simultânea de teclas físicas e toque na tela (ideal para Nokia Asha 300/303, Nokia E6 e emuladores).
2. **Keypad Only:** Oculta controles virtuais da tela, fixa a mira no centro e prioriza atalhos numéricos (`2/4/6/8` para mover, `5` minerar/atacar, `1` usar/colocar, `0` pular, `*` agachar, `#` criar, softkeys para menu).
3. **Touchscreen Only:** Controles virtuais (D-Pad analógico, pulo, pausa e gestos de toque) ativados desde o boot.

---

## 📱 Builds e Telas Disponíveis (`dist/`)
- `MinecraftJ2ME_Universal_Adaptive.jar`: Build universal responsiva para qualquer resolução.
- `MinecraftJ2ME_QVGA_240x320_Keypad.jar`: Otimizada para celulares verticais 240x320 (Nokia S40 / S60v3).
- `MinecraftJ2ME_Landscape_320x240_QWERTY.jar`: Otimizada para celulares horizontais 320x240 (Nokia E71, E72, C3).
- `MinecraftJ2ME_Touch_360x640_nHD.jar`: Otimizada para celulares touch 360x640 (Symbian^3, S60v5, Nokia 5800, N8).
- `MinecraftJ2ME_Compact_176x220.jar`: Otimizada para celulares de tela pequena (176x208 / 176x220).

---

## 🛠️ Como Compilar
Para recompilar o projeto utilizando JDK 8+ e as stubs incluídas em `libs/`:

```powershell
$libs = (Get-ChildItem libs/*.jar | ForEach-Object { $_.FullName }) -join ";"
javac -cp "$libs" -source 1.8 -target 1.8 -d build/classes (Get-ChildItem src/java/*.java | ForEach-Object { $_.FullName })
```
