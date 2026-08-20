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

    const sensitivePattern = /(?:password|passcode|otp|one[- ]time|cvv|cvc|card number|upi pin|bank account|credit card|debit card)/i
    if (sensitivePattern.test(screenContext)) {
      return NextResponse.json({ guidance: 'Main password, OTP, PIN ya payment details par guide nahi kar sakta. Kripya sensitive information hide karke safe screen par dobara try karein.' })
    }

    const { text } = await generateText({
      model: google('gemini-3.5-flash-lite', { apiKey: process.env.GEMINI_API_KEY }),
      system: `You are Guide AI, a calm and concise screen assistant. Explain the next safe action in ${language}. Never ask for passwords, OTPs, payment card details, or sensitive personal information. If the context is unclear, say what is missing. Return JSON only with this shape: {"guidance":"2-4 short steps","action":"the safest next action or null","confidence":"low|medium|high"}. Do not use markdown headings.`,
      prompt: `The user is stuck on this screen. Give practical next steps in ${language}:\n\n${screenContext}`,
    })

    try {
      const parsed = JSON.parse(text)
      return NextResponse.json({ guidance: parsed.guidance || text, action: parsed.action || null, confidence: parsed.confidence || 'low' })
    } catch {
      return NextResponse.json({ guidance: text, action: null, confidence: 'low' })
    }
  } catch (error) {
    console.error('[v0] Guide AI request failed:', error)
    return NextResponse.json({ error: 'Guide AI is temporarily unavailable.' }, { status: 500 })
  }
}
