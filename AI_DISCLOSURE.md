# AI Disclosure — How Golem Logix Is Made

Golem Logix is an independently developed project by NiceClown, with extensive use of generative AI as a production and engineering tool.

AI has played a substantial role in the implementation of the project, particularly in software development. However, the concept, game design, progression, visual direction and creative decisions behind Golem Logix are the author's work.

This document describes both contributions openly.

## 1. Human authorship and creative direction

### Concept and identity

The original concept of Golem Logix — cold, occult, analog logistics — was created by the author.

The project started with a design document describing its identity, atmosphere, mechanics and the idea of a storage network where every block is a functioning machine with a working golem crew.

The author established the project's central creative principles:

* Brass, copper, frost, runes and analog machinery as its visual language.
* Golems as an integral part of the machines rather than decorative elements.
* A world in which logistics has a physical presence, history and atmosphere.
* A progression system combining industrial automation, occult rituals and magic.

### Game design and progression

The author designed the gameplay systems and their relationships, including:

* The plate economy and its different production routes.
* Progression gates, secret recipes and exploration requirements.
* The storage network, channel economy and its expansion.
* Autocrafting, parallel processing and large-scale automation.
* The Telegraph and its interdimensional communication system.
* Integration and balance with Ars Nouveau, Create and other mods.

AI assisted with implementing and refining these systems, but the intended gameplay, design constraints and final decisions were established by the author.

### Art direction

The author is responsible for the visual identity and art direction of the project.

This includes the design of individual machines, their proportions, materials, animations, golem crews and interactions with the environment.

The visual development process is iterative: the author evaluates implementations in game, identifies defects or deviations from the intended design, and requests revisions until the result meets the project's standards.

The Git history documents many such iterations, from machine silhouettes and engraved surfaces to animation details, lighting and rendering corrections.

## 2. Where generative AI was used

### Software development

Most of the Java code was written with AI coding assistants, primarily Claude by Anthropic, working from the author's specifications, design documents, task descriptions and ongoing feedback.

AI assistance covered:

* Implementation of gameplay mechanics and interconnected systems.
* Architecture and integration of new features.
* Bug investigation, debugging and refactoring.
* Performance optimization and rendering.
* Compatibility with Minecraft, NeoForge and other mods.
* Automated in-game testing and maintenance.

The author directed the development, reviewed the implementations and tested changes in game. AI-generated code was not treated as an automatically accepted final result.

The repository's commit history includes explicit Claude co-authorship for a substantial part of the documented development.

### Textures and models

Golem Logix does not use AI image generation for its in-game artwork.

Textures and block models are produced through the project's own code: procedural art tools (`tools/*.java`) and the mod's renderers.

This code was developed with AI assistance under the author's visual direction. Its output was repeatedly reviewed in game and revised to meet the intended appearance.

No generated AI image is directly used as an in-game texture or model.

### Concept references

Generative AI image tools were used to create some private concept references based on the author's descriptions.

These images served as visual references and mood boards during development. They were not included in the mod, project gallery or promotional materials. The final artwork was built separately through the project's own code, using them as references.

### Documentation and text

AI assisted in drafting and editing:

* The in-game guide book.
* Tooltips and descriptions.
* Changelogs and release notes.
* README and project documentation.
* English-language project descriptions.

The author reviews the content for mechanical accuracy and consistency. The Russian translation is also reviewed by the author, a native speaker.

### Automated testing

AI assisted in writing and expanding the automated in-game test suite alongside the implementation.

Tests cover gameplay mechanics, network behaviour, autocrafting, multiblock structures, integrations and edge cases.

The test suite is continuously revised as the project grows, with particular attention to ensuring that tests verify actual behaviour rather than merely repeat the implementation's assumptions.

## 3. What does not use generative AI

* No AI-generated in-game artwork. Textures and models are produced by the project's own code.
* No AI-generated promotional screenshots. Screenshots and footage show the actual game.
* No AI-generated sounds. The mod uses vanilla Minecraft sounds, with pitch adjustments where appropriate.
* No generative AI inside the mod. Golems, machines and logistics systems operate through ordinary Minecraft game logic. The mod does not communicate with external AI services.

## 4. Final responsibility

Golem Logix is a human-directed project built with extensive AI assistance.

The author is responsible for the concept, creative direction, game design, progression, visual standards, testing, integration decisions and final acceptance of the work.

AI is an important part of the production process, particularly in software engineering, but it does not independently determine what Golem Logix is supposed to be.

The distinction is intentional: AI helps build the project; the author decides what the project should become.
