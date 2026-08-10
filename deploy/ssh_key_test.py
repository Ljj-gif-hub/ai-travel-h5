"""测试 SSH 密钥登录（部署用）"""
import sys
import paramiko

host = sys.argv[1]
key_path = sys.argv[2]
user = "root"

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
key = paramiko.Ed25519Key.from_private_key_file(key_path)
c.connect(hostname=host, port=22, username=user, pkey=key,
          look_for_keys=False, allow_agent=False, timeout=25)
stdin, stdout, stderr = c.exec_command("echo '=== 密钥登录成功 ==='; whoami; hostname", timeout=30, get_pty=True)
print(stdout.read().decode())
c.close()
