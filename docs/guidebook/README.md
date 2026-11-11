# The guide book

The in-game guide book (the *Occult Grimoire of Logistics*) is written here in Markdown, one file per language:

- [en_us.md](en_us.md) — English
- [ru_ru.md](ru_ru.md) — Russian

The build turns every `<lang>.md` in this folder into `assets/golem_logix/book/<lang>.json`, the file the game reads
(`gradle/guidebook.gradle`, run before `processResources`). Nothing else needs to change: edit the Markdown, build, and the book
in the game follows. A language with no file of its own shows the English book.

## Translating

Copy `en_us.md` to the code of your language (`de_de.md`, `fr_fr.md`, `zh_cn.md`...) and translate the text. Keep:

- the headings' levels (`#`, `##`, `###`, `####`, `#####`) and their order;
- every `<!-- {...} -->` line as it is: these are the page's fields (ids, icons, recipes, positions on the map). Only text inside
  them that a player reads (a task's `"text"`) is translated;
- the item names as the game's own translation has them, so the book and the tooltips agree.

## The format

```
# Book title

## Category title
<!-- {"id": "basics", "icon": "golem_logix:guide_book", "color": "38d9d6"} -->
> the category's short subtitle

### Entry title
<!-- {"id": "quickstart", "icon": "golem_logix:analog_terminal", "x": 2, "y": 0, "parents": ["source"]} -->

#### Page title
<!-- {"type": "crafting", "recipe": "golem_logix:forge_hammer"} -->

A paragraph: one line, one paragraph of the page.

- a bullet point

##### A small heading inside a page

**bold** is the book's emphasis (copper ink), *italic* its asides.
```

- `##` a category (a tab of the book), `###` an entry (a node on its map), `####` a page of the entry. A page with no title is
  only its `<!-- {...} -->` line.
- A page's fields: `"type"` (`text` when left out, `crafting`, `ritual`, `table`, `image`, `blueprint`, `checklist`, `tasks`) and
  what the type needs - a `"recipe"`, a ritual's `"station"`, `"inputs"` and `"outputs"`, an image's `"texture"`...
- A ritual's condition line is a quote under its title: `> 1000 Source per ritual.`
- A table page is a Markdown table; its column widths (in book pixels) are the `"widths"` field.
- Colours other than the copper emphasis stay in Minecraft's codes: `&2green&r`, `&6amber&r`, `&4red&r`.
- Each paragraph is one line; a blank line between paragraphs is only for reading.
