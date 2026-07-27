/**
 * PostCSS 配置 — postcss-pxtorem 移动端适配
 *
 * 【修复】exclude 改为函数判断：同时兼容 Windows 反斜杠和 Unix 正斜杠路径
 * 根因：正则 /node_modules/i 在 Windows 路径（H:\...\node_modules\...）下
 * postcss 内部将路径 normalize 后可能导致正则匹配不稳定，
 * Vant Toast/Dialog 等组件的 px 值被误转换 → 样式异常 → Toast 不可见/错位
 */
export default {
  plugins: {
    'postcss-pxtorem': {
      rootValue: 37.5,
      propList: ['*'],
      minPixelValue: 1,
      replace: true,
      mediaQuery: false,
      // 【修复】函数确保 node_modules 绝对被排除，兼容 Windows 反斜杠路径
      exclude: (filePath) => {
        // 将路径统一为反斜杠后再匹配
        const normalized = (filePath || '').replace(/\//g, '\\')
        return normalized.includes('\\node_modules\\')
      },
    },
  },
}
