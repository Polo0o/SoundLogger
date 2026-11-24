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

## Étape 3 — Rapport du matin
- [ ] Courbe Leq de la nuit : Vico ou MPAndroidChart, zoom, couleur par plage (<45 vert, 45-60 orange, >60 rouge), marqueurs d'événements.
- [ ] Stats : Leq, min/max, L10/L50/L90, heure la plus bruyante.
- [ ] Liste des événements tapables → détail (courbe zoomée de l'événement).
- [ ] Comparaison du Leq avec la moyenne des nuits précédentes.

## Étape 4 — Historique et export
- [ ] Résumé multi-nuits (date, Leq, nb d'événements, durée, sparkline), suppression/renommage.
- [ ] FileProvider (`file_paths.xml` + déclaration manifest) pour le partage CSV.
- [ ] Déclencher l'agrégation et la détection à la fin de session (rétro-analyse de la nuit complète).

## Étape 5 — Sessions programmées et finitions
- [ ] Auto start/stop programmés (WorkManager), relance après reboot (RECEIVE_BOOT_COMPLETED).
- [ ] Modal de paramétrage des événements sur l'écran principal (spec §4.1 : slider seuil absolu, ΔdB, fenêtre de référence, durées).
- [ ] Suppression auto optionnelle au-delà de N nuits.

## Étape 6 — Release v1
- [ ] Vérifier les 6 critères d'acceptation (docs/SPECIFICATIONS.md §8).
- [ ] APK signé, tag v0.1.0.
