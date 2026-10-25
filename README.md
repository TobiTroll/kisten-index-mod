# Kisten-Index

Fabric-Mod für Minecraft 26.3. Die Mod merkt sich den Inhalt jedes Containers, den du öffnest, und lässt dich alle bekannten Lagerorte per GUI durchsuchen.

## Unterstützte Container

Kiste, Redstone-Kiste, Endertruhe, Fass, Shulkerbox, Spender, Werfer, Ofen, Hochofen, Räucherofen. Liegen Shulkerboxen in einem Container, werden auch ihre Inhalte erfasst.

## Bedienung

- Taste **K** (in den Steuerungs-Einstellungen änderbar) öffnet den Kisten-Index.
- **Suche:** Jedes Wort muss passen, im Item-Namen, in den Verzauberungen oder im Namen der Shulkerbox. Beispiele: `diamant`, `schärfe`, `schwert schärfe`. Mit `<30%` oder `>80%` filterst du nach restlicher Haltbarkeit, z. B. `spitzhacke <30%`.
- **Filter:** Dimension, Containertyp und Shulker-Inhalt (an/aus).
- **Sortierung:** nach Entfernung, Menge oder Alter (zuletzt gescannt). Angepinnte Container stehen immer oben.
- **Tooltip:** Mauszeiger auf einem Treffer zeigt Verzauberungen, Haltbarkeit, Fundort und wann der Container zuletzt gesehen wurde.
- **Linksklick** auf einen Treffer markiert den Container: gelber Rahmen mit Entfernung auf dem Bildschirm, auch durch Wände. Außerhalb des Bildes zeigt ein Marker am Rand die Richtung.
- **Rechtsklick** auf einen Treffer öffnet ein Menü: Markieren, Anpinnen bzw. Pin entfernen, Container vergessen.
- Die Markierung verschwindet, wenn du den Container öffnest, oder mit der Taste **J** (änderbar).
- Beim erneuten Öffnen eines Containers wird sein Eintrag aktualisiert.

## Speicherort

Die Daten liegen pro Welt bzw. Server als JSON-Datei im Ordner `config/kistenindex/`.

## Installation

Die fertige JAR steht unter **Releases**. Sie benötigt Fabric Loader und Fabric API für Minecraft 26.3.
