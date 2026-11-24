# AGENTS.md — SoundLogger

Consignes pour tout agent (CLI ou autre) travaillant sur ce dépôt.

## Contexte
App Android qui mesure le bruit ambiant pendant toute la nuit (téléphone branché, écran verrouillé) et permet de consulter le lendemain la courbe de la nuit, les événements détectés, un historique multi-nuits et un export CSV. Motivation : Decibel X ne permet pas de consulter l'historique d'une nuit. Sources de vérité : `docs/SPECIFICATIONS.md` (spec v0.1) et `docs/CONTEXTE.md`.

## Invariants (ne jamais casser)
1. **Pas d'enregistrement du contenu audio** : seuls les niveaux (Leq par fenêtre, min/max) sont mesurés et stockés. Jamais de buffer PCM persisté.
2. **Foreground service type `microphone`** : la mesure doit survivre à l'écran verrouillé ; ne pas passer par `MediaRecorder.getMaxAmplitude()` (résolution trop grossière), toujours `AudioRecord` PCM.
3. **Détection d'événements, deux modes** (spec §3 / §4.1) :
   - seuil absolu fixe (ex. 55 dB) ;
   - seuil relatif différé (défaut) : +10 dB au-dessus de la **moyenne des niveaux entre t0+15 min et t0+30 min** (t0 = début de session). Tant que la référence n'est pas calculée, la détection est **suspendue** et signalée dans le rapport.
   - Paramètres communs : dépassement min 500 ms, retour au calme 10 s, fin d'événement sous seuil − 2 dB.
4. **Événement = heure de début, Leq moyen de l'événement, durée, dB max, écart au bruit moyen de la nuit.**
5. **Pas de comparaison superposée de plusieurs nuits** (retiré des specs) : l'historique est un résumé une ligne par nuit avec le nombre d'événements.
6. Tout est **local** (Room) + export CSV `night_YYYY-MM-DD.csv` (timestamp ISO-8601, dB). Pas de cloud, pas de télémétrie.
7. Historique conservé par défaut sans limite (~300 Ko/nuit max en CSV).

## Structure du dépôt
- `docs/` : CONTEXTE.md, SPECIFICATIONS.md — lire en premier.
- `app/src/main/java/com/polo/soundlogger/` :
  - `audio/SoundLevelMeter.kt` : AudioRecord 16 kHz/16 bits/mono, fenêtre 250 ms (réglable 100–1000 ms), RMS → dB + offset de calibration.
  - `detection/EventDetector.kt` : machine à états de détection, 2 modes (invariant 3).
  - `service/RecordingService.kt` : service foreground microphone, notification permanente, wake lock partiel, START_STICKY ; met à jour `RecordingServiceController` (StateFlows) pour l'UI.
  - `data/` : Room (Night, Sample, NightDao, AppDatabase).
  - `export/CsvExporter.kt` : export + intent de partage (FileProvider non encore déclaré).
  - `ui/` : Compose, 4 écrans (Nuit, Rapport, Historique, Réglages) + navigation.
- Stack : Kotlin, Compose, Room (KSP), AGP 8.5.2, minSdk 29, targetSdk 34.

## Conventions
- Kotlin, style officiel, MVVM. Commentaires du squelette : `TODO v0.2` = fonctionnalité à implémenter, `NOTE v0.2` = point d'attention.
- Toute modification de comportement doit d'abord mettre à jour `docs/SPECIFICATIONS.md` si elle change une spec, puis le code.
- Messages de commit : `docs:`, `build:`, `feat:`, `fix:` … en français ou anglais court.
- Après chaque étape : compiler (`./gradlew assembleDebug`) et pousser.

## Tests souhaités
- Nuit simulée en injectant une série de niveaux (pas d'AudioRecord réel) → vérifier détection, timestamps, événements tronqués en fin de session.
- Service tué → relance, discontinuité marquée.
- 8 h de données → vérifier volume (< 5 Mo pour 30 nuits, critère d'acceptation 6).

## Critères d'acceptation (synthèse)
1. Session 8 h sans interruption, aucune donnée manquante non détectée.
2. Verrouiller l'écran n'interrompt pas la mesure.
3. Rapport du matin accessible en < 2 s.
4. Événement testé (ex. claquement de porte) détecté et horodaté à ±1 s.
5. CSV réimportable dans un tableur sans nettoyage.
6. 30 nuits < 5 Mo.
