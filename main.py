import tkinter as tk
from tkinter import ttk, messagebox
import time
import threading
import math
from character import HeroCharacter, Debuff
from pomodoro import PomodoroTimer
import save_manager

class StudyDungeonApp:
    def __init__(self, root):
        self.root = root
        self.root.title("⚔️ Study Dungeon ⚔️")
        self.root.geometry("600x1500")  # Увеличенное окно
        self.root.resizable(False, False)
        self.root.configure(bg="#1a1a2e")
        
        # Загружаем персонажа
        self.hero = save_manager.load_game()
        
        # Переменные для настроек
        self.pomodoro_count = 1
        self.current_pomodoro = 0
        
        # Переменные для времени (в минутах)
        self.work_time = 25
        self.break_time = 5
        
        # Переменные для сворачивания
        self.settings_visible = True
        self.shop_visible = True
        self.inventory_visible = True
        
        # Создаем таймер
        self.timer = PomodoroTimer(self.work_time, self.break_time)
        self.timer.set_callbacks(
            on_update=self.update_timer_display,
            on_complete=self.on_pomodoro_success,
            on_fail=self.on_pomodoro_fail
        )
        
        # Защита от закрытия
        self.root.protocol("WM_DELETE_WINDOW", self.on_closing)
        
        # Аварийный выход
        self.root.bind('<Control-Shift-E>', self.emergency_exit)
        self.root.bind('<Control-Shift-Q>', self.emergency_exit)
        
        # Цвета
        self.bg_color = "#1a1a2e"
        self.accent_color = "#4a47a3"
        self.success_color = "#4caf50"
        self.danger_color = "#f44336"
        self.warning_color = "#ff9800"
        
        # Переменные для анимации
        self.animation_running = False
        self.animation_label = None
        
        # Переменные для блокировки
        self.is_locked = False
        self.trap_window = None
        
        # Создаем интерфейс
        self.create_widgets()
        self.update_character_display()
        
    def create_widgets(self):
        # Главный контейнер с прокруткой
        main_canvas = tk.Canvas(self.root, bg=self.bg_color, highlightthickness=0)
        main_canvas.pack(side="left", fill="both", expand=True)
        
        scrollbar = tk.Scrollbar(self.root, orient="vertical", command=main_canvas.yview)
        scrollbar.pack(side="right", fill="y")
        
        main_canvas.configure(yscrollcommand=scrollbar.set)
        main_canvas.bind('<Configure>', lambda e: main_canvas.configure(scrollregion=main_canvas.bbox("all")))
        
        # Контейнер для содержимого
        content_frame = tk.Frame(main_canvas, bg=self.bg_color)
        main_canvas.create_window((0, 0), window=content_frame, anchor="nw", width=580)
        
        # Привязка колесика мыши
        def _on_mousewheel(event):
            main_canvas.yview_scroll(int(-1*(event.delta/120)), "units")
        
        main_canvas.bind_all("<MouseWheel>", _on_mousewheel)
        
        # ===== ВЕРХНЯЯ ПАНЕЛЬ =====
        top_frame = tk.Frame(content_frame, bg=self.bg_color)
        top_frame.pack(pady=10)
        
        self.name_label = tk.Label(top_frame, text=f"⚔️ {self.hero.name} ⚔️",
                                   font=("Arial", 24, "bold"),
                                   bg=self.bg_color, fg="white")
        self.name_label.pack()
        
        self.status_label = tk.Label(top_frame, text="😴 В режиме ожидания",
                                     font=("Arial", 14),
                                     bg=self.bg_color, fg=self.warning_color)
        self.status_label.pack()
        
        # ===== ХАРАКТЕРИСТИКИ (всегда видны) =====
        stats_frame = tk.Frame(content_frame, bg=self.accent_color, relief="ridge", bd=3)
        stats_frame.pack(pady=10, padx=20, fill="x")
        
        # Уровень
        level_frame = tk.Frame(stats_frame, bg=self.accent_color)
        level_frame.pack(pady=5, padx=10, fill="x")
        
        tk.Label(level_frame, text="УРОВЕНЬ", font=("Arial", 12),
                bg=self.accent_color, fg="white").pack(anchor="w")
        
        self.level_value = tk.Label(level_frame, text="1", font=("Arial", 18, "bold"),
                                   bg=self.accent_color, fg=self.warning_color)
        self.level_value.pack(anchor="w")
        
        # XP
        xp_frame = tk.Frame(stats_frame, bg=self.accent_color)
        xp_frame.pack(pady=5, padx=10, fill="x")
        
        self.xp_label = tk.Label(xp_frame, text="XP: 0/100", font=("Arial", 12),
                                bg=self.accent_color, fg="white")
        self.xp_label.pack(anchor="w")
        
        self.xp_bar = ttk.Progressbar(xp_frame, length=500, mode='determinate')
        self.xp_bar.pack(fill="x")
        
        # HP
        hp_frame = tk.Frame(stats_frame, bg=self.accent_color)
        hp_frame.pack(pady=5, padx=10, fill="x")
        
        self.hp_label = tk.Label(hp_frame, text="❤️ HP: 100/100", font=("Arial", 12),
                                bg=self.accent_color, fg="white")
        self.hp_label.pack(anchor="w")
        
        self.hp_bar = ttk.Progressbar(hp_frame, length=500, mode='determinate')
        self.hp_bar.pack(fill="x")
        
        # Золото
        gold_frame = tk.Frame(stats_frame, bg=self.accent_color)
        gold_frame.pack(pady=5, padx=10, fill="x")
        
        self.gold_label = tk.Label(gold_frame, text="💰 Золото: 50", font=("Arial", 14),
                                  bg=self.accent_color, fg="#ffff00")
        self.gold_label.pack(anchor="w")
        
        # Дебафф
        self.debuff_label = tk.Label(stats_frame, text="", font=("Arial", 12, "bold"),
                                     bg=self.danger_color, fg="white")
        
        # ===== ТАЙМЕР (всегда виден) =====
        timer_frame = tk.Frame(content_frame, bg=self.bg_color, relief="ridge", bd=2)
        timer_frame.pack(pady=20, padx=20, fill="x")
        
        self.phase_label = tk.Label(timer_frame, text="📚 УЧИСЬ!",
                                    font=("Arial", 20, "bold"),
                                    bg=self.bg_color, fg=self.success_color)
        self.phase_label.pack(pady=5)
        
        self.time_label = tk.Label(timer_frame, text="25:00",
                                   font=("Arial", 60, "bold"),
                                   bg=self.bg_color, fg="white")
        self.time_label.pack(pady=10)
        
        self.pomodoro_counter_label = tk.Label(timer_frame,
                                               text="Помидорка 0/0",
                                               font=("Arial", 12),
                                               bg=self.bg_color, fg="gray")
        self.pomodoro_counter_label.pack()
        
        # Кнопки управления
        button_frame = tk.Frame(timer_frame, bg=self.bg_color)
        button_frame.pack(pady=20)
        
        self.start_button = tk.Button(button_frame, 
                                      text="▶️ СТАРТ",
                                      command=self.start_timer,
                                      bg=self.success_color, 
                                      fg="white",
                                      font=("Arial", 18, "bold"),
                                      width=10,
                                      height=2,
                                      relief="raised",
                                      bd=3)
        self.start_button.pack(side="left", padx=10)
        
        self.pause_button = tk.Button(button_frame, 
                                      text="⏸️ ПАУЗА",
                                      command=self.pause_timer,
                                      bg=self.warning_color, 
                                      fg="white",
                                      font=("Arial", 14),
                                      width=8,
                                      height=1,
                                      state="disabled")
        self.pause_button.pack(side="left", padx=5)
        
        self.fail_button = tk.Button(button_frame, 
                                     text="✖️ СДАТЬСЯ",
                                     command=self.fail_timer,
                                     bg=self.danger_color, 
                                     fg="white",
                                     font=("Arial", 14),
                                     width=8,
                                     height=1)
        self.fail_button.pack(side="left", padx=5)
        
        # ===== НАСТРОЙКИ (сворачиваемые) =====
        self.settings_frame = tk.Frame(content_frame, bg=self.accent_color, relief="ridge", bd=3)
        self.settings_frame.pack(pady=10, padx=20, fill="x")
        
        # Заголовок с кнопкой сворачивания
        settings_header = tk.Frame(self.settings_frame, bg=self.accent_color)
        settings_header.pack(fill="x", padx=10, pady=5)
        
        tk.Label(settings_header, text="⚙️ НАСТРОЙКИ", font=("Arial", 16, "bold"),
                bg=self.accent_color, fg="white").pack(side="left")
        
        self.settings_toggle_btn = tk.Button(settings_header, text="▼", 
                                            command=self.toggle_settings,
                                            bg=self.accent_color, fg="white",
                                            font=("Arial", 12, "bold"),
                                            bd=0, width=3)
        self.settings_toggle_btn.pack(side="right")
        
        # Контейнер для содержимого настроек
        self.settings_content = tk.Frame(self.settings_frame, bg=self.accent_color)
        self.settings_content.pack(fill="x", padx=10, pady=5)
        
        # Время работы
        work_frame = tk.Frame(self.settings_content, bg=self.accent_color)
        work_frame.pack(pady=5, fill="x")
        
        tk.Label(work_frame, text="📚 Время работы:", font=("Arial", 12),
                bg=self.accent_color, fg="white").pack(anchor="w")
        
        work_slider_frame = tk.Frame(work_frame, bg=self.accent_color)
        work_slider_frame.pack(fill="x")
        
        self.work_time_var = tk.IntVar(value=25)
        work_slider = tk.Scale(work_slider_frame, from_=1, to=60, orient="horizontal",
                              variable=self.work_time_var, length=450,
                              bg=self.accent_color, fg="white",
                              troughcolor=self.bg_color,
                              command=self.update_work_time_preview)
        work_slider.pack(side="left", padx=5)
        
        self.work_time_label = tk.Label(work_slider_frame, text="25 мин",
                                       font=("Arial", 12, "bold"),
                                       bg=self.accent_color, fg=self.success_color)
        self.work_time_label.pack(side="left", padx=5)
        
        # Время отдыха
        break_frame = tk.Frame(self.settings_content, bg=self.accent_color)
        break_frame.pack(pady=5, fill="x")
        
        tk.Label(break_frame, text="☕ Время отдыха:", font=("Arial", 12),
                bg=self.accent_color, fg="white").pack(anchor="w")
        
        break_slider_frame = tk.Frame(break_frame, bg=self.accent_color)
        break_slider_frame.pack(fill="x")
        
        self.break_time_var = tk.IntVar(value=5)
        break_slider = tk.Scale(break_slider_frame, from_=1, to=30, orient="horizontal",
                               variable=self.break_time_var, length=450,
                               bg=self.accent_color, fg="white",
                               troughcolor=self.bg_color,
                               command=self.update_break_time_preview)
        break_slider.pack(side="left", padx=5)
        
        self.break_time_label = tk.Label(break_slider_frame, text="5 мин",
                                        font=("Arial", 12, "bold"),
                                        bg=self.accent_color, fg=self.warning_color)
        self.break_time_label.pack(side="left", padx=5)
        
        # Кнопка применения
        apply_button_frame = tk.Frame(self.settings_content, bg=self.accent_color)
        apply_button_frame.pack(pady=10)
        
        self.apply_settings_button = tk.Button(apply_button_frame, 
                                              text="✅ ПРИМЕНИТЬ НАСТРОЙКИ",
                                              command=self.apply_settings,
                                              bg="#00a8ff", fg="white",
                                              font=("Arial", 14, "bold"),
                                              padx=30, pady=10)
        self.apply_settings_button.pack()
        
        # Количество помидорок
        count_frame = tk.Frame(self.settings_content, bg=self.accent_color)
        count_frame.pack(pady=10, fill="x")
        
        count_label_frame = tk.Frame(count_frame, bg=self.accent_color)
        count_label_frame.pack(fill="x")
        
        tk.Label(count_label_frame, text="🎯 Помидорок за раз:", font=("Arial", 12),
                bg=self.accent_color, fg="white").pack(side="left")
        
        tk.Label(count_label_frame, text="(от 1 до 10)", font=("Arial", 10),
                bg=self.accent_color, fg="gray").pack(side="left", padx=5)
        
        count_spin_frame = tk.Frame(count_frame, bg=self.accent_color)
        count_spin_frame.pack(fill="x", pady=5)
        
        self.count_var = tk.StringVar(value="1")
        count_spinbox = tk.Spinbox(count_spin_frame, from_=1, to=10, width=5,
                                   textvariable=self.count_var,
                                   font=("Arial", 14),
                                   command=self.update_pomodoro_count)
        count_spinbox.pack(side="left", padx=5)
        
        self.progress_label = tk.Label(count_spin_frame, 
                                       text="Прогресс: 0/0",
                                       font=("Arial", 12),
                                       bg=self.accent_color, fg="white")
        self.progress_label.pack(side="left", padx=20)
        
        self.progress_bar = ttk.Progressbar(self.settings_content, length=500, mode='determinate')
        self.progress_bar.pack(pady=5)
        
        # ===== МАГАЗИН (сворачиваемый) =====
        self.shop_frame = tk.Frame(content_frame, bg=self.accent_color, relief="ridge", bd=3)
        self.shop_frame.pack(pady=10, padx=20, fill="x")
        
        shop_header = tk.Frame(self.shop_frame, bg=self.accent_color)
        shop_header.pack(fill="x", padx=10, pady=5)
        
        tk.Label(shop_header, text="🏪 МАГАЗИН", font=("Arial", 16, "bold"),
                bg=self.accent_color, fg="white").pack(side="left")
        
        self.shop_toggle_btn = tk.Button(shop_header, text="▼", 
                                        command=self.toggle_shop,
                                        bg=self.accent_color, fg="white",
                                        font=("Arial", 12, "bold"),
                                        bd=0, width=3)
        self.shop_toggle_btn.pack(side="right")
        
        self.shop_content = tk.Frame(self.shop_frame, bg=self.accent_color)
        self.shop_content.pack(fill="x", padx=10, pady=5)
        
        # Зелье
        item1_frame = tk.Frame(self.shop_content, bg=self.accent_color)
        item1_frame.pack(fill="x", pady=5)
        
        tk.Label(item1_frame, text="🧪 Малое зелье (восст. 30 HP)", font=("Arial", 12),
                bg=self.accent_color, fg="white").pack(side="left")
        tk.Label(item1_frame, text="20💰", font=("Arial", 12, "bold"),
                bg=self.accent_color, fg="#ffff00").pack(side="left", padx=10)
        tk.Button(item1_frame, text="Купить", command=lambda: self.buy_item("potion", 20),
                 bg=self.success_color, fg="white",
                 font=("Arial", 12), width=8).pack(side="right")
        
        # Свиток
        item2_frame = tk.Frame(self.shop_content, bg=self.accent_color)
        item2_frame.pack(fill="x", pady=5)
        
        tk.Label(item2_frame, text="📜 Свиток мудрости (+50 XP)", font=("Arial", 12),
                bg=self.accent_color, fg="white").pack(side="left")
        tk.Label(item2_frame, text="50💰", font=("Arial", 12, "bold"),
                bg=self.accent_color, fg="#ffff00").pack(side="left", padx=10)
        tk.Button(item2_frame, text="Купить", command=lambda: self.buy_item("scroll", 50),
                 bg=self.success_color, fg="white",
                 font=("Arial", 12), width=8).pack(side="right")
        
        # ===== ИНВЕНТАРЬ (сворачиваемый) =====
        self.inv_frame = tk.Frame(content_frame, bg=self.bg_color, relief="ridge", bd=2)
        self.inv_frame.pack(pady=10, padx=20, fill="x")
        
        inv_header = tk.Frame(self.inv_frame, bg=self.bg_color)
        inv_header.pack(fill="x", padx=10, pady=5)
        
        tk.Label(inv_header, text="🎒 ИНВЕНТАРЬ", font=("Arial", 16, "bold"),
                bg=self.bg_color, fg="white").pack(side="left")
        
        self.inv_toggle_btn = tk.Button(inv_header, text="▼", 
                                       command=self.toggle_inventory,
                                       bg=self.bg_color, fg="white",
                                       font=("Arial", 12, "bold"),
                                       bd=0, width=3)
        self.inv_toggle_btn.pack(side="right")
        
        self.inv_content = tk.Frame(self.inv_frame, bg=self.bg_color)
        self.inv_content.pack(fill="x", padx=10, pady=5)
        
        self.inventory_listbox = tk.Listbox(self.inv_content, height=4, 
                                           bg="#2a2a3a", fg="white",
                                           font=("Arial", 12))
        self.inventory_listbox.pack(fill="x")
        
    # ========== МЕТОДЫ ДЛЯ СВОРАЧИВАНИЯ ==========
    
    def toggle_settings(self):
        """Сворачивает/разворачивает настройки"""
        if self.settings_visible:
            self.settings_content.pack_forget()
            self.settings_toggle_btn.config(text="▶")
            self.settings_visible = False
        else:
            self.settings_content.pack(fill="x", padx=10, pady=5)
            self.settings_toggle_btn.config(text="▼")
            self.settings_visible = True
            
    def toggle_shop(self):
        """Сворачивает/разворачивает магазин"""
        if self.shop_visible:
            self.shop_content.pack_forget()
            self.shop_toggle_btn.config(text="▶")
            self.shop_visible = False
        else:
            self.shop_content.pack(fill="x", padx=10, pady=5)
            self.shop_toggle_btn.config(text="▼")
            self.shop_visible = True
            
    def toggle_inventory(self):
        """Сворачивает/разворачивает инвентарь"""
        if self.inventory_visible:
            self.inv_content.pack_forget()
            self.inv_toggle_btn.config(text="▶")
            self.inventory_visible = False
        else:
            self.inv_content.pack(fill="x", padx=10, pady=5)
            self.inv_toggle_btn.config(text="▼")
            self.inventory_visible = True
    
    def hide_all_settings(self):
        """Прячет все настройки во время учебы"""
        # Сохраняем состояние, чтобы потом восстановить
        self.settings_was_visible = self.settings_visible
        self.shop_was_visible = self.shop_visible
        self.inv_was_visible = self.inventory_visible
        
        # Прячем всё
        if self.settings_visible:
            self.settings_content.pack_forget()
            self.settings_toggle_btn.config(text="▶")
            self.settings_visible = False
            
        if self.shop_visible:
            self.shop_content.pack_forget()
            self.shop_toggle_btn.config(text="▶")
            self.shop_visible = False
            
        if self.inventory_visible:
            self.inv_content.pack_forget()
            self.inv_toggle_btn.config(text="▶")
            self.inventory_visible = False
            
    def restore_settings(self):
        """Восстанавливает настройки после учебы"""
        # Восстанавливаем как было
        if hasattr(self, 'settings_was_visible') and self.settings_was_visible:
            self.settings_content.pack(fill="x", padx=10, pady=5)
            self.settings_toggle_btn.config(text="▼")
            self.settings_visible = True
            
        if hasattr(self, 'shop_was_visible') and self.shop_was_visible:
            self.shop_content.pack(fill="x", padx=10, pady=5)
            self.shop_toggle_btn.config(text="▼")
            self.shop_visible = True
            
        if hasattr(self, 'inv_was_visible') and self.inv_was_visible:
            self.inv_content.pack(fill="x", padx=10, pady=5)
            self.inv_toggle_btn.config(text="▼")
            self.inventory_visible = True
        
    # ========== МЕТОДЫ ДЛЯ НАСТРОЙКИ ВРЕМЕНИ ==========
    
    def update_work_time_preview(self, value):
        """Предпросмотр времени работы"""
        self.work_time_label.config(text=f"{int(float(value))} мин")
        
    def update_break_time_preview(self, value):
        """Предпросмотр времени отдыха"""
        self.break_time_label.config(text=f"{int(float(value))} мин")
        
    def apply_settings(self):
        """Применяет настройки времени к таймеру"""
        if self.timer.is_running:
            messagebox.showwarning("Внимание", "Нельзя менять настройки во время работы таймера!")
            return
            
        self.work_time = self.work_time_var.get()
        self.break_time = self.break_time_var.get()
        
        self.timer.work_duration = self.work_time * 60
        self.timer.break_duration = self.break_time * 60
        self.timer.seconds_left = self.work_time * 60
        self.timer.is_work_phase = True
        
        self.time_label.config(text=self.timer._format_time())
        self.phase_label.config(text="📚 УЧИСЬ!", fg=self.success_color)
        
        messagebox.showinfo("Настройки применены", 
                           f"✅ Время работы: {self.work_time} мин\n"
                           f"✅ Время отдыха: {self.break_time} мин")
        
    # ========== МЕТОДЫ ДЛЯ КОЛИЧЕСТВА ПОМИДОРОК ==========
    
    def update_pomodoro_count(self):
        """Обновляет количество помидорок"""
        try:
            self.pomodoro_count = int(self.count_var.get())
            self.current_pomodoro = 0
            self.update_progress_display()
        except:
            pass
            
    def update_progress_display(self):
        """Обновляет отображение прогресса"""
        if self.pomodoro_count > 0:
            self.progress_label.config(text=f"Прогресс: {self.current_pomodoro}/{self.pomodoro_count}")
            self.progress_bar['value'] = (self.current_pomodoro / self.pomodoro_count) * 100
            self.pomodoro_counter_label.config(text=f"Помидорка {self.current_pomodoro + 1}/{self.pomodoro_count}")
        
    # ========== МЕТОДЫ ТАЙМЕРА ==========
    
    def start_timer(self):
        """Запуск таймера"""
        if self.current_pomodoro >= self.pomodoro_count:
            if messagebox.askyesno("Новая серия", "Ты выполнил все помидорки! Начать новую серию?"):
                self.current_pomodoro = 0
                self.update_progress_display()
            else:
                return
        
        # Прячем все настройки
        self.hide_all_settings()
        
        self.timer.start()
        self.start_button.config(state="disabled", text="⚔️ В БИТВЕ ⚔️", bg="#45a049")
        self.pause_button.config(state="normal")
        self.status_label.config(text="⚔️ В БИТВЕ! ⚔️", fg=self.success_color)
        
        self.is_locked = True
        self.lock_screen()
        self.grab_focus()
        self.monitor_focus()
        
        self.root.bind('<Alt-F4>', lambda e: 'break')
        self.root.bind('<Alt-Tab>', lambda e: 'break')
        self.root.bind('<Escape>', lambda e: 'break')
        
    def pause_timer(self):
        """Пауза"""
        self.timer.pause()
        self.start_button.config(state="normal", text="▶️ СТАРТ", bg=self.success_color)
        self.pause_button.config(state="disabled")
        self.status_label.config(text="⏸️ Пауза", fg=self.warning_color)
        
        self.is_locked = False
        self.unlock_screen()
        
    def fail_timer(self):
        """Провал"""
        self.timer.fail()
        self.start_button.config(state="normal", text="▶️ СТАРТ", bg=self.success_color)
        self.pause_button.config(state="disabled")
        self.status_label.config(text="😵 Поражение", fg=self.danger_color)
        
        # Возвращаем настройки
        self.restore_settings()
        
        self.is_locked = False
        self.unlock_screen()
        
    def update_timer_display(self, time_str, is_work_phase):
        """Обновляет отображение таймера"""
        self.time_label.config(text=time_str)
        if is_work_phase:
            self.phase_label.config(text="📚 УЧИСЬ!", fg=self.success_color)
        else:
            self.phase_label.config(text="☕ ОТДЫХАЙ", fg=self.warning_color)
            
    def on_pomodoro_success(self):
        """Успешное завершение одной помидорки"""
        self.current_pomodoro += 1
        self.update_progress_display()
        
        self.hero.add_reward(50, 10)
        self.update_character_display()
        save_manager.save_game(self.hero)
        
        self.show_success_animation()
        
        if self.current_pomodoro >= self.pomodoro_count:
            self.status_label.config(text="🎉 СЕРИЯ ЗАВЕРШЕНА! 🎉", fg="#ffd700")
            self.start_button.config(state="normal", text="▶️ НОВАЯ СЕРИЯ", bg=self.success_color)
            self.pause_button.config(state="disabled")
            
            # Возвращаем настройки
            self.restore_settings()
            
            self.is_locked = False
            self.unlock_screen()
            
            messagebox.showinfo("Великолепно!", 
                              f"✅ Ты выполнил все {self.pomodoro_count} помидорок!\n"
                              f"Получено: {self.pomodoro_count * 50} XP\n"
                              f"Получено: {self.pomodoro_count * 10} золота")
        else:
            self.status_label.config(text=f"⚔️ Помидорка {self.current_pomodoro + 1}/{self.pomodoro_count} ⚔️", 
                                    fg=self.success_color)
            self.start_button.config(state="normal", text="▶️ СТАРТ", bg=self.success_color)
            self.pause_button.config(state="disabled")
            
            # Возвращаем настройки на отдыхе
            self.restore_settings()
            
            self.is_locked = False
            self.unlock_screen()
        
    def on_pomodoro_fail(self):
        """Провал Pomodoro"""
        self.hero.take_damage(15)
        self.update_character_display()
        save_manager.save_game(self.hero)
        messagebox.showwarning("Поражение", f"😵 Вы отвлеклись!\nПерсонаж потерял 15 HP")
        
    # ========== АНИМАЦИЯ ==========
    
    def show_success_animation(self):
        """Показывает красивую анимацию при успехе"""
        if self.animation_running:
            return
            
        self.animation_running = True
        
        anim = tk.Toplevel(self.root)
        anim.title("✨ ПОБЕДА! ✨")
        anim.geometry("500x400")
        anim.configure(bg='black')
        anim.attributes('-topmost', True)
        anim.transient(self.root)
        anim.overrideredirect(True)
        
        anim.update_idletasks()
        x = (anim.winfo_screenwidth() // 2) - (500 // 2)
        y = (anim.winfo_screenheight() // 2) - (400 // 2)
        anim.geometry(f'500x400+{x}+{y}')
        
        title = tk.Label(anim, text="✨ +50 XP ✨", 
                        font=("Arial", 40, "bold"),
                        bg='black', fg='gold')
        title.pack(expand=True)
        
        icon = tk.Label(anim, text="🏆", 
                       font=("Arial", 80),
                       bg='black', fg='gold')
        icon.pack()
        
        text = tk.Label(anim, text=f"Помидорка {self.current_pomodoro} завершена!",
                       font=("Arial", 18),
                       bg='black', fg='white')
        text.pack()
        
        colors = ['gold', 'yellow', 'orange', 'red', 'purple', 'blue']
        
        def animate(i=0):
            if not anim.winfo_exists():
                self.animation_running = False
                return
                
            color = colors[i % len(colors)]
            title.config(fg=color)
            icon.config(fg=color)
            
            if i % 2 == 0:
                title.config(font=("Arial", 42, "bold"))
            else:
                title.config(font=("Arial", 38, "bold"))
                
            if i < 20:
                anim.after(100, lambda: animate(i+1))
            else:
                anim.destroy()
                self.animation_running = False
                
        animate()
        
    # ========== МЕТОДЫ БЛОКИРОВКИ ==========
    
    def lock_screen(self):
        """Блокирует экран во время таймера"""
        self.root.attributes('-topmost', True)
        self.root.attributes('-fullscreen', True)
        self.root.overrideredirect(True)
        
    def unlock_screen(self):
        """Разблокирует экран"""
        self.root.attributes('-topmost', False)
        self.root.attributes('-fullscreen', False)
        self.root.overrideredirect(False)
        
        if self.trap_window and self.trap_window.winfo_exists():
            self.trap_window.destroy()
            self.trap_window = None
        
    def grab_focus(self):
        """Постоянно возвращает фокус"""
        if self.is_locked:
            self.root.lift()
            self.root.focus_force()
            self.root.after(100, self.grab_focus)
            
    def monitor_focus(self):
        """Мониторит потерю фокуса"""
        if self.is_locked:
            if not self.root.focus_get():
                self.show_trap_window()
            self.root.after(500, self.monitor_focus)
            
    def show_trap_window(self):
        """Показывает окно-ловушку"""
        if self.trap_window and self.trap_window.winfo_exists():
            return
            
        self.trap_window = tk.Toplevel(self.root)
        self.trap_window.title("⚠️ НЕЛЬЗЯ УЙТИ ⚠️")
        
        screen_width = self.root.winfo_screenwidth()
        screen_height = self.root.winfo_screenheight()
        self.trap_window.geometry(f"{screen_width}x{screen_height}+0+0")
        self.trap_window.configure(bg='red')
        self.trap_window.attributes('-topmost', True)
        self.trap_window.attributes('-fullscreen', True)
        self.trap_window.overrideredirect(True)
        
        tk.Label(self.trap_window, 
                text="💀 ТЫ НЕ МОЖЕШЬ УЙТИ! 💀",
                font=("Arial", 48, "bold"),
                bg='red', fg='white').pack(expand=True)
        
        tk.Label(self.trap_window,
                text=f"⚔️ {self.hero.name} сражается!",
                font=("Arial", 24),
                bg='red', fg='white').pack()
        
        tk.Label(self.trap_window,
                text=f"❤️ Осталось HP: {self.hero.current_hp}",
                font=("Arial", 24),
                bg='red', fg='white').pack()
        
        tk.Label(self.trap_window,
                text=f"⏰ Время: {self.time_label.cget('text')}",
                font=("Arial", 24),
                bg='red', fg='white').pack()
        
        tk.Button(self.trap_window,
                 text="⚔️ ВЕРНУТЬСЯ ⚔️",
                 command=self.close_trap_window,
                 bg='green', fg='white',
                 font=("Arial", 20)).pack(pady=50)
        
        self.trap_window.protocol("WM_DELETE_WINDOW", lambda: None)
        
    def close_trap_window(self):
        """Закрывает окно-ловушку"""
        if self.trap_window and self.trap_window.winfo_exists():
            self.trap_window.destroy()
            self.trap_window = None
        self.root.lift()
        self.root.focus_force()
        
    # ========== МЕТОДЫ ИНТЕРФЕЙСА ==========
    
    def update_character_display(self):
        """Обновляет характеристики персонажа"""
        self.level_value.config(text=str(self.hero.level))
        
        self.xp_label.config(text=f"XP: {self.hero.current_xp}/{self.hero.xp_to_next}")
        self.xp_bar['value'] = (self.hero.current_xp / self.hero.xp_to_next) * 100
        
        self.hp_label.config(text=f"❤️ HP: {self.hero.current_hp}/{self.hero.max_hp}")
        self.hp_bar['value'] = (self.hero.current_hp / self.hero.max_hp) * 100
        
        self.gold_label.config(text=f"💰 Золото: {self.hero.gold}")
        
        if self.hero.debuff != Debuff.NONE:
            self.debuff_label.config(text=f"⚠️ ДЕБАФФ ⚠️")
            self.debuff_label.pack(pady=5, padx=10, fill="x")
        else:
            self.debuff_label.pack_forget()
            
        self.inventory_listbox.delete(0, tk.END)
        for item in self.hero.inventory:
            self.inventory_listbox.insert(tk.END, item)
            
    def buy_item(self, item_type, cost):
        """Покупка предмета"""
        if item_type == "potion":
            if self.hero.buy_item("🧪 Малое зелье", cost):
                self.hero.heal(30)
                messagebox.showinfo("Успех", f"❤️ +30 HP!")
        elif item_type == "scroll":
            if self.hero.buy_item("📜 Свиток мудрости", cost):
                self.hero.current_xp += 50
                while self.hero.current_xp >= self.hero.xp_to_next:
                    self.hero.level_up()
                messagebox.showinfo("Успех", f"📚 +50 XP!")
                
        self.update_character_display()
        save_manager.save_game(self.hero)
        
    # ========== АВАРИЙНЫЙ ВЫХОД ==========
    
    def emergency_exit(self, event=None):
        """Секретный аварийный выход (Ctrl+Shift+E)"""
        result = messagebox.askyesno(
            "⚠️ АВАРИЙНЫЙ ВЫХОД ⚠️",
            "Ты действительно хочешь принудительно закрыть игру?\n\n"
            "❗ Это считается провалом!\n"
            f"❤️ Потеря: 50 HP\n"
            f"📉 Потеря: 30 XP\n\n"
            "Продолжить?",
            icon='warning'
        )
        
        if result:
            if self.timer.is_running:
                self.hero.take_damage(50)
                self.hero.current_xp = max(0, self.hero.current_xp - 30)
                self.hero.debuff = Debuff.WEAK
                save_manager.save_game(self.hero)
            
            self.root.destroy()
            
        return 'break'
        
    # ========== ЗАЩИТА ОТ ЗАКРЫТИЯ ==========
    
    def on_closing(self):
        """Перехватываем закрытие окна"""
        if self.timer.is_running:
            result = messagebox.askyesno(
                "⚠️ ВНИМАНИЕ!", 
                f"Таймер запущен!\n\nЗакрыть приложение = потеря 50 HP и 30 XP\n\nПродолжить учёбу?",
                icon='warning'
            )
            if result:
                self.hero.take_damage(50)
                self.hero.current_xp = max(0, self.hero.current_xp - 30)
                self.hero.debuff = Debuff.WEAK
                save_manager.save_game(self.hero)
                self.root.destroy()
        else:
            save_manager.save_game(self.hero)
            self.root.destroy()

# ========== ЗАПУСК ==========
if __name__ == "__main__":
    root = tk.Tk()
    app = StudyDungeonApp(root)
    root.mainloop()
