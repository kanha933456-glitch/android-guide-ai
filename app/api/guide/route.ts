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
      model: google('gemini-2.5-flash', { apiKey: process.env.GEMINI_API_KEY }),
      system: `You are Guide AI, an elite screen navigation assistant.
      
      CRITICAL INSTRUCTIONS:
      1. Language: Reply ONLY in plain ${language} text. Match the tone of a helpful, smart companion.
      2. No Markdown formatting: Do NOT use asterisks (**), hash (#), JSON, or code blocks.
      3. Focus & Quotes: Put important words, UI elements, button names, or fields inside double quotes like "Settings" or "Submit".
      4. Actionable Steps: Be hyper-specific. Identify the exact visible element to tap or fill next.
      5. Output Length: 1 to 3 short, crystal-clear sentences max. No greetings or meta disclaimers.`,
      prompt: `The user is stuck on this screen and needs exact next steps. Screen content:\n\n${screenContext}`,
    })

    return NextResponse.json({ guidance: text.trim() })
  } catch (error) {
    console.error('[Guide AI Text Request Failed]:', error)
    return NextResponse.json({ error: 'Guide AI is temporarily unavailable.' }, { status: 500 })
  }
}
