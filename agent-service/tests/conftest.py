"""pytest 共享配置：把 agent-service 根目录加入 sys.path，使测试可直接 import agent.*"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
