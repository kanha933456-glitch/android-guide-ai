import { google } from '@ai-sdk/google'
import { generateText } from 'ai'
import { NextResponse } from 'next/server'

const supportedLanguages = new Set(['Hindi', 'English', 'اردو', 'বাংলা'])

export async function POST(request: Request) {
  try {
    const body = await request.json()
    const language = supportedLanguages.has(body?.language) ? body.language : 'Hindi'
    const screenContext = typeof body?.screenContext === 'string' ? body.screenContext.slice(0, 4000) : ''

    if (!screenContext.trim()) {
      return NextResponse.json({ error: 'Screen context is required.' }, { status: 400 })
    }

    const { text } = await generateText({
      model: google('gemini-3.5-flash-lite', { apiKey: process.env.GEMINI_API_KEY }),
      system: `You are Guide AI, a calm and concise screen assistant. Explain the next safe action in ${language}. Never ask for passwords, OTPs, payment card details, or sensitive personal information. If the context is unclear, say what is missing. Return 2-4 short steps and no markdown headings.`,
      prompt: `The user is stuck on this screen. Give practical next steps in ${language}:\n\n${screenContext}`,
    })

    return NextResponse.json({ guidance: text })
  } catch (error) {
    console.error('[v0] Guide AI request failed:', error)
    return NextResponse.json({ error: 'Guide AI is temporarily unavailable.' }, { status: 500 })
  }
}
