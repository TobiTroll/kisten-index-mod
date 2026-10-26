# Kisten-Index / Chest Index

Fabric-Mod für Minecraft 26.3. Die Mod merkt sich den Inhalt jedes Containers, den du öffnest, und lässt dich alle bekannten Lagerorte in einer modernen, dunklen GUI durchsuchen.

## Unterstützte Container

Kiste, Doppelkiste, Redstone-Kiste, Endertruhe, Fass, Shulkerbox, Spender, Werfer, Ofen, Hochofen, Räucherofen. Liegen Shulkerboxen in einem Container, werden auch ihre Inhalte erfasst. Beide Hälften einer Doppelkiste zählen als ein Container.

## Bedienung

- Taste **K** (in den Steuerungs-Einstellungen änderbar) öffnet den Kisten-Index.
- **Umschalter oben:** "Items" (Standard) zeigt einzelne Items, "Container" zeigt jeden Container genau einmal. Der Wechsel schließt die GUI nicht.
- **Suche:** Jedes Wort muss passen, im Item-Namen, in den Verzauberungen oder im Namen der Shulkerbox. In der Container-Ansicht zählen auch der Container-Name, der Typ und die Inhalte. Mit `<30%` oder `>80%` filterst du nach restlicher Haltbarkeit.
- **Dropdowns:** Dimension, Containertyp und Sortierung. Items: Entfernung, Menge, Alter, Name A-Z. Container: Entfernung, Alter, Name A-Z, Item-Arten, Gesamtmenge. Angepinnte Container stehen immer oben.
- **Zahnrad oben rechts (Einstellungen):** Shulker-Inhalt an/aus, **Sprache** (Deutsch / English), Filter zurücksetzen. Die Sprache gilt für die ganze Mod (GUI, Typ- und Dimensionsnamen, Chat-Meldungen) und bleibt nach einem Neustart gespeichert. Die Namen der Tastenbelegungen folgen der Sprache von Minecraft.
- **Item-Ansicht:** Linksklick markiert den Container, Rechtsklick öffnet Markieren, Anpinnen, Container vergessen.
- **Container-Ansicht:** Jede Zeile zeigt Icon, Name, Typ, Dimension, eine Inhaltsübersicht und die Koordinaten. Einen Namen vergibst du per Rechtsklick auf "Umbenennen". Ohne eigenen Namen heißt ein Container z. B. "Kiste #12". Der Name gilt nur in der Mod und bleibt dauerhaft gespeichert.
- **Detailansicht:** Linksklick auf einen Container zeigt alle gespeicherten Items mit Icon, Name und Menge. Oben stehen Name, Typ, Dimension und Koordinaten mit den Aktionen "Hervorheben", "Koordinaten kopieren" und "Umbenennen". "Zurück" (oder ESC) führt zur Containerliste, Suche, Filter, Sortierung und Seite bleiben erhalten.
- Die Markierung verschwindet, wenn du den Container öffnest, oder mit der Taste **J** (änderbar).
- Beim erneuten Öffnen eines Containers wird sein Eintrag aktualisiert.

## Speicherort

Die Daten liegen pro Welt bzw. Server als JSON-Datei im Ordner `config/kistenindex/`, die Sprachauswahl in `config/kistenindex/language.txt`. Ein Container wird eindeutig durch Dimension und Blockkoordinaten bestimmt; sein eigener Name hängt daran.

## Installation

Die fertige JAR steht unter **Releases**. Sie benötigt Fabric Loader und Fabric API für Minecraft 26.3.

---

## English

Fabric mod for Minecraft 26.3. Chest Index remembers the contents of every container you open and lets you search all known storage locations in a modern dark GUI.

- Press **K** to open the index. The toggle at the top switches between "Items" (default) and "Containers".
- Click the **gear icon** (settings) to switch the mod language between German and English, toggle shulker contents or reset the filters. The choice is saved in `config/kistenindex/language.txt`.
- Left click a result to highlight the container in the world, right click for more options (pin, rename, forget). In the container view you can rename containers and open a detail view with all stored items.
- Download the latest JAR under **Releases**. It requires Fabric Loader and Fabric API for Minecraft 26.3.
