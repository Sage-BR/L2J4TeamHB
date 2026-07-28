# Migração SQL → XML — Status

## Objetivo

Migrar todos os dados de NPCs (`npc`, `npcskills`, `pets_stats`, `skill_learn`, `minions`) e Itens (`armor`, `armor_sets`, `etcitem`) do SQL para arquivos XML, mantendo compilação e sem perda de dados.

---

## ✅ Concluído

### NPC Templates (8063 NPCs + 3 customs)
- SQL: `npc`, `custom_npc`
- XML: 19 arquivos em `data/Server/data/xml/npcs/` + 1 em `data/Server/data/xml/npcs/customs/`
- Faixas de 1000 IDs: `12000-12999.xml` a `1000000-1000999.xml` + `50000-50999.xml` (customs)

### Skills (21968 tags `<skill>`)
- SQL: `npcskills` (22062 registros)
- XML: tags `<skills><skill id="..." level="..."/></skills>` em cada NPC
- 94 registros órfãos no SQL (NPC ID inexistente) — ignorados

### Pet Stats (984 tags `<stat>`)
- SQL: `pets_stats` (tabela `hellb.pets_stats` inexistente no banco)
- XML: `data/Server/data/stats/pets.xml` — 13 pet types (wolf, great_wolf, hatchling_of_wind/star/twilight, strider_of_wind/star/twilight, wyvern, sin_eater, baby buffalo/cougar/kookaburra)
- Estrutura: `<list><pet type="..." typeId="N"><stat level="L" .../></pet></list>`
- `L2PetDataTable.java`: `loadPetsData()` reescrito — DOM parser via `DocumentBuilderFactory` ao invés de JDBC; imports SQL removidos

### Skill Learn (256 tags `<teachTo>`)
- SQL: `skill_learn` (1630 registros)
- XML: atributo `classes` na tag `<teachTo/>` agregando class_ids por NPC

### Minions (445 tags `<minion>`)
- SQL: `minions` (445 registros)
- XML: blocos `<minions><minion id="..." min="..." max="..."/></minions>` nos bosses

### Armor Stats (1378 itens)
- SQL: `armor` (1378 registros)
- XML: tags `<set>` com dados estruturais em `data/Server/data/stats/armor/*.xml` (48 arquivos existentes + 4 criados para custom armors)
- Dados injetados: bodypart, crystallizable, armor_type, weight, material, crystal_type, avoid_modify, duration, price, crystal_count, sellable, dropable, destroyable, tradeable, skill, type1, type2, p_def, m_def, mp_bonus
- Custom armors (IDs 10018-10631) em novos arquivos 10300-10699.xml
- Arquivos criados: 10300-10399.xml (12), 10400-10499.xml (25), 10500-10599.xml (28), 10600-10699.xml (20)

### ArmorSets (+ custom armorsets)
- SQL: `armor_sets` (78 sets) → XML `data/Server/data/stats/armorsets.xml`
- Custom armorsets: `data/Server/data/stats/custom_armorsets.xml` (vazio, suporte a `Config.CUSTOM_ARMORSETS_TABLE`)

### EtcItem (7387 itens)
- SQL: `etcitem` (7387 registros)
- XML: tags `<set>` com dados estruturais em `data/Server/data/stats/itens/etcitem/*.xml` (11 arquivos, IDs 0-10999)
- Dados injetados: item_type, consume_type, crystallizable, weight, material, crystal_type, duration, price, crystal_count, sellable, dropable, destroyable, tradeable, type1, type2, bodypart, stackable
- `SkillsEngine.java`: linha `hashFiles("data/stats/itens/etcitem", _etcitemFiles)` reativada
- `DocumentItem.java`: detecção de `item_type` → `L2EtcItemType` + mapeamento baseado no `readItem()` original
- `ItemTable.java`: query `etcitem` removida de `SQL_ITEM_SELECTS`; `Item.readItem()` mantido como legado, `SQL_ITEM_SELECTS` restrito a array vazio

### NpcTable.java
- Loading: `restoreNpcData()` → `loadNpcsFromXml()` (parser DOM completo com `<set>`, `<ai>`, `<skills>`, `<teachTo>`, `<petdata>`, `<minions>`)
- `reloadNpc()`: restaura de template em memória (skills, drops, minions, teachInfo)
- `saveNpc()`: escreve no XML (encontra arquivo por faixa de ID, atualiza `<set>` e atributos)

---

## 📦 Mantido em SQL

### Droplist (27405 registros, 2335 NPCs)
- Tabelas: `droplist`, `custom_droplist`
- Carregado via SQL em `restoreNpcData()` — mantido como legado
- Motivo: volume alto de dados, edição frequente por admins

### Custom weapon
- Tabela: `custom_weapon`
- Ainda não migrada para XML
- Aguardando decisão

---

## 📁 Estrutura

```
data/Server/data/stats/
├── pets.xml (novo, 984 stat rows)
├── armorsets.xml
├── custom_armorsets.xml
├── itens/
│   ├── armor/
│   │   ├── 0000-0099.xml (injetado)
│   │   ├── ...
│   │   ├── 10300-10399.xml (novo, custom)
│   │   ├── 10400-10499.xml (novo, custom)
│   │   ├── 10500-10599.xml (novo, custom)
│   │   └── 10600-10699.xml (novo, custom)
│   └── etcitem/
│       ├── 0-999.xml (novo)
│       ├── 1000-1999.xml (novo)
│       ├── ...
│       └── 10000-10999.xml (novo)
```

---

## ⚙️ L2PetDataTable.java

| Método | Função |
|--------|--------|
| `loadPetsData()` | XML → `data/Server/data/stats/pets.xml` via `DocumentBuilderFactory` (DOM) |
| `addPetData()` | Insere/atualiza `L2PetData` no `_petTable` por petID + level |

### Mapa de attributes XML → campos L2PetData

| Atributo XML | Campo L2PetData |
|--------------|-----------------|
| `level` | `PetLevel` |
| `expMax` | `PetMaxExp` (long) |
| `hpMax`, `mpMax` | `PetMaxHP`, `PetMaxMP` |
| `patk`, `pdef`, `matk`, `mdef` | `PetPAtk`, `PetPDef`, `PetMAtk`, `PetMDef` |
| `acc` | `PetAccuracy` |
| `evasion` | `PetEvasion` |
| `crit` | `PetCritical` |
| `speed` | `PetSpeed` |
| `atk_speed` | `PetAtkSpeed` |
| `cast_speed` | `PetCastSpeed` |
| `feedMax` | `PetMaxFeed` |
| `feedbattle`, `feednormal` | `PetFeedBattle`, `PetFeedNormal` |
| `loadMax` | `PetMaxLoad` |
| `hpregen`, `mpregen` | `PetRegenHP`, `PetRegenMP` |
| `owner_exp_taken` | `OwnerExpTaken` (float) |

---

## ⚙️ NpcTable.java

| Método | Função |
|--------|--------|
| `restoreNpcData()` | XML → `loadNpcsFromXml()` + SQL → `droplist` |
| `loadNpcsFromXml(String dir)` | Parseia `<npc>` com `<set>`, `<ai>`, `<skills>`, `<teachTo>`, `<petdata>`, `<minions>` |
| `saveNpc(StatsSet npc)` | Escreve `StatsSet` no XML (encontra arquivo por range de ID) |
| `reloadNpc(int id)` | Restaura de template em memória |
| `getNpcXmlFile(int id)` | Localiza arquivo XML pela faixa de ID (range = `id / 1000 * 1000`) |

### Mapa de chaves `saveNpc()`

Chaves `StatsSet` → nomes de atributos XML resolvidos:

| StatsSet key | XML `<set name="...">` |
|--------------|------------------------|
| `npcId` | (identificador do NPC, não é set) |
| `collision_radius` | `radius` |
| `collision_height` | `height` |
| `rhand` | `rHand` |
| `lhand` | `lHand` |
| `hpreg` | `hpRegen` |
| `mpreg` | `mpRegen` |
| `patk` | `pAtk` |
| `pdef` | `pDef` |
| `matk` | `mAtk` |
| `mdef` | `mDef` |
| `atkspd` | `atkSpd` |
| `runspd` | `runSpd` |
| `attackrange` | `attackRange` |
| `name`, `title`, `idTemplate` | atributos da tag `<npc>` |
| `serverSideName` | `usingServerSideName` |
| `serverSideTitle` | `usingServerSideTitle` |

Chaves sem equivalente XML (`armor`, `faction_id`, `faction_range`, `isUndead`, `absorb_level`, `aggro`, `matkspd`) são ignoradas no salvamento.

---

## 🔮 Próximos Passos Possíveis

- Migrar `custom_weapon` do SQL para XML
- Migrar `droplist` do SQL para XML (27405 registros, 2335 NPCs)
- Adicionar parser/suporte para `<drops>` no `loadNpcsFromXml()`
- Criar script de injeção de drops nos XMLs
