#!/usr/bin/env python3
"""
Test de bout en bout du Kong Gateway et de ses services backend.

Usage :
    python test_kong.py                          # test de toutes les routes
    python test_kong.py --token <JWT>            # teste aussi un endpoint protégé du service IA

Fonctionne sous Windows, Linux et macOS (aucune dépendance hors 'requests').
"""

import argparse
import sys

try:
    import requests
except ImportError:
    print("Le module 'requests' est requis : pip install requests")
    sys.exit(1)

KONG_PROXY = "http://localhost:8000"
KONG_ADMIN = "http://localhost:8001"
TIMEOUT = 10

# Routes à tester : (nom, chemin via Kong, port backend attendu)
ROUTES = [
    ("userms",     "/userms/",     8085),
    ("authms",     "/authms/",     8090),
    ("extractor",  "/extractor/",  65005),
    ("monitoring", "/monitoring/", 65010),
    ("aiagents",   "/aiagents/",   65015),
]

# Codes couleur ANSI (désactivés si la sortie n'est pas un terminal)
if sys.stdout.isatty():
    GREEN, YELLOW, RED, CYAN, RESET = "\033[92m", "\033[93m", "\033[91m", "\033[96m", "\033[0m"
else:
    GREEN = YELLOW = RED = CYAN = RESET = ""

OK, WARN, KO = f"{GREEN}✅{RESET}", f"{YELLOW}⚠️ {RESET}", f"{RED}❌{RESET}"


def interpret(status: int, name: str) -> tuple:
    """Retourne (icône, diagnostic) selon le code HTTP reçu à travers Kong."""
    if status == 200:
        return OK, "Route Kong OK + backend répond"
    if status in (301, 302, 308):
        return OK, "Route Kong OK + backend répond (redirection)"
    if status == 401:
        return OK, "Route Kong OK + backend répond (auth requise — normal sans token)"
    if status == 403:
        return OK, "Route Kong OK + backend répond (accès refusé — vérifier scopes/token)"
    if status == 404:
        return WARN, "Backend joignable mais chemin inconnu (404 émis par le backend)"
    if status == 429:
        return WARN, "Rate-limiting Kong actif (trop de requêtes)"
    if status == 502:
        return KO, f"Route Kong OK mais backend '{name}' éteint ou injoignable"
    if status == 503:
        return KO, "Backend joignable mais ne répond pas correctement"
    return WARN, f"Code inattendu : {status}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--token", help="JWT AuthMS pour tester un endpoint protégé du service IA")
    args = parser.parse_args()

    exit_code = 0

    # ------------------------------------------------------------------
    print(f"\n{CYAN}═══ 1. Kong est-il démarré ? ═══{RESET}")
    try:
        r = requests.get(f"{KONG_ADMIN}/status", timeout=TIMEOUT)
        r.raise_for_status()
        print(f"{OK} Kong répond sur l'admin API ({KONG_ADMIN})")
    except requests.RequestException as e:
        print(f"{KO} Kong injoignable : {e}")
        print("   → Lancez :  docker compose up -d   puis  docker logs kong")
        sys.exit(1)

    # ------------------------------------------------------------------
    print(f"\n{CYAN}═══ 2. Config déclarative chargée ? ═══{RESET}")
    try:
        services = {s["name"] for s in requests.get(f"{KONG_ADMIN}/services", timeout=TIMEOUT).json()["data"]}
        routes = {rt["name"] for rt in requests.get(f"{KONG_ADMIN}/routes", timeout=TIMEOUT).json()["data"]}
        for name, _, _ in ROUTES:
            s_ok = name in services
            r_ok = f"{name}-route" in routes
            icon = OK if (s_ok and r_ok) else KO
            detail = "service + route déclarés" if (s_ok and r_ok) else f"service:{s_ok} route:{r_ok} → vérifier kong.yml"
            print(f"{icon} {name:<12} {detail}")
            if not (s_ok and r_ok):
                exit_code = 1
    except requests.RequestException as e:
        print(f"{KO} Impossible de lire la config : {e}")
        sys.exit(1)

    # ------------------------------------------------------------------
    print(f"\n{CYAN}═══ 3. Test de chaque route à travers le proxy ═══{RESET}")
    for name, path, port in ROUTES:
        url = f"{KONG_PROXY}{path}"
        try:
            r = requests.get(url, timeout=TIMEOUT, allow_redirects=False)
            icon, diag = interpret(r.status_code, name)
            print(f"{icon} {name:<12} GET {path:<14} → {r.status_code}  {diag}")
            if icon == KO:
                exit_code = 1
                print(f"     → Démarrez le service sur le port {port} (bind 0.0.0.0, pas 127.0.0.1)")
        except requests.RequestException as e:
            exit_code = 1
            print(f"{KO} {name:<12} GET {path:<14} → erreur réseau : {e}")

    # ------------------------------------------------------------------
    if args.token:
        print(f"\n{CYAN}═══ 4. Endpoint protégé du service IA (avec JWT) ═══{RESET}")
        url = f"{KONG_PROXY}/aiagents/api/reports/"
        try:
            r = requests.get(url, timeout=30, headers={"Authorization": f"Bearer {args.token}"})
            if r.status_code == 200:
                print(f"{OK} GET /aiagents/api/reports/ → 200 : circuit complet Kong → Django → OAuth2 validé")
            elif r.status_code == 401:
                exit_code = 1
                print(f"{KO} 401 : token rejeté (expiré ? mauvais issuer ? AuthMS/JWKS injoignable depuis Django ?)")
                print(f"     Détail : {r.text[:200]}")
            elif r.status_code == 403:
                exit_code = 1
                print(f"{KO} 403 : token valide mais scope insuffisant (ex. 'ai:read' manquant)")
            else:
                print(f"{WARN} Code {r.status_code} : {r.text[:200]}")
        except requests.RequestException as e:
            exit_code = 1
            print(f"{KO} Erreur réseau : {e}")
    else:
        print(f"\n{CYAN}ℹ{RESET}  Pour tester aussi l'OAuth2 de bout en bout :  python test_kong.py --token <JWT>")

    # ------------------------------------------------------------------
    print(f"\n{CYAN}═══ Résultat ═══{RESET}")
    if exit_code == 0:
        print(f"{OK} Tous les tests passent : Kong communique correctement avec les services démarrés.\n")
    else:
        print(f"{KO} Certains tests échouent — voir les diagnostics ci-dessus.\n")
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
