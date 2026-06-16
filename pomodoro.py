import time
import threading

class PomodoroTimer:
    def __init__(self, work_minutes=25, break_minutes=5):
        self.work_duration = work_minutes * 60
        self.break_duration = break_minutes * 60
        self.seconds_left = self.work_duration
        self.is_running = False
        self.is_work_phase = True
        self.callback_update = None
        self.callback_complete = None
        self.callback_fail = None
        
    def set_callbacks(self, on_update, on_complete, on_fail):
        self.callback_update = on_update
        self.callback_complete = on_complete
        self.callback_fail = on_fail
        
    def start(self):
        if not self.is_running:
            self.is_running = True
            self.thread = threading.Thread(target=self._run_timer)
            self.thread.daemon = True
            self.thread.start()
            
    def pause(self):
        self.is_running = False
        
    def fail(self):
        self.is_running = False
        if self.callback_fail:
            self.callback_fail()
        self.reset()
            
    def reset(self):
        self.is_running = False
        self.is_work_phase = True
        self.seconds_left = self.work_duration
        if self.callback_update:
            self.callback_update(self._format_time(), self.is_work_phase)
            
    def _run_timer(self):
        while self.is_running and self.seconds_left > 0:
            time.sleep(1)
            self.seconds_left -= 1
            if self.callback_update:
                self.callback_update(self._format_time(), self.is_work_phase)
                
        if self.is_running and self.seconds_left <= 0:
            self._switch_phase()
            
    def _switch_phase(self):
        if self.is_work_phase:
            if self.callback_complete:
                self.callback_complete()
            self.is_work_phase = False
            self.seconds_left = self.break_duration
        else:
            self.is_work_phase = True
            self.seconds_left = self.work_duration
            
        if self.is_running:
            self._run_timer()
        elif self.callback_update:
            self.callback_update(self._format_time(), self.is_work_phase)
            
    def _format_time(self):
        minutes = self.seconds_left // 60
        seconds = self.seconds_left % 60
        return f"{minutes:02d}:{seconds:02d}"
