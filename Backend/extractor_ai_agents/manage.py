#!/usr/bin/env python
import os, sys

def main():
    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "settings")
    from django.core.management import execute_from_command_line

    # Port par défaut : 65015 (si aucun port n'est précisé sur runserver)
    if len(sys.argv) > 1 and sys.argv[1] == "runserver":
        has_addrport = any(not a.startswith("-") for a in sys.argv[2:])
        if not has_addrport:
            sys.argv.append("65015")

    execute_from_command_line(sys.argv)

if __name__ == "__main__":
    main()
