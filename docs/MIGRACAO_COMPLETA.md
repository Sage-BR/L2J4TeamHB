# Migração SQL → XML — Documentação Completa

> **Última atualização:** Julho/2026
> **Projeto:** L2J4TeamHB — Server Hellbound

---

## Índice

1. [Visão Geral](#1-visão-geral)
2. [NPC Templates](#2-npc-templates)
3. [Skills](#3-skills)
4. [Pet Stats](#4-pet-stats)
5. [Skill Learn](#5-skill-learn)
6. [Minions](#6-minions)
7. [Armor Stats](#7-armor-stats)
8. [ArmorSets](#8-armorsets)
9. [Weapon Stats](#9-weapon-stats)
10. [EtcItem](#10-etcitem)
11. [Bodypart Fix (Sessão Atual)](#11-bodypart-fix-sessão-atual)
12. [Estrutura Final de Diretórios](#12-estrutura-final-de-diretórios)
13. [Arquivos Java Modificados](#13-arquivos-java-modificados)
14. [Configuração](#14-configuração)
15. [Observações Técnicas](#15-observações-técnicas)

---

## 1. Visão Geral

### Objetivo

Migrar todos os dados de NPCs, Itens, Skills, Pets e configurações do SQL para XML, eliminando dependência de banco de dados para dados estáticos do jogo. O servidor deve funcionar com banco mínimo (apenas contas, personagens, clans, etc.).

### Princípios

- **Convention over configuration**: Se o arquivo XML existe, carrega. Sem flags de ativar/desativar.
- **Auto-detecção**: Subpasta `/custom/` em qualquer diretório de itens carrega automaticamente.
- **Defaults seguros**: `DocumentItem.java` define defaults para campos ausentes em XMLs parcialmente migrados.

### Estado Atual

| Componente | Origem | Status |
|---|---|---|
| NPC Templates | XML (19 arquivos) | ✅ Migrado |
| Skills (npcskills) | XML (embutido nos NPCs) | ✅ Migrado |
| Pet Stats | XML (1 arquivo) | ✅ Migrado |
| Skill Learn | XML (embutido nos NPCs) | ✅ Migrado |
| Minions | XML (embutido nos NPCs) | ✅ Migrado |
| Armor Stats | XML (53 arquivos) | ✅ Migrado + bodypart fix |
| ArmorSets | XML (1 arquivo) | ✅ Migrado |
| Custom ArmorSets | XML (1 arquivo) | ✅ Auto-detectado |
| Weapon Stats | XML (11 arquivos + custom/) | ✅ Migrado + bodypart fix |
| EtcItem | XML (11 arquivos) | ✅ Migrado + bodypart fix |
| Droplist | SQL (27405 registros) | ⏳ Mantido em SQL |
| Custom Items | SQL (via `custom_*` tabelas) | ⏳ Legado (não usado) |

---

## 2. NPC Templates

### Origem
- SQL: `npc`, `custom_npc`
- XML: 19 arquivos em `data/xml/npcs/` + 1 em `data/xml/npcs/customs/`

### Arquivos
```
data/xml/npcs/
├── 12000-12999.xml
├── 13000-13999.xml
├── 14000-14999.xml
├── 16000-16999.xml
├── 18000-18999.xml
├── 20000-20999.xml
├── 21000-21999.xml
├── 22000-22999.xml
├── 25000-25999.xml
├── 27000-27999.xml
├── 29000-29999.xml
├── 30000-30999.xml
├── 31000-31999.xml
├── 32000-32999.xml
├── 35000-35999.xml
├── 36000-36999.xml
├── 50000-50999.xml
├── 70000-70999.xml
├── 1000000-1000999.xml
└── customs/
    └── 50000-50999.xml
```

### Total
- **8063 NPCs** carregados dos XMLs principais
- **3 NPCs** carregados de `customs/`

### Estrutura XML
```xml
<npc id="12345" name="Orc" title="" usingServerSideName="false" usingServerSideTitle="false">
  <set name="radius" val="8.0"/>
  <set name="height" val="24.0"/>
  <set name="rHand" val="0"/>
  <set name="lHand" val="0"/>
  <set name="pAtk" val="100"/>
  <set name="pDef" val="200"/>
  <!-- ... -->
  <ai type="L2Attackable"/>
  <skills>
    <skill id="123" level="1"/>
  </skills>
  <teachTo classes="1,2,3"/>
  <petdata>
    <stat level="1" expMax="1000" hpMax="200" .../>
  </petdata>
  <minions>
    <minion id="50000" min="1" max="3"/>
  </minions>
</npc>
```

### Carregamento
- `NpcTable.restoreNpcData()` → `loadNpcsFromXml()` (parser DOM completo)
- `reloadNpc()` restaura template em memória (skills, drops, minions, teachInfo)
- `saveNpc()` escreve no XML (encontra arquivo por faixa de ID)

---

## 3. Skills

### Origem
- SQL: `npcskills` (22062 registros)
- XML: tags `<skills>` embutidas nos NPCs

### Total
- **21968 tags `<skill>`** nos XMLs de NPC
- **94 registros órfãos** no SQL (NPC ID inexistente) — ignorados

### Estrutura XML
```xml
<npc id="..." name="..." ...>
  <!-- ... -->
  <skills>
    <skill id="123" level="1"/>
    <skill id="456" level="3"/>
  </skills>
  <!-- ... -->
</npc>
```

---

## 4. Pet Stats

### Origem
- SQL: `pets_stats` (tabela inexistente no banco `hellb`)
- XML: `data/stats/pets.xml`

### Total
- **984 tags `<stat>`**
- **13 tipos de pet**: wolf, great_wolf, hatchling_of_wind/star/twilight, strider_of_wind/star/twilight, wyvern, sin_eater, baby buffalo/cougar/kookaburra

### Estrutura XML
```xml
<list>
  <pet type="wolf" typeId="12077">
    <stat level="1" expMax="1000" hpMax="200" mpMax="100"
          patk="10" pdef="20" matk="5" mdef="15"
          acc="40" evasion="30" crit="5" speed="150"
          atk_speed="300" cast_speed="300"
          feedMax="100" feedbattle="5" feednormal="2"
          loadMax="1000" hpregen="1.5" mpregen="0.5"
          owner_exp_taken="0.15"/>
  </pet>
</list>
```

### Código
- `L2PetDataTable.loadPetsData()` reescrito — DOM parser via `DocumentBuilderFactory` (não mais JDBC)
- Imports SQL (JDBC) removidos

---

## 5. Skill Learn

### Origem
- SQL: `skill_learn` (1630 registros)
- XML: tags `<teachTo>` embutidas nos NPCs

### Total
- **256 tags `<teachTo>`**

### Estrutura XML
```xml
<npc id="..." name="..." ...>
  <!-- ... -->
  <teachTo classes="1,2,3"/>
  <!-- classes = class_ids separados por vírgula -->
</npc>
```

---

## 6. Minions

### Origem
- SQL: `minions` (445 registros)
- XML: tags `<minions>` embutidas nos NPCs

### Total
- **445 tags `<minion>`**

### Estrutura XML
```xml
<npc id="12345" name="Boss" ...>
  <!-- ... -->
  <minions>
    <minion id="50000" min="1" max="3"/>
  </minions>
</npc>
```

---

## 7. Armor Stats

### Origem
- SQL: `armor` (1378 registros) + `custom_armor`
- XML: 53 arquivos em `data/stats/itens/armor/`

### Diretório
```
data/stats/itens/armor/
├── 0000-0099.xml a 9900-9999.xml (49 arquivos existentes)
├── 10300-10399.xml (novo, custom — 12 armors)
├── 10400-10499.xml (novo, custom — 25 armors)
├── 10500-10599.xml (novo, custom — 28 armors)
├── 10600-10699.xml (novo, custom — 20 armors)
└── custom/ (subpasta opcional, auto-carregada)
```

### Estrutura XML
```xml
<item id="21" name="Shirt">
  <set name="armor_type" val="light"/>
  <set name="bodypart" val="1024"/>     <!-- SLOT_CHEST -->
  <set name="crystallizable" val="false"/>
  <set name="weight" val="4830"/>
  <set name="material" val="10"/>        <!-- cloth -->
  <set name="crystal_type" val="0"/>
  <set name="p_def" val="36"/>
  <set name="m_def" val="0"/>
  <set name="price" val="147"/>
  <set name="sellable" val="true"/>
  <set name="dropable" val="true"/>
  <set name="destroyable" val="true"/>
  <set name="tradeable" val="true"/>
  <set name="skill" val="0-0;"/>
  <for>
    <!-- stat modifiers existentes -->
    <add val='36' order='0x10' stat='pDef'/>
  </for>
</item>
```

### Carregamento
- `SkillsEngine.getInstance().loadArmors(armorData)` com `hashFiles("data/stats/itens/armor", _armorFiles)`
- Subpasta `/custom/` carregada automaticamente

---

## 8. ArmorSets

### Origem
- SQL: `armor_sets` (78 sets)
- XML: `data/stats/armorsets.xml`

### Custom ArmorSets
- XML: `data/stats/custom_armorsets.xml`
- Carregado automaticamente se o arquivo existir (sem config)
- `ArmorSetsTable.java`: chamada `loadFromXml()` incondicional (não depende de `Config.CUSTOM_ARMORSETS_TABLE`)

### Estrutura XML
```xml
<list>
  <armorset chest="23" legs="2386" head="43" gloves="0" feet="0"
            skill_id="3500" skill_lvl="1" shield="0" shield_skill_id="0"
            enchant6skill="0"/>
</list>
```

---

## 9. Weapon Stats

### Origem
- SQL: `weapon` (1402 registros) + `custom_weapon`
- XML: 11 arquivos em `data/stats/itens/weapon/`

### Diretório
```
data/stats/itens/weapon/
├── 0000-0999.xml  (354 weapons)
├── 1000-1999.xml  (29 weapons)
├── 2000-2999.xml  (133 weapons)
├── 3000-3999.xml  (26 weapons)
├── 4000-4999.xml  (237 weapons)
├── 5000-5999.xml  (95 weapons)
├── 6000-6999.xml  (91 weapons)
├── 7000-7999.xml  (79 weapons)
├── 8000-8999.xml  (229 weapons)
├── 9000-9999.xml  (297 weapons)
├── 10000-10999.xml (79 weapons)
└── custom/        (subpasta opcional, auto-carregada)
```

### Total
- **1649 weapons** carregadas dos XMLs
- 1599 blocos `<for>` preservados dos XMLs originais (stat modifiers)
- Tipos: sword (53), blunt (44), none/shield (64), dagger (34), bow (22), pole (25), fist (9), etc.

### Estrutura XML
```xml
<item id="1" name="Short Sword">
  <set name="weapon_type" val="sword"/>
  <set name="bodypart" val="128"/>       <!-- SLOT_R_HAND -->
  <set name="crystallizable" val="false"/>
  <set name="weight" val="1600"/>
  <set name="soulshots" val="1"/>
  <set name="spiritshots" val="1"/>
  <set name="material" val="0"/>         <!-- steel -->
  <set name="crystal_type" val="0"/>
  <set name="p_dam" val="8"/>
  <set name="rnd_dam" val="10"/>
  <set name="critical" val="8"/>
  <set name="atk_speed" val="379"/>
  <set name="m_dam" val="6"/>
  <set name="duration" val="-1"/>
  <set name="price" val="768"/>
  <set name="sellable" val="true"/>
  <set name="dropable" val="true"/>
  <set name="destroyable" val="true"/>
  <set name="tradeable" val="true"/>
  <set name="skill" val="0-0;"/>
  <for>
    <set val='8' order='0x08' stat='pAtk'/>
    <set val='6' order='0x08' stat='mAtk'/>
    <set val='8' order='0x08' stat='rCrit'/>
  </for>
</item>
```

### Carregamento 100% XML
- `ItemTable.java`: `SQL_ITEM_SELECTS = {}` (vazio — sem SQL)
- `SkillsEngine.getInstance().loadWeapons(weaponData)` com `weaponData` vazio
- `DocumentItem.java`: defaults para campos L2Weapon ausentes

---

## 10. EtcItem

### Origem
- SQL: `etcitem` (5361 registros) + `custom_etcitem`
- XML: 11 arquivos em `data/stats/itens/etcitem/`

### Diretório
```
data/stats/itens/etcitem/
├── 0-999.xml a 9000-9999.xml (10 arquivos)
├── 10000-10999.xml (1 arquivo)
└── custom/ (subpasta opcional, auto-carregada)
```

### Total
- **7387 itens** carregados dos XMLs
- Tipos: potion, scroll, shot, material, recipe, spellbook, arrow, bolt, herb, quest, pet_collar, seed, lure, etc.

### Estrutura XML
```xml
<item id="1835" name="Soulshot: No-Grade">
  <set name="item_type" val="shot"/>
  <set name="consume_type" val="stackable"/>
  <set name="crystallizable" val="false"/>
  <set name="weight" val="3"/>
  <set name="material" val="0"/>
  <set name="crystal_type" val="0"/>
  <set name="duration" val="-1"/>
  <set name="price" val="4"/>
  <set name="crystal_count" val="0"/>
  <set name="sellable" val="true"/>
  <set name="dropable" val="true"/>
  <set name="destroyable" val="true"/>
  <set name="tradeable" val="true"/>
  <set name="type1" val="4"/>
  <set name="type2" val="0"/>
  <set name="bodypart" val="0"/>
  <set name="stackable" val="true"/>
</item>
```

### Carregamento
- `SkillsEngine.getInstance().loadItems(itemData)` com `data/stats/itens/etcitem`
- `DocumentItem.parseItem()` detecta `item_type` e mapeia para `L2EtcItemType`

---

## 11. Bodypart Fix (Sessão Atual)

### O Problema

Os valores de `bodypart` nos XMLs exportados do SQL estavam **incorretos**. O código Java original (`ItemTable.readWeapon()`, `readItem()`) usava o mapa `_slots` para converter strings como `'rhand'`, `'chest'` para as constantes Java corretas:

```java
_slots.put("rhand", L2Item.SLOT_R_HAND);    // 128
_slots.put("chest", L2Item.SLOT_CHEST);     // 1024
```

O script de exportação SQL→XML pulou esse mapeamento e usou valores numéricos errados:
- **Weapons**: 1, 2, 3 ao invés de 128, 256, 16384
- **Armors**: valores "shifted" (ex: chest=2048 ao invés de 1024)
- **Armors**: valores ×64 para talismãs/braceletes (268435456 ao invés de 4194304)
- **Armors**: valores 1, 3, 7 para acessórios (ao invés de 8, 6, 48)
- **Etcitems**: bodypart=4 para flechas/bolts (ao invés de 256)

### Impacto

- **Armaduras não equipavam**: bodypart não correspondia a slot válido
- **Soulshots não ativavam**: `getActiveWeaponItem()` retornava nulo
- **Itens de pet não funcionavam**: bodypart incorreto

### O que foi corrigido

#### Weapons (1649 itens)

| Valor Antigo ❌ | Novo Valor ✅ | Slot |
|---|---|---|
| 1 | 128 | SLOT_R_HAND |
| 2 | 256 | SLOT_L_HAND |
| 3 | 16384 | SLOT_LR_HAND |
| 19, 17, 20, 18 | -100 a -104 | SLOT_WOLF a SLOT_GREATWOLF |

#### Armors (~2900 itens)

| Valor Antigo ❌ | Novo Valor ✅ | Slot |
|---|---|---|
| 64 | 512 | SLOT_GLOVES |
| 1024 | 64 | SLOT_HEAD |
| 2048 | 1024 | SLOT_CHEST |
| 4096 | 2048 | SLOT_LEGS |
| 32768 | 4096 | SLOT_FEET |
| 16384 | 32768 | SLOT_FULL_ARMOR |
| 1 | 8 | SLOT_NECK (colares) |
| 3 | 6 | SLOT_R_EAR\|SLOT_L_EAR (brincos) |
| 7 | 48 | SLOT_R_FINGER\|SLOT_L_FINGER (anéis) |
| 268435456 | 4194304 | SLOT_DECO (talismãs) |
| 67108864 | 1048576 | SLOT_R_BRACELET (braceletes) |

#### Etcitems (7387 itens)

| Valor Antigo ❌ | Novo Valor ✅ | Slot |
|---|---|---|
| 4 | 256 | SLOT_L_HAND (flechas/bolts) |

### Scripts de Correção

- `scripts/fix_bodypart_v2.ps1` — Correção inicial (shifted values)
- `scripts/fix_bodypart_v3.sh` — Correção com suporte a espaços
- `scripts/fix_all_bodyparts.js` — Script Node.js com lookup do DB2.sql
- `scripts/fix_pet_weapons.js` — Correção de pet weapons

---

## 12. Estrutura Final de Diretórios

```
data/Server/
├── config/
│   └── General.properties    (sem flags de custom — auto-detectado)
├── data/
│   ├── xml/
│   │   └── npcs/
│   │       ├── 12000-12999.xml ... 1000000-1000999.xml  (19 arquivos)
│   │       └── customs/
│   │           └── 50000-50999.xml
│   └── stats/
│       ├── pets.xml
│       ├── armorsets.xml
│       ├── custom_armorsets.xml
│       ├── itens/
│       │   ├── armor/       (53 XMLs + custom/)
│       │   │   ├── 0000-0099.xml ... 9900-9999.xml
│       │   │   ├── 10300-10399.xml ... 10600-10699.xml
│       │   │   └── custom/
│       │   ├── weapon/      (11 XMLs + custom/)
│       │   │   ├── 0000-0999.xml ... 10000-10999.xml
│       │   │   └── custom/
│       │   └── etcitem/     (11 XMLs)
│       │       ├── 0-999.xml ... 9000-9999.xml
│       │       ├── 10000-10999.xml
│       │       └── custom/
│       └── skills/
```

---

## 13. Arquivos Java Modificados

### `ItemTable.java`
- `SQL_ITEM_SELECTS` e `SQL_CUSTOM_ITEM_SELECTS` → arrays vazios `{}`
- Weapons, armors e etcitems carregados exclusivamente de XML
- Métodos `readWeapon()`, `readItem()` mantidos como legado (não chamados)

### `SkillsEngine.java`
- Caminhos alterados:
  - Weapons: `"data/stats/weapon"` → `"data/stats/itens/weapon"`
  - Armors: `"data/stats/armor"` → `"data/stats/itens/armor"`
  - Etcitems: `"data/stats/itens/etcitem"` (reativado)
- `hashFiles()`: carrega todos `.xml` do diretório + subpasta `custom/`

### `DocumentItem.java`
- **18 defaults** para campos L2Weapon (soulshots, p_dam, critical, etc.)
- **Defaults** para campos L2Item (type1, type2, weight, material, bodypart, etc.)
- **Defaults** para campos L2Armor (avoid_modify, p_def, m_def, skill)
- Detecção automática de tipo: `weapon_type` → `L2WeaponType`, `armor_type` → `L2ArmorType`, `item_type` → `L2EtcItemType`

### `NpcTable.java`
- `restoreNpcData()` → `loadNpcsFromXml()` (parser DOM completo)
- `saveNpc()`: escreve no XML (encontra arquivo por faixa de ID)
- `reloadNpc()`: restaura template em memória

### `L2PetDataTable.java`
- `loadPetsData()` reescrito: DOM parser via `DocumentBuilderFactory`
- Imports SQL (JDBC) removidos

### `ArmorSetsTable.java`
- `if (Config.CUSTOM_ARMORSETS_TABLE)` removido — `loadFromXml()` chamado incondicionalmente
- `import net.sf.l2j.Config` removido (não usado)

### `Config.java`
- `CUSTOM_ITEM_TABLES` mantido com default `false` (não usado para XML)
- `CUSTOM_ARMORSETS_TABLE` mantido com default `false` (não usado — removido do ArmorSetsTable)

### `General.properties`
- Linhas `CustomArmorTable`, `CustomArmorSetsTable`, `CustomWeaponTable` removidas
- Sem flags de ativação de custom — tudo auto-detectado por presença de arquivo

---

## 14. Configuração

### Princípio: Zero Config

Não é necessário configurar **nada** no `General.properties` para que itens customizados funcionem. Basta colocar o arquivo XML na subpasta `/custom/`:

```
data/stats/itens/weapon/custom/meu_item.xml    → auto-carregado ✅
data/stats/itens/armor/custom/meu_item.xml     → auto-carregado ✅
data/stats/custom_armorsets.xml                → auto-carregado ✅
```

### Flags que ainda existem (mas não são necessárias)

```properties
# NÃO PRECISA — tudo auto-detectado
# CustomItemTables = False   (para tabelas SQL legadas)
```

---

## 15. Observações Técnicas

### Droplist (Não Migrado)

As 27405 entradas de `droplist` para 2335 NPCs únicos **não foram migradas** para XML. Motivo:
- Alto volume de dados
- Edição frequente por admins via SQL
- Seria necessário criar suporte a `<drops>` no `loadNpcsFromXml()`

### Custom Items (Legado SQL)

As tabelas `custom_etcitem`, `custom_weapon`, `custom_armor` ainda existem no banco.
- `Config.CUSTOM_ITEM_TABLES = false` (default)
- Se ativado, os dados SQL substituem os XMLs para os IDs correspondentes
- Recomendação: migrar para XML em `/custom/` e desligar SQL

### Bodypart - Mapa de Constantes

| Nome | Valor | Uso |
|---|---|---|
| SLOT_NONE | 0 | Etcitems não-equipáveis |
| SLOT_UNDERWEAR | 1 | Underwear |
| SLOT_R_EAR | 2 | Brinco direito |
| SLOT_L_EAR | 4 | Brinco esquerdo |
| SLOT_NECK | 8 | Colar |
| SLOT_R_FINGER | 16 | Anel direito |
| SLOT_L_FINGER | 32 | Anel esquerdo |
| SLOT_HEAD | 64 | Capacete |
| SLOT_R_HAND | 128 | Mão direita (arma 1H) |
| SLOT_L_HAND | 256 | Mão esquerda (escudo) |
| SLOT_GLOVES | 512 | Luvas |
| SLOT_CHEST | 1024 | Peito |
| SLOT_LEGS | 2048 | Pernas |
| SLOT_FEET | 4096 | Pés |
| SLOT_BACK | 8192 | Costas (capa) |
| SLOT_LR_HAND | 16384 | Duas mãos (arma 2H) |
| SLOT_FULL_ARMOR | 32768 | Armadura completa |
| SLOT_HAIR | 65536 | Cabelo |
| SLOT_ALLDRESS | 131072 | Belt/Vestido |
| SLOT_HAIR2 | 262144 | Face |
| SLOT_HAIRALL | 524288 | Hair all |
| SLOT_R_BRACELET | 1048576 | Bracelete direito |
| SLOT_L_BRACELET | 2097152 | Bracelete esquerdo |
| SLOT_DECO | 4194304 | Talismã |
| SLOT_WOLF | -100 | Pet wolf |
| SLOT_HATCHLING | -101 | Pet hatchling |
| SLOT_STRIDER | -102 | Pet strider |
| SLOT_BABYPET | -103 | Pet baby |
| SLOT_GREATWOLF | -104 | Pet great wolf |

### Pendências

- [ ] Migrar `droplist` do SQL para XML (27405 registros)
- [ ] Adicionar parser/suporte para `<drops>` no `loadNpcsFromXml()`
- [ ] Remover código morto: `readWeapon()`, `readItem()`, `SQL_ITEM_SELECTS` do `ItemTable.java`
- [ ] Remover `Config.CUSTOM_ITEM_TABLES` se não for mais usado
