# So funktionieren Auto-Highlights

## Was ist ein Highlight?

Ein Highlight markiert einen interessanten Moment innerhalb einer Videoaufnahme. So findest du ihn später wieder, ohne die gesamte Aufnahme anzusehen.

AeroCam kann ein Highlight hinzufügen, wenn während deiner Fahrt etwas Bestimmtes passiert. Dieses Ereignis heißt **Auslöser**.

## Markierungen für Fahrtstart/-ende

Bei neuen Installationen ist diese Option aus. Updates erhalten vorhandene Einstellungen und den bisherigen Standard älterer Installationen. Aktiviere **Auto-Highlights** und **Markierungen für Fahrtstart/-ende**, um sie zu nutzen.

- Eine Startmarkierung wird nur gesetzt, wenn die Kamera beim Fahrtstart bereits aufnimmt. Eine später gestartete Aufnahme erhält keine nachträgliche Fahrtstartmarkierung.
- Eine Endmarkierung wird beim Beenden einer aktiven oder pausierten Fahrt gesetzt, sofern die Kamera noch aufnimmt. Der Karoo meldet für Speichern und Verwerfen denselben Endzustand; beides kann daher eine Endmarkierung erzeugen.
- Pausieren und Fortsetzen setzen keine Markierungen. Verpasste Markierungen werden nicht nachgeholt, auch nicht nach einer erneuten Verbindung.
- Diese Markierungen starten niemals eine Aufnahme, auch bei aktivem automatischem Aufnahmestart. Die Wartezeit für automatische Schwellen-Highlights gilt für sie nicht.


## Verfügbare Auslöser

- **Rundenmarker:** Eine neue Runde beginnt.
- **Leistungsspitze:** Deine Leistung überschreitet die eingestellte Schwelle.
- **Höchstgeschwindigkeit:** Deine Geschwindigkeit überschreitet die eingestellte Schwelle.
- **Gipfelmarker:** Der Karoo meldet das Ende eines Anstiegs.
- **Herzfrequenzspitze:** Deine Herzfrequenz überschreitet die eingestellte Schwelle.
- **Anstiegsmarker:** Sowohl die eingestellte Geschwindigkeit bergauf als auch die positive Steigung werden erreicht.
- **Abfahrtsmarker:** Sowohl die eingestellte Geschwindigkeit bergab als auch das negative Gefälle werden erreicht.
- **Markierungen für Fahrtstart/-ende:** Markiert Fahrtstart und Fahrtende nur, wenn die Kamera bereits aufnimmt. Pausen und Fortsetzen setzen keine Markierungen. Startet niemals eine Aufnahme.

Es werden nur die Auslöser verwendet, die du in den Einstellungen aktivierst.

## Schwellen für Anstiege und Abfahrten

Aktiviere Anstiege, Abfahrten oder beide unabhängig voneinander. Jede Richtung hat eigene Schwellen für Geschwindigkeit und Steigung beziehungsweise Gefälle. **Beide Schwellen müssen erreicht sein**.

Nutze eine **positive Prozentzahl für Anstiege** und eine **negative für Abfahrten**. Bergauf bedeuten beispielsweise **20 km/h** und **+5%** mindestens 20 km/h auf einem Anstieg mit mindestens 5% Steigung. Bergab bedeuten **60 km/h** und **−6%** mindestens 60 km/h bei einem Gefälle von mindestens 6%.

Die bisherigen Abfahrtswerte bleiben **50 km/h** und **−3%**. Der Auslöser bleibt ausgeschaltet, solange du ihn nicht aktivierst. Auch der neue Anstiegsauslöser ist zunächst aus; seine Ausgangswerte sind **20 km/h** und **+3%**. Kleine Schwankungen um eine Schwelle erzeugen keine wiederholten Highlights.

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
