# How Golem Logix is made

Golem Logix is made by one author, **NiceClown**, with heavy use of generative AI as a production tool. This page says
plainly what AI made and what the author did.

## What AI made

| Part | How it was made |
|---|---|
| **Code** | Most of the Java code was written by an AI coding assistant (mainly Claude by Anthropic) from the author's written specifications. The author tests every change in game. The commit history shows this (`Co-Authored-By` lines). |
| **Textures and models** | All block, item and entity textures are drawn by the project's own procedural generators (`tools/*.java`), and these generators were written with AI. No image-generation model made any texture. The author reviewed every texture in game and sent it back until it was right. |
| **Concept references** | Most concept images that guided the look of the machines were made with Google Gemini from the author's descriptions. They were private references only: never published, and no texture is traced or converted from them. |
| **Texts** | The guide book, tooltips, changelog, README and project pages were drafted with AI from the author's descriptions. The Russian translation was proofread by the author, a native speaker. |
| **Tests** | The automated in-game tests were written with AI alongside the code. |
| **Promo video** | The footage is real in-game recording. The editing, captions and synthesised music were made by scripts written with AI. |

## What is not AI

- **No AI images** in the mod, on the project pages or in the gallery. The project icon is the author's in-game screenshot.
- **No AI sounds**: every sound is a vanilla Minecraft sound, re-pitched.
- **No generative AI inside the mod**: golems run on ordinary game logic, and the mod calls no AI service.

## The author's part

- **Concept and design document.** The idea of "cold, occult, analog logistics", where every block is a working
  machine with a golem crew, and the design document the project started from.
- **Game design.** Every mechanic and how they fit together. For example:
  - the plate economy and its routes (anvil ritual, hand forging, the Saturator);
  - progression gates such as the End and the two-deed secret recipe;
  - the network's growth from 8 to 16 to 64 channels a bushing;
  - autocrafting at scale: the Mainframe, the Catalog, the Concentrator;
  - the Telegraph and its cards;
  - auto-output;
  - the balance against Ars Nouveau and Create.
- **Art direction.** What each machine looks like and how its crew moves, decided and judged by the author, scene by
  scene; crude versions were rejected until they fit.
- **Testing and direction.** All playtesting in singleplayer and on a server, bug finding, versions, ports and
  integrations.

The full list of features is in the [README](README.md) and the [CHANGELOG](CHANGELOG.md).
