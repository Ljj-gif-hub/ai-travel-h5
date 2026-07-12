// src/api/plan.js
import { streamPost } from '../utils/streamRequest'

const MODEL = 'ep-m-20260712023022-m2glj'

/**
 * 流式生成旅行计划
 * @param {object} data - { destination, budget, days }
 * @param {function} onToken - 每次收到 token 时的回调（用于实时更新 UI）
 * @returns {Promise<string>} 完整的结果文本
 */
export const generateTravelPlan = async (data, onToken) => {
  const { destination, budget, days } = data
  const prompt = `你是一个专业的旅游规划师。请为去${destination}旅游的用户生成一份详细的行程规划，预算${budget}元，共${days}天。请按天数返回每天的行程安排，包括景点、美食和活动建议。`

  console.log('AI请求参数:', { destination, budget, days, prompt })

  const requestData = {
    model: MODEL,
    messages: [
      { role: 'system', content: '你是一个专业的旅游规划助手，擅长为用户提供详细、实用的旅行建议。' },
      { role: 'user', content: prompt },
    ],
    stream: true, // 开启流式
  }

  console.log('发送给API的数据:', JSON.stringify(requestData, null, 2))

  let fullContent = ''

  await streamPost('/chat/completions', requestData, {
    onChunk: (token) => {
      fullContent += token
      onToken?.(token) // 实时回调给 UI
    },
    onDone: () => {
      console.log('流式传输完成，完整内容:', fullContent)
    },
    onError: (err) => {
      console.error('流式请求失败:', err)
      throw err
    },
  })

  return fullContent
}