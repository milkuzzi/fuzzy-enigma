import json
import os
from character import HeroCharacter

SAVE_FILE = "character_save.json"

def save_game(hero):
    try:
        with open(SAVE_FILE, 'w', encoding='utf-8') as f:
            json.dump(hero.to_dict(), f, ensure_ascii=False, indent=4)
        return True
    except:
        return False

def load_game():
    if not os.path.exists(SAVE_FILE):
        return HeroCharacter()
    
    try:
        with open(SAVE_FILE, 'r', encoding='utf-8') as f:
            data = json.load(f)
        return HeroCharacter.from_dict(data)
    except:
        return HeroCharacter()
