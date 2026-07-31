#!/bin/bash
# ============================================
# SEO7 AI Agents — Script d'installation
# ============================================

set -e

echo "=========================================="
echo "  SEO7 AI Agents — Installation"
echo "=========================================="

# 1. Python venv
echo ""
echo "[1/5] Création de l'environnement virtuel..."
python3 -m venv venv
source venv/bin/activate

# 2. Dépendances Python
echo "[2/5] Installation des dépendances Python..."
pip install --upgrade pip
pip install -r requirements.txt

# 3. Fichier .env
if [ ! -f .env ]; then
    echo "[3/5] Création du fichier .env..."
    cp .env.example .env
    echo "     ⚠️  IMPORTANT : Éditez .env avec vos paramètres SQL Server"
else
    echo "[3/5] .env existe déjà, skip"
fi

# 4. Vérification Ollama
echo "[4/5] Vérification d'Ollama..."
if command -v ollama &> /dev/null; then
    echo "     ✅ Ollama est installé"
    if ollama list 2>/dev/null | grep -q "qwen2.5"; then
        echo "     ✅ Modèle qwen2.5 disponible"
    else
        echo "     📥 Téléchargement de qwen2.5:7b (~4.7 Go)..."
        ollama pull qwen2.5:7b
    fi
else
    echo "     ❌ Ollama n'est pas installé"
    echo "     Installez-le avec : curl -fsSL https://ollama.com/install.sh | sh"
    echo "     Puis : ollama pull qwen2.5:7b"
fi

# 5. Test
echo "[5/5] Vérification Django..."
python manage.py check --no-color 2>/dev/null && echo "     ✅ Django OK" || echo "     ❌ Erreur Django"

echo ""
echo "=========================================="
echo "  Installation terminée !"
echo "=========================================="
echo ""
echo "  Prochaines étapes :"
echo "  1. Éditez .env avec vos paramètres SQL Server"
echo "  2. Lancez Ollama : ollama serve"
echo "  3. Lancez Django : python manage.py runserver"
echo "  4. Testez : curl http://localhost:65015/"
echo ""
