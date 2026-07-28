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
