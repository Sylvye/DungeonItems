# DungeonItems

Paper 1.21.11 RPG item presentation and kit authoring backed by ConfigurableItems.

Requires Java 21 and ConfigurableItems. Build with `gradlew.bat build`.

Use `/di`, `/di create <id>`, `/di give <id> [player] [amount]`, and `/di kit ...`.

`DungeonItemsAPI.kits()` lists authored kit IDs. `kit(id)` returns an optional detached copy of the kit and its item stacks, including inventory, armor, and offhand slots. Consumers can resolve kits before a match and equip their snapshots directly; this does not change `/di kit give` collision and overflow behavior.
