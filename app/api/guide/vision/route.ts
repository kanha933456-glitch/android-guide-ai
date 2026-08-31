import { google } from '@ai-sdk/google'
import { generateText } from 'ai'
import { NextResponse } from 'next/server'

export async function POST(request: Request) {
  try {
    const body = await request.json()
    const image = typeof body?.image === 'string' ? body.image : ''
    const question = typeof body?.question === 'string' ? body.question.slice(0, 1000) : ''
    
    if (!image.startsWith('data:image/')) {
      return NextResponse.json({ error: 'A valid screenshot is required.' }, { status: 400 })
    }
    if (image.length > 6_000_000) {
      return NextResponse.json({ error: 'Screenshot is too large.' }, { status: 413 })
    }

    const userAsk = question.trim() || 'What should I do next on this screen to move forward?'

    const { text } = await generateText({
      model: google('gemini-2.5-flash', { apiKey: process.env.GEMINI_API_KEY }),
      system: `You are Guide AI, a top-tier visual screen assistant built for maximum speed and accuracy.

      CRITICAL RULES & HANDLING LOGIC:
      1. LANGUAGE: Detect the exact language/dialect of the user's question (Hindi, English, Hinglish, etc.) and reply in that EXACT language. If question is empty, use natural Hinglish/English.
      2. GENERAL QUESTIONS: If user asks context-independent general knowledge (e.g., definitions, math, logic), answer directly with 1-2 accurate sentences without analyzing screen UI.
      3. SCREEN GUIDANCE: If user asks about the screen/stuck state, scan the screenshot visually. Point out the exact button label, input box, or action. Wrap key buttons or UI words in quotation marks, like "Submit" or "Next".
      4. SENSITIVE DATA: If screen shows password, OTP, CVV, or PIN fields, decline gracefully in user's language.
      5. NO MARKDOWN: Never use asterisks (**), bold stars, markdown formatting, JSON, or code blocks. Keep output smooth for speech text-to-speech engine. Max 2-3 short direct sentences. No greetings.`,
      messages: [
        { 
          role: 'user', 
          content: [
            { type: 'text', text: userAsk }, 
            { type: 'image', image }
          ] 
        }
      ],
    })

    return NextResponse.json({ guidance: text.trim() })
  } catch (error) {
    console.error('[Guide AI Vision Request Failed]:', error)
    return NextResponse.json({ error: 'Visual guidance is temporarily unavailable.' }, { status: 500 })
  }
}
