# ROADMAP — SoundLogger

Étapes par ordre de priorité. Commiter et pousser après chacune.

## Étape 0 — Build initial (fait)
- [x] Cloner le dépôt, ajouter le Gradle wrapper (`gradle wrapper --gradle-version 8.7`).
- [x] `./gradlew assembleDebug` : compilation OK (material-icons-extended ajouté, insertSample suspend déplacé dans une coroutine).

## Étape 1 — Acquisition de confiance (fait côté code ; smoke test à valider sur device)
- [ ] Smoke test sur téléphone réel : procédure écrite dans `docs/TEST_SMOKE.md` (adb, ≥ 30 min écran verrouillé, contrôle des discontinuités) — **à exécuter sur un téléphone réel**.
- [x] Notification permanente + immunité Doze (wake lock partiel + demande d'exemption d'optimisation batterie dans Réglages).
- [x] Calibration : `calibrationOffsetDb` exposé dans les réglages + aide (sonomètre de référence), persisté via `SettingsRepository`.
- [x] Pondération A logicielle (filtres IEC 61672, `AWeightingFilter`) + tests unitaires ; vrais min/max par sous-fenêtres dans `SoundLevelMeter`.

## Étape 2 — Détection d'événements complète (fait)
- [x] `EventDetector` complété : machine à états (seuil, hystérésis seuil-2 dB, durée min 500 ms, retour au calme 10 s), Leq énergétique de l'événement, max, écart au bruit moyen de la nuit rempli à `finish()`, événements tronqués marqués. Suspension avant calcul de la référence en mode relatif.
- [x] Événements persistés (table Room `events`, DB v2) + stats de nuit à l'arrêt du service (`NightStats` : Leq, min/max, L10/L50/L90, heure la plus bruyante).
- [x] Tests unitaires sur nuits simulées (`EventDetectorTest` 5 tests, `NightStatsTest` 2 tests, `AWeightingFilterTest` 3 tests) : `./gradlew testDebugUnitTest` OK.

## Étape 3 — Rapport du matin (fait)
- [x] Courbe Leq de la nuit : tracé Canvas Compose maison (`ui/NightChart.kt`) avec zoom/pan au doigt, couleur par plage (<45 vert, 45–60 orange, >60 rouge), bande min/max, marqueurs d'événements tapables. (Choix indicatif Vico/MPAndroidChart remplacé par un Canvas custom : zéro dépendance, contrôle total des couleurs/marqueurs/zoom.)
- [x] Stats : Leq, min/max, L10/L50/L90, heure la plus bruyante (`NightStats`, persistées à l'arrêt de session).
- [x] Liste des événements tapables → détail + zoom auto sur l'événement dans la courbe.
- [x] Comparaison du Leq de la nuit avec la moyenne des nuits précédentes.

## Étape 4 — Historique et export (fait)
- [x] Résumé multi-nuits (`HistoryScreen`) : date, Leq, nb d'événements, durée, sparkline, suppression en cascade et renommage (colonne `label`, DB v3).
- [x] FileProvider (`res/xml/file_paths.xml` + déclaration manifest) pour le partage CSV `night_YYYY-MM-DD.csv` via intent.
- [x] Agrégation + détection déclenchées à la fin de session (rétro-analyse dans `RecordingService.onDestroy` : événements finaux avec deltas + stats de nuit).

## Étape 5 — Sessions programmées et finitions (fait)
- [x] Auto start/stop programmés (WorkManager, `NightScheduler` + Start/StopNightWorker, replanifiés à chaque cycle et à l'ouverture de l'app), relance après reboot (`BootReceiver` BOOT_COMPLETED).
- [x] Modal de paramétrage des événements sur l'écran principal (`DetectionDialog`, spec §4.1 : seuil absolu slider, ΔdB relatif, durées min/calme) + affichage de la référence mesurée t0+15→t0+30 min pendant l'enregistrement.
- [x] Suppression auto au-delà de N nuits (réglage rétention, 0 = illimité, appliquée à la clôture de session).

## Étape 6 — Release v1
- [ ] Vérifier les 6 critères d'acceptation (docs/SPECIFICATIONS.md §8).
- [ ] APK signé, tag v0.1.0.
