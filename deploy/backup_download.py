"""从服务器下载最新 MySQL 备份到本地（G盘）——供 Windows 计划任务每天调用

用法: python backup_download.py [服务器IP] [远程目录] [本地目录]
默认: 8.148.223.54  /opt/backups/mysql  G:\ai-travel-backups
"""
import os
import sys
import glob
import paramiko

host = sys.argv[1] if len(sys.argv) > 1 else "8.148.223.54"
remote_dir = sys.argv[2] if len(sys.argv) > 2 else "/opt/backups/mysql"
local_dir = sys.argv[3] if len(sys.argv) > 3 else r"G:\ai-travel-backups"
keep_local = 14  # 本地只保留最近 14 个备份

key_path = os.path.expanduser("~/.ssh/id_ed25519")
if not os.path.exists(key_path):
    print(f"[错误] 找不到 SSH 密钥: {key_path}")
    sys.exit(1)
os.makedirs(local_dir, exist_ok=True)

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
key = paramiko.Ed25519Key.from_private_key_file(key_path)
c.connect(hostname=host, port=22, username="root", pkey=key,
          look_for_keys=False, allow_agent=False, timeout=25)

# 找最新的备份文件
stdin, stdout, stderr = c.exec_command(
    f"ls -t {remote_dir}/travel_plans_*.sql.gz 2>/dev/null | head -1", timeout=30)
remote_file = stdout.read().decode().strip()
if not remote_file:
    print("服务器上还没有备份文件（稍后会自动生成）")
    sys.exit(0)

name = remote_file.split("/")[-1]
local_path = os.path.join(local_dir, name)

# 已存在则跳过
if os.path.exists(local_path):
    print(f"已是最新，跳过: {name}")
else:
    sftp = c.open_sftp()
    sftp.get(remote_file, local_path)
    sftp.close()
    print(f"✓ 已下载: {name} ({os.path.getsize(local_path)/1024:.0f} KB) -> {local_dir}")

c.close()

# 清理本地过旧备份
files = sorted(glob.glob(os.path.join(local_dir, "travel_plans_*.sql.gz")), reverse=True)
for old in files[keep_local:]:
    os.remove(old)
    print(f"清理旧备份: {os.path.basename(old)}")
