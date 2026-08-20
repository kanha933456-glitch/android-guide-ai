import { google } from '@ai-sdk/google'
import { generateText } from 'ai'
import { NextResponse } from 'next/server'

const languages = new Set(['Hindi', 'English', 'اردو', 'বাংলা'])

export async function POST(request: Request) {
  try {
    const body = await request.json()
    const language = languages.has(body?.language) ? body.language : 'Hindi'
    const image = typeof body?.image === 'string' ? body.image : ''
    const question = typeof body?.question === 'string' ? body.question.slice(0, 1000) : ''
    if (!image.startsWith('data:image/')) return NextResponse.json({ error: 'A valid screenshot is required.' }, { status: 400 })
    if (image.length > 6_000_000) return NextResponse.json({ error: 'Screenshot is too large.' }, { status: 413 })

    const { text } = await generateText({
      model: google('gemini-3.5-flash-lite', { apiKey: process.env.GEMINI_API_KEY }),
      system: `You are Guide AI. Describe only safe, visible UI guidance in ${language}. Never request or reveal passwords, OTPs, PINs, payment details, or personal data. Do not claim certainty if text is unreadable. Give 2-4 concise steps and mention the visible button label when possible. Return JSON only with {"guidance":"2-4 short steps","action":"visible safe next action or null","confidence":"low|medium|high"}.`,
      messages: [{ role: 'user', content: [{ type: 'text', text: question || 'What should the user do next on this screen?' }, { type: 'image', image }] }],
    })
    try {
      const parsed = JSON.parse(text)
      return NextResponse.json({ guidance: parsed.guidance || text, action: parsed.action || null, confidence: parsed.confidence || 'low' })
    } catch {
      return NextResponse.json({ guidance: text, action: null, confidence: 'low' })
    }
  } catch (error) {
    console.error('[v0] Vision guide failed:', error)
    return NextResponse.json({ error: 'Visual guidance is temporarily unavailable.' }, { status: 500 })
  }
}
