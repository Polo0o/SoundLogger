# SoundLogger

**SoundLogger** est une app Android qui mesure le niveau de bruit ambiant pendant toute la nuit, détecte les événements sonores, et permet de consulter le lendemain un historique complet, exportable.

Motivation : les apps grand public (ex. Decibel X) ne permettent pas de consulter proprement l'historique d'une nuit complète. SoundLogger répond à ce besoin : courbe de la nuit, événements détectés, historique multi-nuits, export CSV.

## Documentation

- [docs/CONTEXTE.md](docs/CONTEXTE.md) — contexte et besoin
- [docs/SPECIFICATIONS.md](docs/SPECIFICATIONS.md) — spécifications fonctionnelles et techniques (v0.1)

## Fonctionnalités clés (v1)

- Mesure du niveau sonore (dB, pondération A) toutes les 250 ms (réglable 100–1000 ms)
- Enregistrement toute la nuit via foreground service de type `microphone` (écran verrouillé OK)
- Détection d'événements : seuil absolu, ou seuil relatif (+10 dB au-dessus de la moyenne t0+15 → t0+30 min)
- Rapport du matin : courbe zoomable, statistiques, liste des événements
- Historique multi-nuits avec résumé comparatif
- Export CSV par nuit

## Stack

Kotlin, Jetpack Compose, MVVM, Room, AudioRecord (16 kHz / 16 bits / mono), WorkManager.

## Build

- Debug : `./gradlew assembleDebug` (APK non signé de dev dans `app/build/outputs/apk/debug/`).
- Tests unitaires : `./gradlew testDebugUnitTest`.
- Release signé : `./gradlew assembleRelease` avec un keystore local **non commité**, fourni
  par variables d'environnement :
  ```sh
  export SOUNDLOGGER_KEYSTORE=/chemin/soundlogger-release.jks \
         SOUNDLOGGER_KEYSTORE_PASSWORD=... SOUNDLOGGER_KEY_ALIAS=... SOUNDLOGGER_KEY_PASSWORD=...
  ./gradlew assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
  ```

## Smoke test

Procédure complète (adb, téléphone réel ou émulateur) et résultats constatés :
[docs/TEST_SMOKE.md](docs/TEST_SMOKE.md).

## Statut

v0.1.0 — taguée. Voir [ROADMAP.md](ROADMAP.md) et les critères d'acceptation dans
[docs/SPECIFICATIONS.md](docs/SPECIFICATIONS.md) §8.
