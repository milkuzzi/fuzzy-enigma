import os
import sys
import subprocess

def build_exe():
    """Собирает exe файл со всеми зависимостями"""
    
    print("🔨 Начинаю сборку Study Dungeon...")
    
    # Проверяем наличие pyinstaller
    try:
        import PyInstaller
    except:
        print("📦 Устанавливаю pyinstaller...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "pyinstaller"])
    
    # Создаем spec файл для точной настройки
    spec_content = """
# -*- mode: python ; coding: utf-8 -*-

a = Analysis(
    ['main.py'],
    pathex=[],
    binaries=[],
    datas=[
        ('character.py', '.'),
        ('pomodoro.py', '.'),
        ('save_manager.py', '.'),
    ],
    hiddenimports=[],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='StudyDungeon',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,  # False = без консоли, True = с консолью
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon='icon.ico' if os.path.exists('icon.ico') else None,
)
"""
    
    # Сохраняем spec файл
    with open("build.spec", "w", encoding='utf-8') as f:
        f.write(spec_content)
    
    # Запускаем сборку
    print("⚙️  Компилирую в EXE...")
    subprocess.check_call(["pyinstaller", "build.spec"])
    
    # Очищаем временные файлы
    print("🧹 Очищаю временные файлы...")
    if os.path.exists("build"):
        import shutil
        shutil.rmtree("build")
    if os.path.exists("build.spec"):
        os.remove("build.spec")
    
    print("\n✅ Готово! EXE файл находится в папке 'dist'")
    print("📁 Путь: dist/StudyDungeon.exe")

if __name__ == "__main__":
    build_exe()
