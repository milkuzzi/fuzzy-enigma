import json
from enum import Enum

class Debuff(Enum):
    NONE = "none"
    TIRED = "tired"
    DISTRACTED = "distracted"
    WEAK = "weak"

class HeroCharacter:
    def __init__(self, name="Искатель Знаний"):
        self.name = name
        self.level = 1
        self.current_xp = 0
        self.xp_to_next = 100
        self.current_hp = 100
        self.max_hp = 100
        self.gold = 50
        self.debuff = Debuff.NONE
        self.inventory = []
        
    def add_reward(self, xp_reward, gold_reward):
        self.current_xp += xp_reward
        self.gold += gold_reward
        while self.current_xp >= self.xp_to_next:
            self.level_up()
            
    def level_up(self):
        self.level += 1
        self.current_xp -= self.xp_to_next
        self.xp_to_next = int(self.xp_to_next * 1.5)
        self.max_hp += 20
        self.current_hp = self.max_hp
        self.debuff = Debuff.NONE
        
    def take_damage(self, damage):
        self.current_hp -= damage
        if self.current_hp < 0:
            self.current_hp = 0
        if self.current_hp <= 30:
            self.debuff = Debuff.DISTRACTED
            
    def heal(self, amount):
        self.current_hp = min(self.current_hp + amount, self.max_hp)
        if self.current_hp > 50:
            self.debuff = Debuff.NONE
            
    def buy_item(self, item_name, cost):
        if self.gold >= cost:
            self.gold -= cost
            self.inventory.append(item_name)
            return True
        return False
        
    def to_dict(self):
        return {
            "name": self.name,
            "level": self.level,
            "current_xp": self.current_xp,
            "xp_to_next": self.xp_to_next,
            "current_hp": self.current_hp,
            "max_hp": self.max_hp,
            "gold": self.gold,
            "debuff": self.debuff.value,
            "inventory": self.inventory
        }
    
    @classmethod
    def from_dict(cls, data):
        hero = cls(data["name"])
        hero.level = data["level"]
        hero.current_xp = data["current_xp"]
        hero.xp_to_next = data["xp_to_next"]
        hero.current_hp = data["current_hp"]
        hero.max_hp = data["max_hp"]
        hero.gold = data["gold"]
        
        for debuff in Debuff:
            if debuff.value == data["debuff"]:
                hero.debuff = debuff
                break
                
        hero.inventory = data["inventory"]
        return hero
