# So funktioniert Wach halten

## Warum es diese Funktion gibt

Eine vollständig ausgeschaltete DJI Osmo Action kann von AeroCam nicht zuverlässig eingeschaltet werden.

**Wach halten** umgeht diese Einschränkung. AeroCam sendet regelmäßig einen Druck auf die QS-Taste, um den Timer für das automatische Ausschalten zurückzusetzen. Die Kamera bleibt verbunden und bereit, auch wenn ihre Displays ausgeschaltet sind.

> **Akku:** Wach halten verbraucht deutlich mehr Energie als ein echter Standby-Modus.

## Kamera einrichten

Wach halten funktioniert am besten mit diesen Kameraeinstellungen:

1. Stelle **Single Screen Preview** auf **Screen off when locked**. Dadurch können sich die Displays ausschalten, während die Kamera erreichbar bleibt.
2. Stelle **Auto Power Off** auf mindestens **2 Minuten**.
3. Verbinde die Kamera mit dem Karoo und sperre danach den Kamerabildschirm. Das schaltet die Displays aus und spart etwas Energie.
4. Schalte die Kameratöne aus, wenn du nicht bei jedem QS-Befehl ein Geräusch hören möchtest.

Die Namen der Menüs können je nach Firmware leicht abweichen.

## Während einer Fahrt

Aktiviere Wach halten in der Kamerasteuerung. AeroCam sendet sofort einen QS-Befehl und wiederholt ihn im eingestellten Intervall.

Wenn die Verbindung verloren geht, setzt AeroCam Wach halten nach dem erneuten Verbinden fort. Nach dem Verbinden wartet es kurz, damit die Kamera betriebsbereit wird.

Während einer Videoaufnahme pausiert Wach halten. In einer Aufnahme erzeugt derselbe QS-Befehl ein Highlight. Die Pause verhindert unerwünschte Highlights. Nach dem Ende der Aufnahme wird Wach halten fortgesetzt.

## Akkuverbrauch

In einem Test sank der Kamerakku in etwa **1 Stunde und 56 Minuten von 100 % auf 64 %**. Das ist nur ein praktischer Richtwert: Temperatur, Aufnahmen, Displaynutzung und Akkuzustand beeinflussen das Ergebnis.

Verwende Wach halten, wenn ein schneller Kamerazugriff wichtig ist. Schalte es aus, wenn die Kamera nicht mehr bereit bleiben muss.

> **Wichtig:** Wach halten verhindert das Ausschalten. Eine bereits vollständig ausgeschaltete Kamera kann es nicht einschalten.
