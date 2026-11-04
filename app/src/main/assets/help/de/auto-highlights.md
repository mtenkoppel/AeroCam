# So funktionieren Auto-Highlights

## Was ist ein Highlight?

Ein Highlight markiert einen interessanten Moment innerhalb einer Videoaufnahme. So findest du ihn später wieder, ohne die gesamte Aufnahme anzusehen.

AeroCam kann ein Highlight hinzufügen, wenn während deiner Fahrt etwas Bestimmtes passiert. Dieses Ereignis heißt **Auslöser**.

## Verfügbare Auslöser

- **Rundenmarker:** Eine neue Runde beginnt.
- **Leistungsspitze:** Deine Leistung überschreitet die eingestellte Schwelle.
- **Höchstgeschwindigkeit:** Deine Geschwindigkeit überschreitet die eingestellte Schwelle.
- **Gipfelmarker:** Der Karoo meldet das Ende eines Anstiegs.
- **Herzfrequenzspitze:** Deine Herzfrequenz überschreitet die eingestellte Schwelle.
- **Anstiegsmarker:** Löst bei der gewählten Steigung aus, optional mit Mindestgeschwindigkeit.
- **Abfahrtsmarker:** Löst beim gewählten Gefälle aus, optional mit Mindestgeschwindigkeit.
- **Markierungen für Fahrtstart/-ende:** Markiert Fahrtstart und Fahrtende nur, wenn die Kamera bereits aufnimmt. Pausen und Fortsetzen setzen keine Markierungen. Startet niemals eine Aufnahme.

Es werden nur die Auslöser verwendet, die du in den Einstellungen aktivierst.

## Schwellen für Anstiege und Abfahrten

Aktiviere Anstiege, Abfahrten oder beide unabhängig voneinander. Nutze positive Prozentwerte für Anstiege und negative für Abfahrten. Schalte **Zusätzlich Geschwindigkeit prüfen** aus, um nur die Steigung zu verwenden. Ist die Option an, müssen beide Schwellen erreicht sein.

Zum Beispiel markiert **+10%** ohne Geschwindigkeitsbedingung einen steilen Anstieg bei jedem Tempo. Mit **+5%** und **30 km/h** markierst du einen schnellen Anstieg. Für Abfahrten gilt dasselbe mit negativem Gefälle. Kleine Schwankungen erzeugen keine wiederholten Highlights.

## Bei laufender Aufnahme

Wird während einer Aufnahme ein Auslöser erkannt, sendet AeroCam einen Highlight-Befehl an die Kamera. Die Datenfelder Video und Cam Control zeigen kurz an, welcher Auslöser erkannt wurde.

## Aufnahme automatisch starten

Normalerweise fügt Auto-Highlights nur dann Highlights hinzu, wenn bereits eine Aufnahme läuft.

Aktiviere **Aufnahme automatisch starten**, wenn ein Auslöser die Aufnahme bei inaktiver Kamera starten soll.

Der erste Auslöser startet die Aufnahme, wird aber **nicht** als Highlight hinzugefügt. Spätere Auslöser können Highlights in dieser Aufnahme setzen.

So bleiben die Kamerabefehle in einer sicheren Reihenfolge. Aufnahme starten und Highlight hinzufügen werden nicht nahezu gleichzeitig gesendet.

## Schwellen wählen

Wähle Werte für Momente, die du später wiederfinden möchtest. Ist eine Schwelle zu leicht zu erreichen, können zu viele Highlights entstehen.

Beginne mit einem anspruchsvollen Wert und passe ihn nach einigen Fahrten an.
