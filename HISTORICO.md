# Histórico de Modificações

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

## 2026-07-25 — Sessão 11: Fix teleport-abaixo-terreno nas colunas + pathfallback sem restrição de distância + correção NSWE entre colunas

- `L2Character.java` — Removido `distance < 2000` do limite de ativação de pathfinding; NPCs buscam path com qualquer distância
- `L2Character.java` — Destino de NPC usa `traceTerrainZ` para Z correto ao chegar (previne "teleport abaixo do chão" ao rotear entre colunas)
- `L2PcInstance.java` — Destino do jogador usa `traceTerrainZ` para Z correto ao chegar (previne teleport abaixo do chão ao forçar pathing entre colunas)
- `ValidatePosition.java` — Terrain height snap (5-50 unidades abaixo do terreno → ajusta para cima); reverteu check anterior que causava "jump around" em hills
- `GeoEngine.java` — `nCanMoveNext` FLAT case agora usa `nGetHeight(tx,ty,z)` para consultar NSWE do alvo na altura do terreno do alvo (corrige oscilação "passa e volta" entre colunas)
- Branch `upstream/feature/geoengine_and_movement_stabilization` — commits analisados; `c0b8a1940` removeu o bad check de ValidatePosition e usa traceTerrainZ no destino ao invés disso
- Branch `upstream/feature/colosseum_fences` — commit `22678f0ad` aplicado
- Branch `upstream/fix/door_coords` — `47f30f98d` NÃO aplicável (getX/getY final em L2Object, sem DoorData/L2DoorTemplate)
- Branch `upstream/feature/attackable-ai-rework` — refactor massivo disponível para futuro cherry-pick

## 2026-07-24 — Sessão 8

## 2026-07-24 — Sessão 7: Correção column bug + ajustes ValidatePosition

- `GeoEngine.java` — `nCanMoveNext`: NSWE da célula de destino verificado apenas quando origem é FLAT
- `ValidatePosition.java` — Rolagem não causa mais 3+ rollbacks ao andar perto de colunas
- Índices de geodata/path sincronizados com arquivos reais no disco
