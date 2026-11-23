# Contexte du projet

## Besoin initial

Paul veut surveiller le bruit ambiant de sa chambre pendant la nuit (voisinage, rue, interventions, etc.) et pouvoir analyser a posteriori ce qui s'est passé, quand, et à quel niveau sonore.

L'app de référence utilisée jusqu'ici, **Decibel X**, ne convient pas : impossible de consulter l'historique de la nuit. Le besoin n'est pas seulement de mesurer en direct, mais de **logger** durablement et de **restituer** les données.

## Décisions de cadrage (nov. 2026)

- **Plateforme** : Android, téléphone restant **branché** près du lit toute la nuit.
- **Fréquence d'échantillonnage** : 250 ms par défaut (réglable 100–1000 ms). Une nuit de 8 h ≈ 115 200 points ≈ 300 Ko en CSV : le volume de données n'est pas une contrainte.
- **Visualisation** : courbe du niveau dans le temps + détection de pics/événements.
- **Persistance** : archive multi-nuits en base locale + export CSV par nuit.
- Pas d'enregistrement du contenu audio (privacy) : seuls les niveaux sont stockés.

## Contrainte technique centrale (Android)

Pour mesurer pendant la nuit, il faut un **foreground service** avec `foregroundServiceType="microphone"` (Android 10+), une notification permanente, un wake lock partiel, et idéalement la relance automatique après un reboot. C'est ce point qui fait échouer la plupart des apps grand public.

## Repository

Dépôt `Polo0o/SoundLogger` (branche `main`). Ce dépôt est la source de vérité pour les specs, le contexte et le code.
