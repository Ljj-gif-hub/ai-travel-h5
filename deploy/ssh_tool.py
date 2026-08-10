"""远程 SSH/SFTP 辅助工具（部署用）
用法（密码走环境变量 SSH_PASS，避免进命令行/脚本）:
  python ssh_tool.py --host HOST --user root --run "命令"
  python ssh_tool.py --host HOST --user root --upload 本地文件 --remote /opt/xx
  python ssh_tool.py --host HOST --user root --tar 本地.tar.gz --extract /opt
  python ssh_tool.py --host HOST --user root --tail /tmp/deploy.log --n 30
"""
import argparse
import os
import sys
import paramiko


def get_conn(args):
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())

    # 优先密钥登录（服务器已关闭密码认证）
    key_path = os.environ.get("SSH_KEY") or os.path.join(os.path.expanduser("~"), ".ssh", "id_ed25519")
    if os.path.exists(key_path):
        key = paramiko.Ed25519Key.from_private_key_file(key_path)
        c.connect(
            hostname=args.host, port=22, username=args.user, pkey=key,
            look_for_keys=False, allow_agent=False, timeout=25,
        )
        return c

    # 回退：密码登录（仅测试阶段，服务器关闭后不可用）
    password = os.environ.get("SSH_PASS") or args.password
    if not password:
        sys.exit("未找到 SSH 密钥 (~/.ssh/id_ed25519)，且未提供 SSH_PASS")
    c.connect(
        hostname=args.host, port=22, username=args.user, password=password,
        look_for_keys=False, allow_agent=False, timeout=25,
    )
    return c


def run_cmd(c, cmd, timeout=3600):
    # get_pty=True 会把 stderr 并入 stdout，避免双缓冲死锁
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout, get_pty=True)
    out = stdout.read().decode("utf-8", "replace")
    code = stdout.channel.recv_exit_status()
    return code, out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", required=True)
    ap.add_argument("--user", default="root")
    ap.add_argument("--password", default="")
    ap.add_argument("--run", help="执行远程命令")
    ap.add_argument("--timeout", type=int, default=3600)
    ap.add_argument("--upload", help="上传本地文件")
    ap.add_argument("--remote", help="上传目标路径")
    ap.add_argument("--tar", help="上传 tar.gz 并解压")
    ap.add_argument("--extract", help="--tar 的解压目标目录")
    ap.add_argument("--tail", help="查看远程文件末尾")
    ap.add_argument("--n", type=int, default=30)
    args = ap.parse_args()

    c = get_conn(args)

    if args.run:
        code, out = run_cmd(c, args.run, args.timeout)
        print(out, end="")
        sys.exit(code)

    if args.upload and args.remote:
        sftp = c.open_sftp()
        sftp.put(args.upload, args.remote)
        sftp.close()
        print(f"[OK] 上传完成 {args.upload} -> {args.remote}")

    if args.tar and args.extract:
        sftp = c.open_sftp()
        # 远程路径必须用 / 拼接（Windows 的 os.path.join 会产生 \，Linux 下会变成文件名/转义）
        remote = args.extract.rstrip("/") + "/" + os.path.basename(args.tar)
        print(f"[...] 上传 {args.tar} -> {remote}（约 {os.path.getsize(args.tar)/1e6:.0f} MB）")
        sftp.put(args.tar, remote)
        sftp.close()
        print("[OK] 上传完成，开始解压...")
        code, out = run_cmd(c, f"mkdir -p {args.extract} && tar -xzf {remote} -C {args.extract} && rm -f {remote} && ls {args.extract}", 600)
        print(out, end="")
        sys.exit(code)

    if args.tail:
        code, out = run_cmd(c, f"tail -n {args.n} {args.tail}", 60)
        print(out, end="")
        sys.exit(code)

    c.close()


if __name__ == "__main__":
    main()
