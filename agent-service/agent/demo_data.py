"""
Demo 模式内置数据 — 8 城真实景点库 + 兜底行程构建

从 planner.py 拆分而来（A3，保持行为不变）：
  get_demo_research: 返回目的地内置景点/美食/酒店区域数据（含模糊匹配与通用兜底）
  build_demo_plan:   用内置数据构建完整旅行方案（无需任何外部 API）
"""
from __future__ import annotations

from datetime import datetime

from .parsers import to_int


def get_demo_research(destination: str, days: int) -> dict:
    """Demo 模式：返回目的地的内置真实景点数据"""
    city_data = _DEMO_CITY_DATA.get(destination, None)
    if not city_data:
        # 模糊匹配
        for key in _DEMO_CITY_DATA:
            if key in destination or destination in key:
                city_data = _DEMO_CITY_DATA[key]
                break

    if not city_data:
        # 通用兜底
        city_data = {
            "summary": f"{destination}是一座充满魅力的旅游城市，建议安排{days}天深度游览，体验当地风土人情和特色美食。",
            "spots": [
                {"name": f"{destination}古城/老街区", "desc": "感受城市历史底蕴，漫步石板路，体验地道生活", "price": 0, "hours": "全天开放", "tips": "建议早晨前往避开人流"},
                {"name": f"{destination}博物馆", "desc": "了解城市历史文化，馆藏丰富", "price": 30, "hours": "09:00-17:00（周一闭馆）", "tips": "建议请讲解员，游览体验更佳"},
                {"name": f"{destination}地标观光塔", "desc": "俯瞰城市全景的最佳位置", "price": 80, "hours": "09:00-21:00", "tips": "选晴天前往，能见度高"},
                {"name": f"{destination}城市公园", "desc": "城市绿肺，本地人休闲好去处", "price": 0, "hours": "全天开放", "tips": "适合晨跑和傍晚散步"},
                {"name": f"{destination}特色街区", "desc": "文艺小店、网红咖啡馆聚集地", "price": 0, "hours": "10:00-22:00", "tips": "拍照打卡的好地方"},
            ],
            "foods": [f"{destination}特色小吃", f"{destination}老字号餐厅", f"{destination}夜市美食街"],
            "hotel_areas": {"市中心": "交通便利，周边配套齐全", "景区周边": "环境优美，适合度假"},
        }
    return city_data


def build_demo_plan(dest: str, days: int, budget: int, people: int, pace: str,
                    styles: list, hotel_level: str, research: dict) -> dict:
    """Demo 模式：用内置数据构建完整旅行方案"""
    spots = research.get("spots", [])
    foods = research.get("foods", [])
    hotel_areas = research.get("hotel_areas", {})

    # 住 days 天 = days-1 晚（至少 1 晚）（B10）
    nights = max(to_int(days, 3) - 1, 1)

    # 酒店价格映射
    hotel_price_base = {"经济型": 250, "舒适型": 500, "豪华型": 1000}
    price_per_night = hotel_price_base.get(hotel_level, 500)
    area_name, area_desc = next(iter(hotel_areas.items())) if hotel_areas else ("市中心", "交通便利")

    # 建酒店
    hotels = [
        {
            "name": f"{dest}{area_name}酒店",
            "district": area_name,
            "price_per_night": price_per_night,
            "total_price": price_per_night * nights,
            "rating": 4.3,
            "highlights": area_desc,
        }
    ]

    # 构建每日行程
    day_plans = []
    spot_idx = 0
    n_spots = len(spots)

    day_idx = 0
    for d in range(1, days + 1):
        s1 = spots[spot_idx % n_spots]
        s2 = spots[(spot_idx + 1) % n_spots]
        spot_idx += 2

        if d == 1:
            title = f"第{d}天：抵达{dest}·{s1['name']}"
        elif d == days:
            title = f"第{d}天：{s2['name']}·告别{dest}"
        else:
            title = f"第{d}天：{s1['name']} & {s2['name']}"

        pace_mult = {"轻松": 0.7, "适中": 1.0, "紧凑": 1.3}.get(pace, 1.0)

        time_slots = [
            {
                "time_of_day": "上午", "time": "08:30",
                "attraction": s1["name"], "activity": s1.get("desc", f"探索{s1['name']}，感受{dest}独特魅力"),
                "duration": f"{int(2.5 * pace_mult)}小时",
                "cost": int(s1.get("price", 0) or 0),
                "transport": "地铁" if d > 1 else "步行",
                "tips": s1.get("tips", ""),
                "hours": s1.get("hours", ""),
            },
            {
                "time_of_day": "下午", "time": "14:00",
                "attraction": s2["name"], "activity": s2.get("desc", f"深度游览{s2['name']}"),
                "duration": f"{int(2 * pace_mult)}小时",
                "cost": int(s2.get("price", 0) or 0),
                "transport": "步行",
                "tips": s2.get("tips", ""),
                "hours": s2.get("hours", ""),
            },
            {
                "time_of_day": "晚上", "time": "18:30",
                "attraction": f"{dest}特色美食街区",
                "activity": f"品尝当地美食：{'、'.join(foods[:3]) if foods else dest + '小吃'}，感受夜市烟火气。人气餐厅建议提前30分钟取号，避开用餐高峰时段。",
                "duration": "2小时",
                "cost": 100,
                "transport": "步行/地铁",
                "tips": "",
                "hours": "17:00-23:00",
            },
        ]
        day_plans.append({
            "day": d, "day_title": title,
            "time_slots": time_slots,
            "meals": [
                f"午餐：{foods[day_idx % len(foods)] if foods else '当地特色菜'}（人均60-80元）",
                f"晚餐：{foods[(day_idx+1) % len(foods)] if len(foods) > 1 else (foods[0] if foods else '地道小吃')}（人均80-100元）",
            ] if foods else [f"午餐：{dest}本地菜（人均60元）", f"晚餐：{dest}特色美食（人均80元）"],
        })
        day_idx += 1

    # 预算（晚间餐饮只计入 food，避免与晚上时段 cost 重复计费）
    total_budget = budget * people
    accommodation = price_per_night * nights
    tickets = sum(
        int(s.get("cost", 0) or 0)
        for dp in day_plans
        for s in dp["time_slots"]
        if s.get("time_of_day") != "晚上"
    )
    food = 80 * days * 3  # 三餐（含晚餐）
    transport_est = int(total_budget * 0.25)
    shopping = total_budget - accommodation - tickets - food - transport_est
    if shopping < 0:
        # 预算过紧：压缩住宿，保证 accommodation 非负且与酒店价格一致
        shopping = int(total_budget * 0.05)
        accommodation = total_budget - tickets - food - transport_est - shopping
        if accommodation < 0:
            accommodation = 0
        room_price = max(200, accommodation // max(nights, 1))
        hotels[0]["price_per_night"] = room_price
        hotels[0]["total_price"] = room_price * nights
        accommodation = room_price * nights

    return {
        "destination": dest, "days": days, "people": people,
        "total_budget": total_budget,
        "overview": research.get("summary", f"{dest}{days}天深度游"),
        "day_plans": day_plans,
        "budget_detail": {
            "transport": transport_est, "accommodation": accommodation,
            "food": food, "tickets": tickets, "shopping": shopping,
            "total": transport_est + accommodation + food + tickets + shopping,
        },
        "hotels": hotels,
        "transport": {
            "depart_type": "flight", "depart_title": f"飞往{dest}",
            "depart_detail": "建议选上午航班 · 提前2小时到机场",
            "depart_price": 800, "return_type": "flight",
            "return_title": f"从{dest}返程", "return_detail": "建议选傍晚航班",
            "return_price": 800,
        },
        "tips": [
            f"📱 提前在官方渠道预订{dest}热门景点门票，旺季至少提前3天",
            f"🌤️ 出行前一周查询{dest}天气预报，准备合适的衣物和防晒用品",
            f"🍜 必尝美食：{'、'.join(foods[:4])}" if foods else f"🍜 尝试{dest}本地特色美食，打开大众点评看本地人推荐",
            f"🚇 {dest}市内交通以地铁+公交为主，建议下载当地交通APP",
            "📷 热门拍照打卡点建议早晨8点前到达，避开旅行团人流",
            "💊 随身携带常用药物：肠胃药、创可贴、晕车药",
            f"💰 {dest}大部分景点支持线上购票，比现场便宜10-20%",
            "🔌 带上充电宝，导航+拍照耗电很快",
        ],
        "research_notes": [
            f"Demo 模式 — 使用 {dest} 内置景点/酒店数据",
            "门票价格为参考价，以景区当日公示为准",
            f"酒店价格参考 {datetime.now().strftime('%Y年%m月')} 市场行情",
            "⚠️ 配置 LLM_API_KEY + TAVILY_API_KEY + AMAP_WEB_KEY 后可使用实时 Agent 模式",
        ],
        "_demo": True,
    }


# ==================== 8 城真实景点库 ====================

_DEMO_CITY_DATA = {
    "成都": {
        "summary": "成都是一座来了就不想走的城市。悠闲的生活节奏、麻辣鲜香的美食、深厚的文化底蕴，让这座天府之国成为国内最受欢迎的旅游目的地之一。",
        "spots": [
            {"name": "大熊猫繁育研究基地", "desc": "全球最大的大熊猫人工繁育基地，拥有100多只大熊猫。清晨是熊猫最活跃的时段，可以看到它们吃竹子、爬树、打滚卖萌的可爱场景。园内还有月亮产房可以看到熊猫幼崽，建议安排3-4小时慢慢逛。", "price": 55, "hours": "07:30-18:00", "tips": "务必早上8点前到达，9点后熊猫吃饱就睡了；提前在公众号「成都大熊猫繁育研究基地」购票；园内观光车20元建议乘坐"},
            {"name": "宽窄巷子", "desc": "由宽巷子、窄巷子、井巷子三条平行排列的清代古街组成，是成都保存最完好的历史文化街区。宽巷子展示老成都的市井生活，窄巷子主打精致小资的院落文化，井巷子则是现代设计与传统的碰撞。巷内有川剧变脸表演、掏耳朵体验和各种文创小店。", "price": 0, "hours": "全天开放", "tips": "建议傍晚开始逛，灯笼亮起氛围最佳；巷内小吃偏贵，正宗美食在附近魁星楼街；掏耳朵40元一次，体验一下即可"},
            {"name": "锦里古街", "desc": "紧邻武侯祠的三国文化主题古街，全长550米，明清风格建筑蜿蜒曲折。白天可以感受三国文化和蜀绣、糖画等非遗手工艺，晚上灯笼高挂、流光溢彩，是成都最美的夜景之一。街内茶馆可以边喝茶边看川剧变脸。", "price": 0, "hours": "全天开放", "tips": "晚上19:00亮灯后最美，建议先逛武侯祠再到锦里；张飞牛肉和三大炮是必尝小吃；周末人极多注意防盗"},
            {"name": "武侯祠", "desc": "中国唯一君臣合祀祠庙，纪念诸葛亮和刘备，始建于公元223年。祠内古柏参天，碑刻林立，最有名的是岳飞手书《出师表》和三绝碑。三义庙供奉刘关张三人，是三国文化爱好者必朝圣之地。隔壁就是锦里，可一起游览。", "price": 50, "hours": "08:00-18:00", "tips": "建议花20元租讲解器或请导游，否则很难看懂历史内涵；全程约1.5小时；周一正常开放"},
            {"name": "杜甫草堂", "desc": "诗圣杜甫在成都的故居，他在此居住近四年，创作了240余首诗篇。草堂内翠竹掩映、溪水环绕，充满诗情画意。核心景点包括工部祠、诗史堂和茅屋故居，整个园林融合了江南园林的精致和川西民居的质朴。", "price": 60, "hours": "08:00-18:30", "tips": "环境清幽适合慢慢逛，预留2小时；红墙夹道是拍照最美的地方；旁边浣花溪公园免费，可以顺路逛"},
            {"name": "春熙路", "desc": "成都最繁华的百年商业街，也是全国十大步行街之一。IFS国际金融中心顶楼的爬墙大熊猫雕塑是成都地标级网红打卡点，太古里则汇集了国际大牌和设计师店铺。春熙路不仅是购物天堂，更是看成都美女的最佳地点。", "price": 0, "hours": "全天开放", "tips": "IFS顶楼大熊猫免费拍照，从7楼空中花园上去；太古里负一层有大牌折扣店；附近盐市口也有很多小吃"},
            {"name": "青城山", "desc": "中国道教发源地之一，有「青城天下幽」的美誉。前山以道教宫观为主，建福宫、上清宫、天师洞都值得一看；后山以自然风光取胜，飞瀑流泉、绿意盎然。全程游览需5-6小时，是成都周边的天然氧吧。", "price": 90, "hours": "08:00-17:00", "tips": "前山问道后山观景，体力有限选前山即可；穿运动鞋，山路较陡；山上物价高，自带水和干粮"},
            {"name": "都江堰", "desc": "始建于公元前256年的世界水利工程奇迹，由秦国蜀郡太守李冰父子主持修建。鱼嘴分水堤、飞沙堰溢洪道、宝瓶口引水口三大工程至今仍在发挥作用，灌溉了成都平原千万亩良田。站在伏龙观俯瞰整个工程，会被古人的智慧深深震撼。", "price": 80, "hours": "08:00-17:30", "tips": "强烈建议请导游讲解（约100元），否则看不太懂；和青城山同方向，可安排同一天；景区门口有直达高铁站的公交"},
        ],
        "foods": ["火锅", "串串香", "担担面", "龙抄手", "夫妻肺片", "钵钵鸡", "兔头", "冰粉"],
        "hotel_areas": {"春熙路/太古里": "市中心核心商圈，交通便利，美食集中", "宽窄巷子附近": "老成都风情，适合慢节奏体验"},
    },
    "北京": {
        "summary": "北京是一座兼具古典韵味与现代气息的城市，故宫的红墙金瓦、长城的雄伟壮阔、胡同里的人间烟火，每一处都值得细细品味。",
        "spots": [
            {"name": "故宫博物院", "desc": "明清两代皇家宫殿，世界最大宫殿建筑群，红墙金瓦震撼人心", "price": 60, "hours": "08:30-17:00（周一闭馆）", "tips": "提前7天在公众号预约，现场不售票"},
            {"name": "八达岭长城", "desc": "万里长城最精华段，登高望远气势磅礴", "price": 40, "hours": "07:30-16:00", "tips": "穿舒适运动鞋，带足够水"},
            {"name": "颐和园", "desc": "清代皇家园林，昆明湖畔万寿山下，一步一景", "price": 30, "hours": "06:30-18:00", "tips": "建议上午去人少，佛香阁可俯瞰全景"},
            {"name": "天坛公园", "desc": "明清皇帝祭天场所，祈年殿是北京地标", "price": 15, "hours": "06:00-21:00", "tips": "清晨可看大爷大妈打太极"},
            {"name": "南锣鼓巷", "desc": "北京最有名的胡同街区，小店、美食、文创琳琅满目", "price": 0, "hours": "全天开放", "tips": "尝老北京炸酱面和豆汁"},
            {"name": "798艺术区", "desc": "旧工厂改造的当代艺术区，展览、画廊、设计店", "price": 0, "hours": "10:00-18:00", "tips": "周末有市集，很好逛"},
            {"name": "什刹海", "desc": "前海、后海、西海统称，可划船赏荷花，酒吧街热闹", "price": 0, "hours": "全天开放", "tips": "傍晚去可看日落，晚上酒吧街热闹"},
        ],
        "foods": ["北京烤鸭", "炸酱面", "豆汁焦圈", "涮羊肉", "卤煮火烧", "炒肝", "爆肚"],
        "hotel_areas": {"王府井/东单": "市中心核心，步行可到天安门", "后海/鼓楼": "胡同风情，文艺气息浓厚"},
    },
    "上海": {
        "summary": "上海是一座海纳百川的国际化大都市，外滩的万国建筑、陆家嘴的摩天大楼、弄堂里的市井生活，在这里古今交融、中西合璧。",
        "spots": [
            {"name": "外滩", "desc": "万国建筑博览群，浦江两岸古今辉映", "price": 0, "hours": "全天开放", "tips": "夜景比白天更美，建议傍晚去"},
            {"name": "东方明珠", "desc": "上海地标，登塔俯瞰浦江两岸全景", "price": 199, "hours": "08:30-21:30", "tips": "选晴天去，能见度高"},
            {"name": "豫园", "desc": "明代江南园林，曲径通幽别有洞天", "price": 40, "hours": "08:30-17:00", "tips": "旁边城隍庙小吃很多"},
            {"name": "南京路步行街", "desc": "中华第一商业街，购物天堂", "price": 0, "hours": "全天开放", "tips": "晚上霓虹灯璀璨"},
            {"name": "迪士尼乐园", "desc": "中国大陆首座迪士尼，梦幻童话世界", "price": 475, "hours": "08:30-21:30", "tips": "提前下载APP抢FP快速通行"},
            {"name": "田子坊", "desc": "弄堂里的文艺小店聚集地，手工艺品和咖啡香", "price": 0, "hours": "10:00-22:00", "tips": "周末人很多，建议工作日去"},
            {"name": "上海博物馆", "desc": "中国古代艺术顶级殿堂，青铜器收藏举世闻名", "price": 0, "hours": "09:00-17:00（周一闭馆）", "tips": "免费但需提前预约"},
        ],
        "foods": ["生煎包", "小笼包", "蟹粉面", "本帮菜", "葱油拌面", "排骨年糕", "蝴蝶酥"],
        "hotel_areas": {"外滩/南京路": "一线江景，步行逛外滩", "静安寺": "时尚商圈，购物方便"},
    },
    "杭州": {
        "summary": "上有天堂，下有苏杭。杭州以西湖为核心，湖光山色与人文底蕴交相辉映，是一座让人流连忘返的诗意之城。",
        "spots": [
            {"name": "西湖", "desc": "世界文化遗产，十景如画，泛舟湖上如入仙境", "price": 0, "hours": "全天开放", "tips": "断桥残雪最美，苏堤春晓必走"},
            {"name": "灵隐寺", "desc": "千年古刹，飞来峰下香烟缭绕", "price": 45, "hours": "07:00-17:30", "tips": "心诚则灵，建议早上前往"},
            {"name": "雷峰塔", "desc": "白蛇传说之地，塔顶俯瞰西湖全景", "price": 40, "hours": "08:00-17:30", "tips": "傍晚登塔可看西湖日落"},
            {"name": "西溪湿地", "desc": "城市绿肺，坐摇橹船穿行芦苇荡", "price": 80, "hours": "08:00-17:00", "tips": "坐摇橹船体验最佳"},
            {"name": "龙井村", "desc": "西湖龙井原产地，层层茶园翠绿欲滴", "price": 0, "hours": "全天开放", "tips": "清明前后最热闹，可品正宗龙井"},
            {"name": "河坊街", "desc": "南宋古街，美食小吃和手工艺品丰富", "price": 0, "hours": "全天开放", "tips": "定胜糕和葱包烩必尝"},
        ],
        "foods": ["西湖醋鱼", "龙井虾仁", "东坡肉", "葱包烩", "定胜糕", "片儿川", "叫花鸡"],
        "hotel_areas": {"西湖湖滨": "西湖畔，出门即是湖景", "武林广场": "市中心商圈，交通便利"},
    },
    "大理": {
        "summary": "大理是云南高原上的一颗明珠，苍山洱海之间的白族古城，有「风花雪月」之美。在这里时间变慢，心灵得到治愈。",
        "spots": [
            {"name": "洱海", "desc": "环湖骑行赏苍山倒影，湖水湛蓝如宝石", "price": 0, "hours": "全天开放", "tips": "环海西路风景绝美，租电动车最方便"},
            {"name": "大理古城", "desc": "漫步白族古城，石板路、老宅、鲜花饼", "price": 0, "hours": "全天开放", "tips": "人民路小店值得逛"},
            {"name": "苍山", "desc": "乘索道登顶，俯瞰洱海和大理坝子", "price": 280, "hours": "08:30-17:00", "tips": "山顶凉，带外套"},
            {"name": "喜洲古镇", "desc": "白族民居博物馆，严家大院和转角楼必看", "price": 0, "hours": "全天开放", "tips": "喜洲粑粑必尝，打车20分钟可达"},
            {"name": "双廊古镇", "desc": "洱海边发呆看日落的最佳位置", "price": 0, "hours": "全天开放", "tips": "海景咖啡馆很多，适合发呆"},
            {"name": "崇圣寺三塔", "desc": "千年古塔，大理国皇家寺院遗址", "price": 75, "hours": "08:00-18:00", "tips": "清晨钟声很治愈"},
        ],
        "foods": ["酸辣鱼", "烤乳扇", "喜洲粑粑", "凉鸡米线", "洱海虾", "白族三道茶"],
        "hotel_areas": {"大理古城": "吃喝玩乐方便，夜生活丰富", "双廊/洱海边": "海景客栈，适合发呆看日落"},
    },
    "三亚": {
        "summary": "三亚是中国最南端的热带滨海城市，蓝天碧海、椰风海韵，是冬日避寒、夏日戏水的首选度假胜地。",
        "spots": [
            {"name": "亚龙湾", "desc": "天下第一湾，沙白水清，热带天堂", "price": 0, "hours": "全天开放", "tips": "自带浮潜装备玩得更尽兴"},
            {"name": "蜈支洲岛", "desc": "中国马尔代夫，潜水爱好者的天堂", "price": 168, "hours": "08:00-17:30", "tips": "提前一天买票，早上第一班船上岛人少"},
            {"name": "南山文化旅游区", "desc": "108米海上观音庄严壮观", "price": 129, "hours": "08:00-17:00", "tips": "景区很大，穿舒适鞋子"},
            {"name": "天涯海角", "desc": "三亚标志性景点，礁石海滩椰林", "price": 81, "hours": "07:30-18:00", "tips": "情侣必去打卡地"},
            {"name": "海棠湾免税店", "desc": "全球最大单体免税店，购物天堂", "price": 0, "hours": "10:00-22:00", "tips": "离岛前提前购买，机场提货"},
        ],
        "foods": ["海鲜大排档", "椰子鸡", "清补凉", "抱罗粉", "文昌鸡", "热带水果"],
        "hotel_areas": {"亚龙湾": "一线海景五星酒店群", "大东海": "性价比高，交通便利"},
    },
    "西安": {
        "summary": "西安是十三朝古都，兵马俑的壮观、古城墙的厚重、回民街的美食，让这座千年帝都散发着永恒的魅力。",
        "spots": [
            {"name": "秦始皇兵马俑", "desc": "世界第八大奇迹，千军万马气势磅礴", "price": 120, "hours": "08:30-17:00", "tips": "建议请导游讲解，自己看很难懂"},
            {"name": "大雁塔", "desc": "唐代古塔，玄奘译经之地，音乐喷泉壮观", "price": 50, "hours": "08:00-18:00", "tips": "晚上音乐喷泉表演很棒"},
            {"name": "西安城墙", "desc": "中国保存最完整的古城墙，可骑车环游", "price": 54, "hours": "08:00-22:00", "tips": "租自行车骑行一圈约1.5小时"},
            {"name": "回民街", "desc": "西安最热闹的美食街，羊肉泡馍、肉夹馍", "price": 0, "hours": "全天开放", "tips": "晚上最热闹，人多注意财物"},
            {"name": "大唐不夜城", "desc": "仿唐建筑群，夜景灯光华丽", "price": 0, "hours": "全天开放", "tips": "晚上去才有feel，不倒翁小姐姐很火"},
            {"name": "华清宫", "desc": "唐玄宗与杨贵妃的温泉离宫", "price": 120, "hours": "07:00-18:00", "tips": "和兵马俑一天可以逛完"},
        ],
        "foods": ["羊肉泡馍", "肉夹馍", "凉皮", "Biangbiang面", "灌汤包", "甑糕", "胡辣汤"],
        "hotel_areas": {"钟楼/回民街": "市中心核心，吃喝玩乐方便", "大雁塔/曲江": "环境好，夜景美"},
    },
    "重庆": {
        "summary": "重庆是一座魔幻 8D 山城，轻轨穿楼、洪崖洞夜景、麻辣火锅，每一步都是惊喜，每一口都是热辣。",
        "spots": [
            {"name": "洪崖洞", "desc": "千与千寻同款吊脚楼，夜景璀璨夺目", "price": 0, "hours": "全天开放", "tips": "晚上亮灯后最美，人超多注意安全"},
            {"name": "解放碑", "desc": "重庆地标，中国唯一抗战胜利纪功碑", "price": 0, "hours": "全天开放", "tips": "周边好吃街很多"},
            {"name": "磁器口古镇", "desc": "千年古镇，麻花飘香，茶馆听川剧", "price": 0, "hours": "全天开放", "tips": "陈麻花必买，周末人超多"},
            {"name": "长江索道", "desc": "万里长江第一条空中走廊，飞渡长江", "price": 20, "hours": "07:30-22:30", "tips": "傍晚坐，看长江日落"},
            {"name": "南山一棵树", "desc": "俯瞰重庆夜景的绝佳位置", "price": 30, "hours": "09:00-22:30", "tips": "重庆夜景是名片，必看"},
        ],
        "foods": ["火锅", "小面", "酸辣粉", "抄手", "毛血旺", "辣子鸡", "豆花饭"],
        "hotel_areas": {"解放碑/洪崖洞": "市中心核心，夜景尽收眼底", "南滨路": "江景酒店，环境好"},
    },
    "长沙": {
        "summary": "长沙是一座烟火气十足的城市，岳麓书院的千年书香、橘子洲的伟人足迹、文和友的市井美食，让人来了就不想走。",
        "spots": [
            {"name": "橘子洲", "desc": "湘江中的长岛，毛泽东青年雕像巍然屹立", "price": 0, "hours": "全天开放", "tips": "周末晚上有烟花表演"},
            {"name": "岳麓山", "desc": "南岳衡山余脉，岳麓书院千年学府", "price": 0, "hours": "06:00-23:00", "tips": "秋天红叶最美"},
            {"name": "太平老街", "desc": "长沙最有韵味的老街，臭豆腐和茶颜悦色的发源地", "price": 0, "hours": "全天开放", "tips": "茶颜悦色排队很长但值得"},
            {"name": "湖南省博物馆", "desc": "马王堆汉墓出土文物，辛追夫人千年不朽", "price": 0, "hours": "09:00-17:00（周一闭馆）", "tips": "需提前预约，免费"},
            {"name": "五一广场", "desc": "长沙最繁华的商业中心，IFS国金中心", "price": 0, "hours": "全天开放", "tips": "长沙夜生活从这里开始"},
        ],
        "foods": ["臭豆腐", "口味虾", "辣椒炒肉", "剁椒鱼头", "糖油粑粑", "茶颜悦色", "文和友"],
        "hotel_areas": {"五一广场/坡子街": "市中心核心，美食触手可及", "岳麓区": "大学城附近，性价比高"},
    },
    "厦门": {
        "summary": "厦门是一座文艺清新的海岛城市，鼓浪屿的万国建筑、环岛路的椰风海韵、曾厝垵的渔村风情，每一步都是风景。",
        "spots": [
            {"name": "鼓浪屿", "desc": "海上花园，万国建筑博物馆，钢琴之岛", "price": 35, "hours": "全天开放", "tips": "提前在公众号买船票，现场常售罄"},
            {"name": "厦门大学", "desc": "中国最美大学，嘉庚建筑，芙蓉隧道涂鸦", "price": 0, "hours": "需预约入校", "tips": "需提前在U厦大预约"},
            {"name": "曾厝垵", "desc": "文艺渔村变网红打卡地，小吃手信一条街", "price": 0, "hours": "全天开放", "tips": "沙茶面和海蛎煎必吃"},
            {"name": "环岛路", "desc": "沿海景观大道，骑行看海吹海风", "price": 0, "hours": "全天开放", "tips": "租自行车骑行超惬意"},
            {"name": "南普陀寺", "desc": "闽南佛教圣地，千年古刹，免费开放", "price": 0, "hours": "03:00-18:00", "tips": "素饼很有名，可做伴手礼"},
        ],
        "foods": ["沙茶面", "海蛎煎", "姜母鸭", "土笋冻", "花生汤", "烧肉粽"],
        "hotel_areas": {"中山路/轮渡": "去鼓浪屿方便，中山路美食多", "曾厝垵": "文艺民宿聚集地，近海"},
    },
}
