# Histórico de Modificações

Java 25 c/ virtual threads e I/O
remoção de javolution 
Atualização das Libs
Recompilar MMOCORE
Fix Compilador dos Scripts

Commits aplicados manualmente no servidor, baseados em análise do repositório [l2j-server-game](https://bitbucket.org/l2jserver/l2j-server-game/commits/) (BitBucket) e correções próprias.

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

### Próximos passos necessários

- Re-executar o GeoDataPatcher **corrigido** sobre os arquivos restaurados para corrigir NSWE flags.
- Revisar `nCanMoveNext` RAMP-IGNORE-NSWE — com geodata correto, pode não ser mais necessário.

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

## 2026-07-24 — Sessão 8: Implementações dos commits L2J (página 4+)

- `GeoEngine.java` — Adicionado `nTraceTerrainZ`: percorre célula por célula coletando altura do terreno
- `GeoData.java` — Adicionado `traceTerrainZ` wrapper público
- `L2Character.java` — `updatePosition` usa `traceTerrainZ` para Z de NPCs (substitui snap antigo)
- `L2PcInstance.java` — `updatePosition` usa `traceTerrainZ` para Z de players (substitui interpolação linear)
- `L2PcInstance.java` — Adicionado `isFalling()`/`stopFalling()` com `_fallingTimestamp` (delay 1s)
- `ValidatePosition.java` — Early return se `isFalling(_z)` para evitar "jumping"
- `AbstractAI.java` — `moveToPawn`: se `!canSeeTarget`, offset = 0 (NPC vai direto ao alvo)
- `L2AttackableAI.java` — Range check: `mostHate.isMoving() || npc.isMoving()` (ambos, não só target)
- `GeoEngine.java` — Adicionado `nGetCellNSWE(gx, gy, z)` helper
- `GeoEngine.java` — `nCanMoveNext`: NSWE de destino só para FLAT (removido de complex/multilevel — causava rollbacks)
- `ValidatePosition.java` — Removido geo-collision check (`canMoveToTarget`/`getValidLocation`) por tick
- `geo_index.txt` — Sincronizado com 156 arquivos .l2j no disco
- `pn_index.txt` — Sincronizado com 156 arquivos .pn no disco

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
- `GeoEngine.java` — `nCanMoveNext` FLAT case usava `nGetHeight(tx,ty,z)` para consultar NSWE do alvo na altura do terreno do alvo (corrige oscilação "passa e volta" entre colunas) — **Revertido na sessão 12**; FLAT blocks são agora sempre passáveis (Brproject pattern).
- Branch `upstream/feature/geoengine_and_movement_stabilization` — commits analisados; `c0b8a1940` removeu o bad check de ValidatePosition e usa traceTerrainZ no destino ao invés disso
- Branch `upstream/feature/colosseum_fences` — commit `22678f0ad` aplicado
- Branch `upstream/fix/door_coords` — `47f30f98d` NÃO aplicável (getX/getY final em L2Object, sem DoorData/L2DoorTemplate)
- Branch `upstream/feature/attackable-ai-rework` — refactor massivo disponível para futuro cherry-pick

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
  - Parseia blocos Flat (1 layer), Complex (1 layer) e Multilayer (múltiplas camadas)
  - `findNearestHeight()` — para células Multilayer, escaneia **todas as camadas** do vizinho e retorna a altura mais próxima da camada atual (evita falso negativo comparando chão vs teto)
  - Threshold 96 (configurável via `-t`)
  - Suporta dry-run via `-o <output>`

- `16_19.l2j` — Aplicado patch permanente: 1.308.720 células corrigidas (935k Complex + 374k Multilayer)

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
