# Kisten-Index

Fabric-Mod für Minecraft 26.3. Die Mod merkt sich den Inhalt jeder Kiste, jedes Fasses und jeder Shulkerbox, die du öffnest, und lässt dich alle bekannten Lagerorte per GUI durchsuchen.

## Bedienung

- Taste **K** (in den Steuerungs-Einstellungen änderbar) öffnet den Kisten-Index.
- Suchfeld: Item-Name tippen, die Liste filtert sofort. Jeder Treffer zeigt das Item-Icon.
- Filter: Dimension und Containertyp.
- Klick auf einen Treffer markiert den Container: Ein gelber Rahmen mit Entfernung erscheint auf dem Bildschirm, auch durch Wände. Liegt der Container außerhalb des Bildes, zeigt ein Marker am Bildschirmrand in seine Richtung. Die Koordinaten stehen zusätzlich im Chat.
- Die Markierung verschwindet, wenn du den Container öffnest, oder mit der Taste **J** (änderbar).
- Beim erneuten Öffnen eines Containers wird sein Eintrag aktualisiert.

## Speicherort

Die Daten liegen pro Welt bzw. Server als JSON-Datei im Ordner `config/kistenindex/`.

## Installation

Die fertige JAR steht unter **Releases**. Sie benötigt Fabric Loader und Fabric API für Minecraft 26.3.
