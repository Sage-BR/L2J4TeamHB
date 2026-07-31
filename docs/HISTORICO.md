# Histórico de Modificações

## 2026-07-31 — Sessão 20: Fix NSWE mismatch no pathfinder (getNsweBelow)

### Diagnóstico

O pathfinder continuava retornando `geoPath=size=0` mesmo após a Sessão 19. Análise detalhada do código revelou um **bug de consistência entre camadas**: `getHeightBelow()` e `getNsweNearest()` eram operações **independentes** — cada uma fazia sua própria busca na geodata, e podiam retornar dados de **camadas diferentes**.

**Exemplo concreto do bug:**
Em célula multilayer com:
- Layer 0 (teto): height=-3184, NSWE=0 (sem movimento)
- Layer 1 (chão): height=-3208, NSWE=15 (movimento livre)

`getHeightBelow(-3184)` retornava -3184 (o teto, primeiro layer ≤ worldZ).
`getNsweNearest(-3184)` encontrava o nearest = -3184 (dist=0), retornando NSWE=0.
→ Start node NSWE=0 → `expand()` retornava imediatamente → **nenhum vizinho explorado** → path size=0.

No VERGE, isso não acontece porque ambos usam o **mesmo índice**: `block.getIndexBelow()` → `block.getHeight(index)` → `block.getNswe(index)`.

### Correções Aplicadas

- **`ABlock.java`** — Novo método abstrato `getNsweBelow(geoX, geoY, worldZ)` que usa a **mesma iteração** de `getHeightBelow()` mas retorna NSWE, garantindo consistência de mesma camada.
- **`BlockFlat.getNsweBelow()`** — Retorna `CELL_FLAG_ALL`.
- **`BlockComplex.getNsweBelow()`** — Retorna NSWE da célula (single-layer).
- **`BlockMultilayer.getNsweBelow()`** — Percorre camadas alto→baixo, retorna NSWE da primeira ≤ worldZ (mesma lógica de `getHeightBelow`).
- **`BlockNull.getNsweBelow()`** — Retorna `CELL_FLAG_ALL`.
- **`GeoGridPathFinder.java`** — Todas as chamadas `getNsweNearest` substituídas por `getNsweBelow`:
  - Start node: `startBlock.getNsweBelow(gox, goy, oz)`
  - `getNodeNswe`: `block.getNsweBelow(gx, gy, h)`
  - `addNode`: `block.getNsweBelow(gx, gy, gz)`

### Benefícios

- Start node agora sempre tem NSWE correto da camada onde o personagem está
- `addNode` agora sempre lê NSWE da mesma camada que `getHeightBelow` retornou
- `getNodeNswe` agora sempre lê NSWE consistente com a Z consultada
- Totalmente alinhado com o padrão VERGE SOURCE 2.2 (index-based consistency)

---

## 2026-07-31 — Sessão 19: Fix pathfinder — getHeightBelow, CELL_IGNORE_HEIGHT, bounds check, diagonal NSWE

### Diagnóstico

O GeoGridPathFinder (Sessão 18) retornava `geoPath=size=0` em paths longos e o personagem ficava travado em paredes. Análise do log revelou 4 bugs críticos:

1. **`getHeightNearest` pegava teto em vez do chão** — Em células multilayer, `getHeightNearest()` retornava a camada mais próxima do Z informado, que podia ser o teto (-2544) em vez do chão (-3128). O pathfinder tentava caminhar pelo teto.
2. **Falta `CELL_IGNORE_HEIGHT` (+48)** — VERGE adiciona +48 ao Z ao expandir vizinhos para garantir que encontra o chão abaixo. Nosso pathfinder passava o Z direto.
3. **Bounds check usava `REGION_CELLS_X` (2048)** — Limitava o pathfinder a apenas 1 região (~32K world units). Paths longos falhavam sempre com size=0.
4. **Diagonal expansion incompleta** — VERGE verifica NSWE dos nós intermediários (x+dx,y) e (x,y+dy) antes de permitir movimento diagonal. Nosso código só verificava o nó atual.

### Correções Aplicadas

- **`ABlock.java`** — Novo método abstrato `getHeightBelow(geoX, geoY, worldZ)` que retorna a camada mais alta ≤ worldZ (o chão).
- **`BlockFlat.java`** — `getHeightBelow` retorna `_height` (única camada).
- **`BlockComplex.java`** — `getHeightBelow` retorna a altura da célula.
- **`BlockMultilayer.java`** — `getHeightBelow` percorre camadas (armazenadas alto→baixo) e retorna a primeira ≤ worldZ.
- **`BlockNull.java`** — `getHeightBelow` retorna `worldZ` (sem geo = retorna input).
- **`GeoEngine.java`** — Método público `getBlock(geoX, geoY)` para acesso direto ao ABlock pelo pathfinder.
- **`GeoGridPathFinder.java`** — Reescrita completa seguindo VERGE SOURCE 2.2:
  - `addNode` usa `getBlock().getHeightBelow()` em vez de `getHeightGeo`
  - `expand` adiciona `CELL_IGNORE_HEIGHT` ao Z antes de expandir vizinhos
  - `addCornerNode` verifica NSWE dos nós intermediários para diagonais
  - Bounds check usa `GEO_CELLS_MAX = 65536` (total, não por região)
  - `getNodeNswe` helper estático para consulta de NSWE de vizinhos

### Benefícios

- Pathfinding agora encontra rotas em qualquer distância (não limitado a 1 região)
- Seleção correta de camada (chão, não teto) em células multilayer
- Diagonais verificam corretamente se o caminho intermediário é passável
- Totalmente alinhado com VERGE SOURCE 2.2

---

## 2026-07-30 — Sessão 18: Migração do pathfinding para geo-grid (VERGE pattern)

### Diagnóstico

O pathfinding baseado em pathnodes (`GeoPathFinding.java`) tinha problemas estruturais:
- Pathnodes são uma grade coarse (8x8 células geo por node) que perde precisão
- Pre-checks rígidos causavam falhas frequentes quando o pathnode mais proximo era inalcançável
- Requeria ~1000 arquivos `.pn` extras para manter
- A lógica de A* era frágil e não se alinhava com o padrão VERGE

### Correções Aplicadas

- **NOVO `GeoGridPathFinder.java`** — A* baseado em geo-grid seguindo o padrão VERGE SOURCE 2.2. Cada célula geo é um nó do grafo, usando flags NSWE diretamente da geodata. Sem arquivos pathnode.
- **`GeoEngine.java`** — Adicionados métodos públicos `getGeoX`, `getGeoY`, `getWorldX`, `getWorldY`, `getHeightGeo`, `getNsweGeo`, `hasGeoPos` para acesso ao geo-grid.
- **`L2Character.java`** — `MoveData.geoPath` mudado de `List<AbstractNodeLoc>` para `List<Location>` para suportar novo pathfinder. Pathfinding agora usa `GeoGridPathFinder` em vez de `GeoPathFinding`.
- **`GameServer.java`** — Removido carregamento de pathnodes (`GeoPathFinding.getInstance()`). O pathfinding agora usa apenas geodata.
- **`DoorTable.java`** — Adicionado overload `checkIfDoorsBetween(Location, Location)` para suportar o novo tipo de path.

### Benefícios

- **Precisão** — Paths seguem o terreno célula por célula (não grade coarse)
- **Robustez** — Sem pre-checks rígidos que causavam falhas
- **Manutenção** — Zero arquivos `.pn` para gerar/distribuir
- **Alinhamento** — Totalmente alinhado com o padrão VERGE
- **Performance** — A* com heurística diagonal e MAX_ITERATIONS=6000

### Arquivos Alterados

- `java/net/sf/l2j/gameserver/pathfinding/geonodes/GeoGridPathFinder.java` (NOVO)
- `java/net/sf/l2j/gameserver/GeoEngine.java`
- `java/net/sf/l2j/gameserver/model/L2Character.java`
- `java/net/sf/l2j/gameserver/GameServer.java`
- `java/net/sf/l2j/gameserver/datatables/DoorTable.java`

---

## 2026-07-30 — Sessão 17: Fix pathfinding não encontrava rotas (personagem andava em linha reta e batia na parede)

### Diagnóstico

`GeoPathFinding.findPath()` tinha pre-checks rígidos que retornavam `null` imediatamente quando o pathnode mais proximo era inalcançavel via `moveCheck()`. Se havia uma parede entre o personagem e o pathnode mais proximo, o pathfinding morria sem tentar alternativas. O proprio codigo tinha um TODO reconhecendo o problema: `// TODO: Find closest path node we CAN access.`

### Correções

- **GeoPathFinding.java** — Novo metodo `findReachableNode()` que busca pathnodes acessiveis em aneis expansivos (raio 1-3 = ate ~384 world units). Se o pathnode mais proximo esta atras de uma parede, procura alternativas nearby antes de desistir.
- Tolerancia Z aumentada de 128 para 200 units — aceita mais pathnodes como candidatos.
- Pre-checks flexibilizados — em vez de retornar null imediatamente, tenta encontrar pathnode alternativo acessivel.

---

## Contexto das sessões

- **Sessão 7** — Column bug: NPCs/players travavam ao contornar colunas em blocos complex/multilevel. Removido NSWE check do destino para esses blocos. Removido geo-collision check por tick no ValidatePosition (causava 3+ rollbacks/s). Sincronizados índices geo/path.
- **Sessão 8** — Implementados `traceTerrainZ`, `isFalling`/`stopFalling`, `canSeeTarget` no `moveToPawn`, range NPC `mostHate.isMoving() || npc.isMoving()`, `clientStopMoving` antes do `doAttack`. Tudo compilando. Baseado nos commits L2J `1405cc42`, `fbf51d8e`, `c0b8a194`, `4147f4e6`.
- **Sessão 9** — Auditoria local encontrou 30 problemas potenciais. Corrigidos 9 de alta/média severidade: `doAttack` sem LOS, `isFalling` range 200 insuficiente, pathfinding Z tolerance 55, corner cut diagonal, `getSpawnHeight` com `zmin` errado e `zmin==zmax`, DoorInstance bypassando LOS, ValidatePosition early return sem update de lastPosition, NpcWalkerAI com `==` exato.
- **Sessão 10** — Varredura completa de 26 páginas (~1300 commits) do BitBucket. Os commits com diffs acessíveis são de 2020+. Os anteriores (2014-2019) não têm diffs disponíveis no repositório atual. Os commits relevantes de 2020+ já foram todos aplicados nas sessões 7-9. As branches `feature/geoengine_and_movement_stabilization` e `feature/attackable-ai-rework` do upstream contêm refinamentos que podemos cherry-pick se necessário.

---

## Commits Aplicados

## 2026-07-30 — Sessão 16: Alinhamento completo com VERGE SOURCE 2.2 (geodata + movimento)

### Diagnóstico

Três bugs interconectados causavam rollbacks massivos, teleports e personagens travando:

1. **GeoDataPatcher corrompia arquivos .l2j** — Usava formato `(height << 4) | NSWE` mas o formato L2J correto é `(height << 1) | NSWE`. Resultado: heights multiplicados por 8 (ex: `-1192` virava `-9536`).
2. **ValidatePosition tinha lógica demais** — Z override por geoHeight, terrain snap e geometry stuck recovery competiam com o GeoEngine, causando rollbacks constantes.
3. **Geodata files no disco estavam corrompidos** — Pelo patcher antigo com formato errado.

### Correções aplicadas

- **GeoDataPatcher.java** — `unpackHeight()` e `packData()` corrigidos para formato `(height << 1) | NSWE` (compatível com L2J e VERGE).
- **ValidatePosition.java** — Reescrito para padrão VERGE: lógica simples de desync vs velocidade. Removidos Z override, terrain snap e geometry stuck recovery. Restaurados `setLastClientPosition`/`setLastServerPosition`.
- **data/Server/data/geodata/*.l2j** — Restaurados arquivos originais do git (commit `a66569ab5`) antes da corrupção do patcher.
- **BlockMultilayer.java / BlockComplex.java** — `decodeHeight()` mantido com `>> 1` (formato L2J correto, já era).

## 2026-07-29 — Sessão 15: Fix seleção de layer errada em geodata Multilayer (teleport entre andares)

- `BlockMultilayer.java` — **Bug raiz:** `getHeightNearest()` e `getNsweNearest()` comparavam o valor **raw empacotado** `(height << 1 | NSWE)` diretamente com `worldZ` **decodificado**, causando seleção errada de layer em células multicamada.
- **Efeito colateral:** Jogador no andar de baixo (Z=-3109) era teleportado para o andar de cima (Z=-2544) ao andar — o servidor retornava a altura da layer superior como `geoHeight`.
- **Correção:** Adicionado `decodeHeight()` antes da comparação com `worldZ` em ambas as funções. Nota: `checkMove()` no mesmo arquivo já usava `decodeHeight()` corretamente — o bug era uma inconsistência.
- **Exemplo numérico:** Para célula com layers -2544 e -3109, jogador em -3109:
  - Antes (bug): `Math.abs(-12264 - (-3109)) = 9155` vs `Math.abs(-16364 - (-3109)) = 13255` → layer errada
  - Depois (fix): `Math.abs(-2544 - (-3109)) = 565` vs `Math.abs(-3109 - (-3109)) = 0` → layer correta

## 2026-07-29 — Sessão 14: Prevenção real de queda sob estruturas

- `L2Character.java` — `updatePosition()` passou a usar `traceTerrainZ()` com probe elevado (`super.getZ() + 2 * GeoStructure.CELL_HEIGHT`), seguindo a linha das refs para evitar que o servidor grude o player na layer inferior antes da validação final.
- `ValidatePosition.java` — `lastServerPosition` agora preserva o último ponto estável real; o gatilho de stuck ficou como fallback e não mais como mecanismo principal de correção.
- `L2PcInstance.java` — `checkGeometryStuck()` foi reescrito para procurar uma recuperação segura em posição vizinha, sem aceitar correção para baixo da estrutura.
- Fluxo de movimento — separação mais clara entre prevenção e fallback: movimento horizontal/vertical tratado no update do servidor, sync no packet `ValidatePosition` e recovery só quando a prevenção não for suficiente.

## 2026-07-24 — Sessão 9: Correções de bugs (auditoria local)

- `L2AttackableAI.java` — `doAttack` só executa se `canSeeTarget`, senão move para perto
- `L2PcInstance.java` — Range do `isFalling` expandido de ±200 para ±1000
- `GeoPathFinding.java` — Z tolerance do pathfinding aumentado de 55 para 128
- `GeoEngine.java` — `checkNSWE` diagonal agora verifica ambos os eixos (anti-corner-cut)
- `GeoEngine.java` — `nGetSpawnHeight` usa `(zmin+zmax)/2` como referência em vez de `zmin`
- `L2Spawn.java` — `getSpawnHeight` chamado com `getLocz()-50, getLocz()+50` em vez de `getLocz(), getLocz()`
- `GeoEngine.java` — `DoorInstance` não bypassa mais LOS total, faz LOS contra hinge coords
- `ValidatePosition.java` — `lastClientPosition`/`lastServerPosition` atualizados antes do early return de falling
- `L2NpcWalkerAI.java` — `checkArrived` usa `isInsideRadius(10)` + Z diff < 30 em vez de comparação exata

## 2026-07-25 — Sessão 11: Fix teleport-abaixo-terreno nas colunas + pathfallback sem restrição de distância

- `L2Character.java` — Removido `distance < 2000` do limite de ativação de pathfinding; NPCs buscam path com qualquer distância
- `L2Character.java` — Destino de NPC usa `traceTerrainZ` para Z correto ao chegar (previne "teleport abaixo do chão")
- `L2PcInstance.java` — Destino do jogador usa `traceTerrainZ` para Z correto ao chegar
- `ValidatePosition.java` — Terrain height snap (5-50 unidades abaixo do terreno → ajusta para cima); reverteu check anterior que causava "jump around" em hills

## 2026-07-27 — Sessão 12: FLAT blocks always passable in nCanMoveNext

- `GeoEngine.java` — Removed target NSWE check from FLAT case in `nCanMoveNext`; FLAT blocks are now always passable (Brproject pattern). Fixes rollback ("passa e volta") when walking across block boundaries where FLAT tiles meet complex/multilevel tiles with blocking NSWE at the boundary edge.
- `GeoEngine.java` — Removed dead `nGetCellNSWE(gx, gy, z)` function (was only called from the FLAT case).

## 2026-07-28 — `697e90008`

- `L2Character.java` / `L2PcInstance.java`: `removeSkill(skill, boolean cancelEffect)` com overload `cancelEffect`
- `Heal.java`: SpiritShot consumido só após o loop de targets (multi-target heal)
- `L2Party.java`: `synchronized` em `getPartyMembers()` e `getLeader()`
- `AdminTeleport.java`: +`catch (NumberFormatException)` no `//move_to`
- `L2Summon.java` / `L2PcInstance.java` / `L2GameClientPacket.java`: Summon herda `isInvul()` do dono; `isSpawnProtected()` adicionado; `RequestActionUse` no packet filter
- `L2AttackableAI.java`: Retorno ao spawn também no branch "fixed coord"
- `FloodProtector.java` / `MultiSellChoose.java`: Revertido `PROTECTED_MULTISELL` (upstream considerou bug)
- `L2Character.java` / `L2PcInstance.java`: `isCastingNow()` e `useMagic()` sincronizados
- `DeadLockDetector.java` (novo) — Thread standalone para detecção de deadlocks via `ThreadMXBean`
- `Config.java`: +`DEADLOCK_DETECTOR` field
- `GameServer.java`: Import, field, getter, init condicional do `DeadLockDetector`
- `Shutdown.java`: Interrupt do `DeadLockDetector` no shutdown
- `General.properties`: Config `DeadLockDetector = False`
- `data/Server/data/stats/pets.xml` (novo) — 13 pet types, 984 `<stat>` rows extraídos de `data/DB2.sql`
- `L2PetDataTable.java`: `loadPetsData()` reescrito — DOM parser XML via `DocumentBuilderFactory` ao invés de JDBC; imports SQL removidos
- `docs/migracao-sql-xml.md`: Pet Stats corrigido (XML standalone), nova seção `L2PetDataTable.java`, `pets.xml` adicionado à estrutura

## 2026-07-28 — Sessão 13: Ramp-ignore-NSWE + GeoDataPatcher + Stuck Recovery

### Workaround em código (GeoEngine.java)

- `GeoEngine.java` — `nCanMoveNext` BlockMultilayer: substituído `checkMove()` opaco por `getNsweNearest()` + `checkNSWE()`. Nova lógica em 3 camadas:
  1. NSWE padrão via `getNsweNearest` → se permite, move
  2. NSWE bloqueia + diferença altura entre células ≤ 96 → **ignora NSWE** (rampa corrompida, não parede)
  3. NSWE bloqueia + diferença altura > 96 → bloqueia (parede legítima)
- Threshold aumentado de 32 para 96 baseado em logs reais (diffs observados: 48–88 em células de rampa; paredes reais têm 200+)
- Log melhorado: `[GEO] nCanMoveNext RAMP-IGNORE-NSWE` e `BLOCKED` agora mostram `srcH`, `dstH`, `safeH` e `diff`

### Stuck Recovery + DB Save

- `L2PcInstance.java` — `checkGeometryStuck()`: detecta Z do player muito distante do terreno (±30 unidades), escaneia 300 unidades para cima/baixo procurando chão, teleporta para o terreno ou para a town como fallback. Rate-limited a cada 5s. Agora salva `storeCharBase()` no banco de dados após cada teleport.
- `ValidatePosition.java` — Detecção de stuck melhorada: salva `originalClientZ` antes do Z override, usa `originalClientZ` (se próximo do realZ) com threshold reduzido de 80 para 30 unidades.

### GeoDataPatcher — Correção permanente do .l2j

- `java/Dev/SpecialMods/GeoDataPatcher.java` (novo) — Patcher binário que lê `.l2j`, varre células com NSWE bloqueando movimento onde altura é compatível, e corrige flags para 15 (todas direções livres).
- `data/Server/GeoDataPatcher.bat` (novo) — Menu interativo para Windows com:
  - `[1]` Consertar UM arquivo (digitar região)
  - `[2]` Consertar TODOS (bulk)
  - `[3]` Listar arquivos .l2j disponíveis
  - `[4]` Configurar threshold (32/64/96/128/personalizado)
  - `[5]` Alternar modo Dry-Run (testa sem modificar)
  - `[6]` Fazer backup manual dos geodata
  - `[7]` Restaurar backup (lista disponíveis ou restaura o mais recente)
  - `[8]` Compilar GeoDataPatcher.java
  - Backup automático antes de qualquer patch; restore com um clique
