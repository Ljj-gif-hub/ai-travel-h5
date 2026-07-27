#!/usr/bin/env python3
"""
真实地标图片抓取系统 v2.0
========================
数据源（按优先级）：
  1. 百度百科 og:image → 每个城市/景点都有的百科页面主图
  2. Bing 图片搜索 → 直接抓取搜索结果中的图片URL
  3. Pexels API → 高质量实拍图（需 API Key）
  4. 保留旧 URL → 不丢失数据

图片处理：居中裁剪 800×800 正方形 JPEG

用法：
  python fetch_real_images.py                         # 全部抓取
  python fetch_real_images.py --test                   # 测试前 10 个
  python fetch_real_images.py --cities-only            # 仅城市
  python fetch_real_images.py --attractions-only       # 仅景点
  python fetch_real_images.py --start 深圳             # 断点续传
  python fetch_real_images.py --pexels-key YOUR_KEY    # 启用 Pexels 兜底
  python fetch_real_images.py --dry-run                # 仅检查映射，不下载

依赖：
  pip install curl_cffi Pillow requests
"""

import os, sys, json, time, hashlib, csv, argparse, re
from pathlib import Path
from io import BytesIO
from urllib.parse import quote

import requests as req_lib  # standard requests for non-Baidu calls

try:
    from curl_cffi import requests as baidu_req
    HAS_CURL_CFFI = True
except ImportError:
    print("[WARN] curl_cffi 未安装，百度百科抓取将不可用: pip install curl_cffi")
    HAS_CURL_CFFI = False
    baidu_req = req_lib

try:
    from PIL import Image
except ImportError:
    print("请先安装 Pillow: pip install Pillow")
    sys.exit(1)

# ==================== 路径配置 ====================
PROJECT = Path(__file__).resolve().parent.parent
IMG_DIR = PROJECT / "trval-h5" / "public" / "images" / "landmarks"
CITY_JSON = PROJECT / "trval-h5" / "public" / "city-images.json"
ATTR_JSON = PROJECT / "trval-h5" / "public" / "attraction-images.json"
REPORT = Path(__file__).resolve().parent / "fetch_report.csv"

IMG_SIZE = 800
QUALITY = 88
DELAY_BAIKE = 0.8   # 百度百科请求间隔
DELAY_BING = 2.0    # Bing 图片搜索间隔
DELAY_PEXELS = 2.5  # Pexels API 间隔 (免费 200次/小时)

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
}

# ==================== 数据源 1: 百度百科 ====================
def fetch_baike(name):
    """从百度百科获取主图 URL"""
    if not HAS_CURL_CFFI:
        return None, "curl_cffi_not_installed"

    # URL 编码中文名
    encoded = quote(name, safe='')
    urls_to_try = [
        f"https://baike.baidu.com/item/{encoded}",
        f"https://baike.baidu.com/item/{encoded}?view=home",
    ]

    for url in urls_to_try:
        try:
            r = baidu_req.get(url, impersonate="chrome120", headers=HEADERS, timeout=12)
            if r.status_code == 403 or len(r.text) < 3000:
                continue

            # 策略 1: og:image meta 标签
            m = re.search(r'<meta[^>]+property="og:image"[^>]+content="([^"]+)"', r.text)
            if m:
                return m.group(1), "baike_og"

            # 策略 2: summary-pic 区域
            m = re.search(r'class="summary-pic"[^>]*>.*?src="([^"]+)"', r.text, re.DOTALL)
            if m:
                return m.group(1), "baike_summary"

            # 策略 3: 任意 bkimg.cdn.bcebos.com 图片
            bkimgs = re.findall(r'https?://bkimg[^"\s<>]+\.(?:jpg|jpeg|png)[^"\s<>]*', r.text)
            if bkimgs:
                # 优先选大的
                for img in bkimgs:
                    if 'x-bce-process' in img or 'pic' in img:
                        return img, "baike_bkimg"

        except Exception as e:
            continue

    return None, "baike_failed"


# ==================== 数据源 2: Bing 图片搜索 ====================
def fetch_bing(name, kind="city"):
    """从 Bing 图片搜索抓取图片 URL"""
    if kind == "city":
        queries = [
            f"{name} 城市 风景 地标",
            f"{name} city skyline landmark",
        ]
    else:
        queries = [
            f"{name} 景点 风景",
            f"{name} landmark travel",
        ]

    for q in queries:
        try:
            url = f"https://www.bing.com/images/search?q={quote(q)}&first=1"
            r = req_lib.get(url, headers=HEADERS, timeout=12)

            if r.status_code != 200:
                continue

            # Bing 在 HTML 中将 " 编码为 &quot;，先解码再提取
            decoded = r.text.replace("&quot;", '"').replace("&#39;", "'")

            # 提取 murl (真实图片 URL)
            murls = re.findall(r'"murl"\s*:\s*"(https?://[^"]+)"', decoded)

            # 过滤：排除太短的、bing/microsoft 域名的
            bad_domains = ['bing.com', 'microsoft.com', 'live.com']
            bad_keywords = ['icon', 'logo', 'avatar', 'emoji', 'favicon', 'button',
                           'bubble', 'sticker', 'badge', 'qr', 'placeholder',
                           'facebook_sharing', 'bing_sharing']

            for img_url in murls:
                if any(d in img_url for d in bad_domains):
                    continue
                if any(k in img_url.lower() for k in bad_keywords):
                    continue
                if img_url.lower().endswith(('.jpg', '.jpeg', '.png', '.webp')):
                    return img_url, f"bing:{q[:20]}"

        except Exception:
            continue

    return None, "bing_failed"


# ==================== 数据源 3: Pexels API ====================
def fetch_pexels(name, api_key):
    """使用 Pexels API 搜索图片"""
    if not api_key:
        return None, "no_pexels_key"

    try:
        r = req_lib.get(
            "https://api.pexels.com/v1/search",
            headers={"Authorization": api_key},
            params={"query": f"{name} landmark travel", "per_page": 3, "orientation": "landscape", "size": "large"},
            timeout=15,
        )
        r.raise_for_status()
        photos = r.json().get("photos", [])
        if photos:
            return photos[0]["src"]["large2x"], f"pexels:{photos[0]['photographer']}"
    except Exception as e:
        pass

    return None, "pexels_failed"


# ==================== 图片处理 ====================
def download_and_crop(img_url, save_path):
    """下载图片并居中裁剪为 800×800 正方形"""
    try:
        resp = req_lib.get(img_url, headers=HEADERS, timeout=20, stream=True)
        resp.raise_for_status()

        # 检查 Content-Type
        ct = resp.headers.get("Content-Type", "")
        if "image" not in ct and not img_url.lower().endswith(('.jpg', '.jpeg', '.png', '.webp')):
            return False

        img_data = resp.content
        if len(img_data) < 5000:  # 太小，不是真实图片
            return False

        img = Image.open(BytesIO(img_data))

        # 转换为 RGB
        if img.mode in ("RGBA", "P", "LA"):
            rgb = Image.new("RGB", img.size, (255, 255, 255))
            if img.mode == "P":
                img = img.convert("RGBA")
            rgb.paste(img, mask=img.split()[-1] if img.mode == "RGBA" else None)
            img = rgb
        elif img.mode != "RGB":
            img = img.convert("RGB")

        w, h = img.size
        if w < 300 or h < 300:
            return False

        # 居中裁剪正方形
        side = min(w, h)
        left = (w - side) // 2
        top = (h - side) // 2
        img = img.crop((left, top, left + side, top + side))
        img = img.resize((IMG_SIZE, IMG_SIZE), Image.LANCZOS)

        os.makedirs(os.path.dirname(save_path), exist_ok=True)
        img.save(save_path, "JPEG", quality=QUALITY)
        return True
    except Exception:
        return False


# ==================== 核心: 多源抓取 ====================
def fetch_image(name, kind="city", pexels_key=None):
    """
    多策略获取图片:
      1) 百度百科 og:image
      2) Bing 图片搜索
      3) Pexels API (如果提供了 key)
      4) 失败
    返回: (local_path_or_none, source_label)
    """
    safe = re.sub(r'[\\/:*?"<>|]', '_', name)
    fname = f"{hashlib.md5(safe.encode()).hexdigest()[:12]}.jpg"
    fpath = IMG_DIR / fname

    # 已存在 → 跳过
    if fpath.exists():
        return f"/images/landmarks/{fname}", "cache"

    # ---- 策略 1: 百度百科 ----
    img_url, source = fetch_baike(name)
    time.sleep(DELAY_BAIKE)
    if img_url and download_and_crop(img_url, str(fpath)):
        return f"/images/landmarks/{fname}", source

    # ---- 策略 2: Bing 图片搜索 ----
    img_url, source = fetch_bing(name, kind)
    time.sleep(DELAY_BING)
    if img_url and download_and_crop(img_url, str(fpath)):
        return f"/images/landmarks/{fname}", source

    # ---- 策略 3: Pexels API ----
    if pexels_key:
        img_url, source = fetch_pexels(name, pexels_key)
        time.sleep(DELAY_PEXELS)
        if img_url and download_and_crop(img_url, str(fpath)):
            return f"/images/landmarks/{fname}", source

    return None, "all_failed"


# ==================== 主流程 ====================
def main():
    parser = argparse.ArgumentParser(description="真实地标图片抓取系统 v2.0")
    parser.add_argument("--test", action="store_true", help="仅测试前 10 个")
    parser.add_argument("--dry-run", action="store_true", help="不下载，仅检查映射")
    parser.add_argument("--start", default="", help="断点续传起始名称")
    parser.add_argument("--cities-only", action="store_true")
    parser.add_argument("--attractions-only", action="store_true")
    parser.add_argument("--pexels-key", default="", help="Pexels API Key (免费注册 pexels.com/api)")
    args = parser.parse_args()

    IMG_DIR.mkdir(parents=True, exist_ok=True)

    # ---- 构建任务列表 ----
    tasks = []
    if not args.attractions_only:
        with open(CITY_JSON, "r", encoding="utf-8") as f:
            city_data = json.load(f)
        tasks += [(name, "city") for name in city_data.keys()]
    if not args.cities_only:
        with open(ATTR_JSON, "r", encoding="utf-8") as f:
            attr_data = json.load(f)
        tasks += [(name, "attraction") for name in attr_data.keys()]

    if args.test:
        tasks = tasks[:10]

    if args.start:
        skip = True
        filtered = []
        for t in tasks:
            if skip and t[0] == args.start:
                skip = False
            if not skip:
                filtered.append(t)
        tasks = filtered
        print(f"从 {args.start} 继续，剩余 {len(tasks)} 项")

    total = len(tasks)
    city_n = sum(1 for _, k in tasks if k == "city")
    attr_n = sum(1 for _, k in tasks if k == "attraction")

    print(f"\n{'='*55}")
    print(f"  真实地标图片抓取系统 v2.0")
    print(f"  数据源: 百度百科 → Bing → Pexels")
    print(f"  百度百科: {'可用' if HAS_CURL_CFFI else '不可用 (pip install curl_cffi)'}")
    print(f"  Pexels: {'已配置' if args.pexels_key else '未配置 (可选)'}")
    print(f"  城市: {city_n}  |  景点: {attr_n}  |  总计: {total}")
    print(f"{'='*55}\n")

    # ---- 加载现有 JSON ----
    city_json = json.load(open(CITY_JSON, "r", encoding="utf-8")) if CITY_JSON.exists() else {}
    attr_json = json.load(open(ATTR_JSON, "r", encoding="utf-8")) if ATTR_JSON.exists() else {}

    success = 0
    failed = 0
    skipped = 0
    rows = []
    sources = {}

    for i, (name, kind) in enumerate(tasks):
        # 跳过已有本地图片的条目
        current = city_json.get(name, "") if kind == "city" else attr_json.get(name, "")
        if current.startswith("/images/landmarks/"):
            fname = current.split("/")[-1]
            if (IMG_DIR / fname).exists():
                skipped += 1
                sources["cache"] = sources.get("cache", 0) + 1
                continue

        print(f"[{i+1}/{total}] {name} ({kind})")

        if args.dry_run:
            paths = city_json if kind == "city" else attr_json
            paths[name] = f"/images/landmarks/{hashlib.md5(name.encode()).hexdigest()[:12]}.jpg"
            rows.append([name, kind, "DRY_RUN", paths[name], "dry_run"])
            success += 1
            continue

        path, source = fetch_image(name, kind, args.pexels_key)

        if path:
            if kind == "city":
                city_json[name] = path
            else:
                attr_json[name] = path
            sources[source] = sources.get(source, 0) + 1
            print(f"  OK [{source}]")
            rows.append([name, kind, "成功", path, source])
            success += 1
        else:
            # 保留旧 URL（picsum 兜底）
            rows.append([name, kind, "失败-保留旧URL", current or "", "all_failed"])
            # 如果还没 URL，生成一个 picsum 兜底
            if not current:
                seed = hashlib.md5(name.encode()).hexdigest()[:8]
                fallback = f"https://picsum.photos/seed/{seed}/800/800"
                if kind == "city":
                    city_json[name] = fallback
                else:
                    attr_json[name] = fallback
            print(f"  FAIL [{source}]")
            failed += 1

        # 每 25 个保存进度
        if (i + 1) % 25 == 0:
            _save(city_json, attr_json, rows)
            print(f"  [进度: {i+1}/{total}]")

    # 最终保存
    _save(city_json, attr_json, rows)

    # ---- 统计 ----
    print(f"\n{'='*55}")
    print(f"  完成！")
    print(f"  成功: {success}  |  失败: {failed}  |  跳过: {skipped}  |  总计: {total}")
    print(f"  数据源分布:")
    for s, c in sorted(sources.items(), key=lambda x: -x[1]):
        print(f"    {s}: {c}")
    print(f"  城市映射: {CITY_JSON}  ({len(city_json)} 条)")
    print(f"  景点映射: {ATTR_JSON}  ({len(attr_json)} 条)")
    print(f"  报告: {REPORT}")
    print(f"{'='*55}")

    if failed > 0:
        print(f"\n  建议: 使用 --pexels-key YOUR_KEY 启用 Pexels API 兜底可提升成功率")
        print(f"  免费注册: https://www.pexels.com/api/")


def _save(city_json, attr_json, rows):
    with open(CITY_JSON, "w", encoding="utf-8") as f:
        json.dump(city_json, f, ensure_ascii=False, indent=2)
    with open(ATTR_JSON, "w", encoding="utf-8") as f:
        json.dump(attr_json, f, ensure_ascii=False, indent=2)
    with open(REPORT, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["名称", "类型", "状态", "路径", "来源"])
        w.writerows(rows)


if __name__ == "__main__":
    main()
