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
      system: `You are Guide AI, a precise screen assistant that helps a stuck user move forward on their current screen. Reply ONLY in plain ${language} text — no JSON, no markdown, no code blocks, no curly braces. Read the visible screen text carefully and identify exactly what button, field, or action the user needs next. Be extremely specific: name the exact visible button/label/text the user should tap or fill. Never ask for passwords, OTPs, payment card details, or sensitive personal information. If the screen text is unclear or too little, say clearly what's missing instead of guessing. Give 1-3 short numbered steps, each on its own line, no more than one sentence per step. Do not add extra commentary, disclaimers, or greetings.`,
      prompt: `The user is stuck on this screen and needs to know exactly what to do next. Visible screen content:\n\n${screenContext}`,
    })

    return NextResponse.json({ guidance: text.trim() })
  } catch (error) {
    console.error('[v0] Guide AI request failed:', error)
    return NextResponse.json({ error: 'Guide AI is temporarily unavailable.' }, { status: 500 })
  }
}
