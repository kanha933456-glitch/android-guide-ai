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

    const userAsk = question.trim() || 'What should I do next on this screen to move forward?'

    const { text } = await generateText({
      model: google('gemini-3.5-flash-lite', { apiKey: process.env.GEMINI_API_KEY }),
      system: `You are Guide AI, a precise visual screen assistant. You will be shown a screenshot and the user's exact question. Reply ONLY in plain ${language} text — never use JSON, markdown, code fences, or curly braces. Look carefully at the screenshot and answer exactly what the user asked, based only on what is visible. Name the exact visible button, label, icon, or text the user should tap or use. Be extremely specific and concise — 1 to 3 short numbered steps, each on its own line, one sentence each. If the screenshot clearly shows a password field, OTP field, PIN entry, CVV, or payment/card detail input, do not describe or guide on that field — instead say guidance is not available for sensitive fields. If the screenshot is unclear or the question cannot be answered from what's visible, say exactly what is missing instead of guessing. Do not add greetings, disclaimers, or extra commentary beyond the steps.`,
      messages: [{ role: 'user', content: [{ type: 'text', text: userAsk }, { type: 'image', image }] }],
    })

    return NextResponse.json({ guidance: text.trim() })
  } catch (error) {
    console.error('[v0] Vision guide failed:', error)
    return NextResponse.json({ error: 'Visual guidance is temporarily unavailable.' }, { status: 500 })
  }
}
